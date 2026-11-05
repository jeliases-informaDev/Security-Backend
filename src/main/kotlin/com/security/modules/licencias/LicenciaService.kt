package com.security.modules.licencias

import com.security.modules.licencias.cliente.LicenciasCliente
import com.security.modules.licencias.cliente.LicenciasNoDisponibleException
import com.security.modules.licencias.cola.ColaLicencias
import com.security.modules.licencias.config.LicenciasProperties
import com.security.modules.licencias.dominio.ConsumoRemoto
import com.security.modules.licencias.dominio.CupoRemoto
import com.security.modules.licencias.dominio.LicenciaPublica
import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.modules.licencias.dominio.NIVEL_BLOQUEADO
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Licencia de PLAFT de cada empresa según el servicio común de Sulotec. Reglas de la casa: la caché dura 5 minutos;
 * si Sulotec no responde se sigue con la última licencia conocida y, si nunca se conoció, se trata como activa (a un
 * cliente nunca se le bloquea por una caída nuestra); con LICENCIAS_TOKEN vacío todo está apagado y nada de aquí toca
 * la red ni la base. Solo lanzan hacia la petición verificarCupo (409) y consumir (402/403), nunca por una caída.
 */
@Service
class LicenciaService(
    private val properties: LicenciasProperties,
    private val cliente: LicenciasCliente,
    private val cola: ColaLicencias,
    private val usuarioRepository: UsuarioRepository,
    private val empresaRepository: EmpresaRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Reloj de la caché y de la pausa; solo las pruebas lo cambian. */
    var reloj: Clock = Clock.systemUTC()

    /**
     * Hilo aparte para avisar las cuentas activas sin demorar la respuesta. Uno solo y daemon: los avisos salen en
     * orden y no impiden apagar la aplicación. Las pruebas lo cambian por uno que ejecuta en el acto.
     */
    var ejecutor: Executor = Executors.newSingleThreadExecutor { tarea ->
        Thread(tarea, "licencias-activos").apply { isDaemon = true }
    }

    private data class Entrada(val licencia: LicenciaRemota, val leidaEn: Instant)

    // Una entrada vencida no se borra: ante una caída del servicio la última licencia conocida vale más que nada.
    private val cache = ConcurrentHashMap<String, Entrada>()
    private val pausaHasta = ConcurrentHashMap<String, Instant>()

    val activo: Boolean
        get() = properties.activo

    // ---------- Licencia ----------

    /**
     * La licencia de la empresa (lo que respondió Sulotec), estado SIN_LICENCIA si no tiene PLAFT, o null si las licencias
     * están apagadas, la empresa no tiene UUID o nunca se pudo leer (null = se trata como activa).
     */
    fun licenciaDe(uuid: String?, forzar: Boolean = false): LicenciaRemota? {
        if (!properties.activo) return null
        val empresa = limpiar(uuid) ?: return null
        val ahora = reloj.instant()
        val guardada = cache[empresa]
        if (!forzar && guardada != null && vigente(guardada, ahora)) return guardada.licencia
        if (!forzar && enPausa(empresa, ahora)) return guardada?.licencia
        return try {
            val licencia = cliente.obtenerLicencia(empresa)
            // Sin licencia no se bloquea nada, pero debe notarse en el log: una vez por ciclo de caché.
            if (licencia.esSinLicencia()) {
                log.warn("La empresa {} no tiene una licencia de PLAFT registrada en Sulotec: no se bloquea nada", empresa)
            }
            cache[empresa] = Entrada(licencia, ahora)
            pausaHasta.remove(empresa)
            licencia
        } catch (e: Exception) {
            avisarFallo(empresa, e)
            guardada?.licencia
        }
    }

    /**
     * La licencia preguntada a Sulotec en este momento, SIN caché (la usa el inicio único: una licencia recién creada no
     * debe verse como "sin licencia" durante 5 minutos). null = licencias apagadas o sin UUID. Si Sulotec no responde
     * lanza LicenciasNoDisponibleException: quien la usa decide qué decirle a la persona.
     */
    fun licenciaEnVivo(uuid: String?): LicenciaRemota? {
        if (!properties.activo) return null
        val empresa = limpiar(uuid) ?: return null
        val licencia = cliente.obtenerLicencia(empresa)
        cache[empresa] = Entrada(licencia, reloj.instant())
        pausaHasta.remove(empresa)
        return licencia
    }

    /** Estado que ya está en caché (aunque haya vencido), sin preguntar nada; null si no se conoce. */
    fun estadoConocido(uuid: String?): String? = limpiar(uuid)?.let { cache[it]?.licencia?.estado }

    /** La última licencia conocida (aunque haya vencido), sin preguntar nada. */
    fun ultimaConocida(uuid: String?): LicenciaRemota? = limpiar(uuid)?.let { cache[it]?.licencia }

    /** Lo que ve la web (C1); null = no se muestra nada. */
    fun licenciaPublica(uuid: String?, forzar: Boolean = false): LicenciaPublica? =
        LicenciaReglas.publica(licenciaDe(uuid, forzar))

    // ---------- Cupo de usuarios ----------

    /**
     * Antes de crear, reactivar o mover cuentas activas: pregunta el cupo en vivo. El cupo es del plan de la empresa y
     * se exige a todos, también al administrador de Sulotec (a él se le manda al panel de licencias). Con cuentasNuevas > 1
     * (carga masiva) las cuentas del lote deben caber en lo que queda libre. Si Sulotec no responde se permite, salvo que
     * la última licencia conocida diga que no se pueden crear usuarios.
     */
    fun verificarCupo(uuid: String?, cuentasNuevas: Int = 1, actorEsSulotec: Boolean = false) {
        if (!properties.activo || cuentasNuevas <= 0) return
        val empresa = limpiar(uuid) ?: return
        val cupo = if (enPausa(empresa, reloj.instant())) null else try {
            cliente.consultarCupo(empresa)
        } catch (e: Exception) {
            avisarFallo(empresa, e)
            null
        }
        if (cupo == null) {
            val ultima = cache[empresa]?.licencia
            if (ultima?.puedeCrearUsuarios == false) {
                throw sinCupo(cuentasNuevas, ultima.usuariosActivos, ultima.usuariosMax, actorEsSulotec)
            }
            log.warn("No se pudo verificar el cupo de usuarios de la empresa {}: se permite", empresa)
            return
        }
        if (cupo.sinLicencia) return
        anotarCupo(empresa, cupo)
        if (cupo.puedeCrear == false) throw sinCupo(cuentasNuevas, cupo.activos, cupo.usuariosMax, actorEsSulotec)
        val maximo = cupo.usuariosMax
        val activos = cupo.activos
        if (cuentasNuevas > 1 && maximo != null && activos != null && cuentasNuevas > maximo - activos) {
            throw sinCupo(cuentasNuevas, activos, maximo, actorEsSulotec)
        }
    }

    private fun sinCupo(cuentasNuevas: Int, activos: Long?, maximo: Long?, actorEsSulotec: Boolean): LicenciaSinCupoException {
        val mensaje = if (cuentasNuevas > 1) {
            val libres = if (activos != null && maximo != null) maxOf(0L, maximo - activos) else 0L
            LicenciaReglas.textoCupoLote(cuentasNuevas, libres, activos, maximo)
        } else {
            LicenciaReglas.textoCupo(activos, maximo)
        }
        return LicenciaSinCupoException(activos, maximo, LicenciaReglas.enlaceCupo(actorEsSulotec), mensaje)
    }

    // ---------- Cuentas activas ----------

    /** Cuenta las cuentas activas de la empresa (cualquier rol) y se lo dice a Sulotec. Nunca lanza: devuelve el cupo o null. */
    fun contarYReportarActivos(nombreEmpresa: String?): CupoRemoto? {
        if (!properties.activo) return null
        val nombre = limpiar(nombreEmpresa) ?: return null
        return try {
            val empresa = empresaRepository.findByNombreIgnoreCase(nombre) ?: return null
            val uuid = limpiar(empresa.uuid) ?: return null
            val activos = usuarioRepository.contarActivosPorEmpresa(empresa.nombre)
            val cupo = cliente.reportarActivos(uuid, activos)
            if (!cupo.sinLicencia) anotarCupo(uuid, cupo)
            cupo
        } catch (e: Exception) {
            log.warn("No se pudieron reportar las cuentas activas de la empresa {}: {}", nombre, e.message)
            null
        }
    }

    /**
     * Después de crear, activar, desactivar, mover o cargar cuentas: avisa en un hilo aparte (la petición no se demora)
     * y, si hay una transacción en curso, recién después del commit, para contar lo que de verdad quedó guardado. Nunca lanza.
     */
    fun reportarActivosLuego(vararg nombresEmpresa: String?) {
        if (!properties.activo) return
        try {
            val nombres = nombresEmpresa.mapNotNull { limpiar(it) }.distinctBy { it.lowercase() }
            if (nombres.isEmpty()) return
            val tarea = Runnable { nombres.forEach { contarYReportarActivos(it) } }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() = lanzar(tarea)
                })
            } else {
                lanzar(tarea)
            }
        } catch (e: Exception) {
            log.warn("No se pudo programar el reporte de cuentas activas: {}", e.message)
        }
    }

    /** Al arrancar: cada empresa activa con UUID reporta sus cuentas activas, por si algún aviso anterior se perdió. */
    fun alinearActivosAlArrancar() {
        if (!properties.activo) return
        try {
            val empresas = empresaRepository.findAll().filter { it.activa && limpiar(it.uuid) != null }
            empresas.forEach { contarYReportarActivos(it.nombre) }
            log.info("Cuentas activas reportadas a Sulotec para {} empresas", empresas.size)
        } catch (e: Exception) {
            log.warn("No se pudieron alinear las cuentas activas con Sulotec: {}", e.message)
        }
    }

    /** Lo mismo, en el hilo aparte, para no demorar el arranque. */
    fun alinearActivosEnSegundoPlano() {
        if (!properties.activo) return
        lanzar(Runnable { alinearActivosAlArrancar() })
    }

    private fun lanzar(tarea: Runnable) {
        try {
            ejecutor.execute(tarea)
        } catch (e: Exception) {
            log.warn("No se pudo lanzar el reporte de cuentas activas: {}", e.message)
        }
    }

    // ---------- Tokens ----------

    /** Clave de una búsqueda en listas, con el reloj del servicio (ver LicenciaReglas.claveBusqueda). */
    fun claveBusqueda(uuid: String?, usuario: String, criterios: List<String?>): String =
        LicenciaReglas.claveBusqueda(uuid, usuario, criterios, reloj.instant())

    /** Clave de una acción que un reintento repetiría (búsqueda, PDF), con el reloj del servicio. */
    fun claveDeterminista(uuid: String?, usuario: String, tipo: String, criterios: List<String?>): String =
        LicenciaReglas.claveDeterminista(uuid, usuario, tipo, criterios, reloj.instant())

    /** consumir con los datos de quien actúa. Sin contexto o sin empresa no se consume nada. */
    fun consumirComo(contexto: ContextoLicencia?, tipo: String, clave: String, detalle: String?) {
        if (contexto == null) return
        consumir(contexto.empresaUuid, tipo, clave, contexto.usuario, contexto.esSulotec, detalle)
    }

    /**
     * Pide permiso a Sulotec ANTES de ejecutar la acción; Sulotec descuenta lo que diga su tarifa para ese tipo (la
     * cantidad = 1 que se manda solo es por compatibilidad). Devuelve normalmente si se permite;
     * lanza LicenciaBolsaAgotadaException (402) o LicenciaBloqueadaException (403) si el servicio lo rechaza. Si Sulotec
     * no responde se permite y el consumo queda en la cola local con la misma clave, salvo que la última licencia conocida
     * ya estuviera bloqueada. El administrador de Sulotec nunca se bloquea (sus consumos van marcados hecho_por_sulotec).
     */
    fun consumir(uuid: String?, tipo: String, clave: String, usuario: String, hechoPorSulotec: Boolean, detalle: String?) {
        if (!properties.activo) return
        val empresa = limpiar(uuid) ?: return
        var fallo: String? = "en pausa tras un fallo reciente"
        val respuesta = if (enPausa(empresa, reloj.instant())) null else try {
            cliente.consumir(empresa, clave, tipo, 1, usuario, hechoPorSulotec, detalle)
        } catch (e: Exception) {
            avisarFallo(empresa, e)
            fallo = e.message
            null
        }
        if (respuesta == null) {
            permitirSinRespuesta(empresa, tipo, clave, usuario, hechoPorSulotec, detalle, fallo)
            return
        }
        if (respuesta.sinLicencia) {
            log.warn("La empresa {} no tiene una licencia de PLAFT registrada en Sulotec: no se cobra {}", empresa, tipo)
            return
        }
        if (respuesta.permitido == true) {
            if (respuesta.cobrado != null || respuesta.sinDescuento == true) {
                log.debug("Consumo {} de la empresa {}: Sulotec cobró {} (sin descuento: {})", tipo, empresa, respuesta.cobrado, respuesta.sinDescuento)
            }
            anotarTokens(empresa, respuesta)
            return
        }
        if (hechoPorSulotec) {
            log.info("Sulotec rechazó el consumo {} de la empresa {} ({}), pero lo hace un administrador de Sulotec: se permite", tipo, empresa, respuesta.motivo)
            return
        }
        if (LicenciaReglas.esBolsaAgotada(respuesta.motivo)) throw LicenciaBolsaAgotadaException()
        val estado = LicenciaReglas.estadoDeMotivo(respuesta.motivo)
        if (estado != null) throw bloqueo(estado)
        log.warn("Motivo de rechazo desconocido {} en el consumo {} de la empresa {}: se permite", respuesta.motivo, tipo, empresa)
    }

    private fun permitirSinRespuesta(
        empresa: String,
        tipo: String,
        clave: String,
        usuario: String,
        hechoPorSulotec: Boolean,
        detalle: String?,
        fallo: String?
    ) {
        val ultima = cache[empresa]?.licencia
        if (!hechoPorSulotec && ultima != null) {
            val estado = ultima.estado
            if (estado != null && LicenciaReglas.codigoDeEstado(estado) != null) throw bloqueo(estado)
            if (ultima.tokens?.nivel == NIVEL_BLOQUEADO) throw LicenciaBolsaAgotadaException()
        }
        // Una empresa sin PLAFT no tiene nada que cobrar: no se encola.
        if (ultima?.esSinLicencia() == true) return
        try {
            cola.encolarConsumo(empresa, clave, tipo, 1, usuario, hechoPorSulotec, detalle, fallo)
            log.warn("Sulotec no respondió: el consumo {} de la empresa {} se permite y queda en la cola", tipo, empresa)
        } catch (e: Exception) {
            log.error("No se pudo guardar en la cola el consumo {} de la empresa {} (clave {}): {}", tipo, empresa, clave, e.message)
        }
    }

    /**
     * Si la acción falla después de consumir, se devuelven los tokens. Si el consumo aún esperaba en la cola, basta con
     * quitarlo; si Sulotec no responde, la anulación queda en la cola. Nunca lanza.
     */
    fun anular(clave: String) {
        if (!properties.activo) return
        try {
            if (cola.quitarConsumoPendiente(clave)) return
        } catch (e: Exception) {
            log.warn("No se pudo revisar la cola para anular {}: {}", clave, e.message)
        }
        try {
            cliente.anular(clave)
        } catch (e: Exception) {
            log.warn("Sulotec no respondió al anular el consumo {}: queda en la cola ({})", clave, e.message)
            try {
                cola.encolarAnulacion(clave, e.message)
            } catch (e2: Exception) {
                log.error("No se pudo guardar en la cola la anulación {}: {}", clave, e2.message)
            }
        }
    }

    private fun bloqueo(estado: String) = LicenciaBloqueadaException(
        estado = estado,
        code = LicenciaReglas.codigoDeEstado(estado) ?: LicenciaReglas.CODE_CANCELADA
    )

    // ---------- Caché ----------

    private fun limpiar(valor: String?): String? = valor?.trim()?.takeIf { it.isNotEmpty() }

    private fun vigente(entrada: Entrada, ahora: Instant) =
        entrada.leidaEn.plusSeconds(properties.cacheSegundos).isAfter(ahora)

    private fun enPausa(empresa: String, ahora: Instant) = pausaHasta[empresa]?.isAfter(ahora) == true

    // 401 = el token de PLAFT está mal puesto (error: hay que corregirlo); lo demás es una caída (aviso). En ambos casos
    // se sigue con lo último conocido y se deja de preguntar un rato.
    private fun avisarFallo(empresa: String, e: Exception) {
        if (e is LicenciasNoDisponibleException && e.status == 401) {
            log.error("El servicio de licencias rechazó el token (401): revisa LICENCIAS_TOKEN. Empresa {}", empresa)
        } else {
            log.warn("El servicio de licencias no respondió para la empresa {}: {}", empresa, e.message)
        }
        pausaHasta[empresa] = reloj.instant().plusSeconds(properties.pausaTrasFalloSegundos)
    }

    // El cupo recién consultado refresca los números de la licencia en caché, sin alargar su vida.
    private fun anotarCupo(empresa: String, cupo: CupoRemoto) {
        cache.computeIfPresent(empresa) { _, entrada ->
            val l = entrada.licencia
            entrada.copy(
                licencia = l.copy(
                    estado = cupo.estado ?: l.estado,
                    usuariosActivos = cupo.activos ?: l.usuariosActivos,
                    usuariosMax = cupo.usuariosMax ?: l.usuariosMax,
                    puedeCrearUsuarios = cupo.puedeCrear ?: l.puedeCrearUsuarios
                )
            )
        }
    }

    // Lo mismo con la bolsa de tokens tras un consumo permitido.
    private fun anotarTokens(empresa: String, consumo: ConsumoRemoto) {
        cache.computeIfPresent(empresa) { _, entrada ->
            val l = entrada.licencia
            val t = l.tokens ?: return@computeIfPresent entrada
            entrada.copy(
                licencia = l.copy(
                    periodo = consumo.periodo ?: l.periodo,
                    tokens = t.copy(
                        nivel = consumo.nivel ?: t.nivel,
                        disponibles = consumo.disponibles ?: t.disponibles,
                        porcentaje = consumo.porcentaje ?: t.porcentaje,
                        capacidad = consumo.capacidad ?: t.capacidad,
                        usados = consumo.usados ?: t.usados
                    )
                )
            )
        }
    }
}
