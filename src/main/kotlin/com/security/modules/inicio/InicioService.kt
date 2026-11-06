package com.security.modules.inicio

import com.security.modules.denuncias.application.service.PlazoDenunciasService
import com.security.modules.denuncias.domain.enums.ResultadoCierreDenuncia
import com.security.modules.listas.application.service.ReglasListasService
import com.security.modules.listas.domain.reglas.CalculoListas
import com.security.modules.listas.domain.reglas.Clasificacion
import com.security.modules.oficial.aplicacion.AccesoOficialService
import com.security.modules.oficial.aplicacion.FichaOficialService
import com.security.modules.oficial.aplicacion.OficialException
import com.security.modules.oficial.aplicacion.ReglasOficialService
import com.security.modules.oficial.aplicacion.ReglasPorDefecto
import com.security.modules.oficial.dominio.EstadoPlazo
import com.security.modules.oficial.dominio.TextosOc
import com.security.modules.operaciones.application.service.CatalogosOperacion
import com.security.modules.operaciones.application.service.CatalogosRegistro
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.security.ROL_ADMINISTRADOR
import com.security.shared.security.ROL_ADMINISTRADOR_EMPRESA
import com.security.shared.security.limpiarEmpresa
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * GET /api/inicio/resumen: las cifras de la pagina principal. La empresa y los permisos salen de la cuenta autenticada.
 * Cada bloque usa la misma regla de visibilidad que su modulo; si la cuenta no puede verlo, el bloque va null.
 * Detalle de cada cifra: docs/INICIO.md.
 */
@Service
class InicioService(
    private val usuarios: UsuarioRepository,
    private val consultas: InicioConsultas,
    private val reglasListas: ReglasListasService,
    private val catalogosOperaciones: CatalogosRegistro,
    private val accesoOficial: AccesoOficialService,
    private val fichasOficial: FichaOficialService,
    private val reglasOficial: ReglasOficialService,
    private val demo: DemoInicioService,
    private val reloj: RelojInicio,
    private val plazoDenuncias: PlazoDenunciasService,
    private val cabinaService: CabinaService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val DIAS_SERIE = 14
        const val META_MISMO_DIA = 95.0
        const val ULTIMOS_REGISTROS = 3
        const val TOP_FUENTES = 5
        private val ES = Locale.forLanguageTag("es-PE")

        fun etiqueta(p: YearMonth): String =
            p.month.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.titlecase(ES) } + " " + p.year

    }

    // Sin transaccion propia a proposito: cada consulta lleva la suya, asi un bloque que falla no arrastra a los demas.
    fun resumen(username: String, periodoTexto: String?): ResumenInicioResponse {
        val usuario = usuarios.findByUsuario(username)
            ?: throw InicioException(HttpStatus.FORBIDDEN, "INICIO_SIN_PERMISO", "Usuario no disponible")
        val periodo = try {
            periodoTexto?.trim()?.takeIf { it.isNotEmpty() }?.let { YearMonth.parse(it) } ?: YearMonth.from(reloj.hoy())
        } catch (_: DateTimeParseException) {
            throw InicioException(HttpStatus.BAD_REQUEST, "INICIO_PERIODO_INVALIDO", "El periodo debe tener la forma AAAA-MM (por ejemplo 2026-11).")
        }
        val roles = rolesDe(usuario)
        val empresa = limpiarEmpresa(usuario.empresa)
        val esUniversal = ROL_ADMINISTRADOR in roles
        return ResumenInicioResponse(
            periodo = periodo.toString(),
            periodoEtiqueta = etiqueta(periodo),
            demo = DemoEstado(cargado = empresa != null && demo.cargado(empresa), puedeGestionar = esUniversal),
            listas = bloque("listas") { listas(usuario, roles, empresa, periodo) },
            operaciones = bloque("operaciones") { operaciones(roles, empresa, periodo) },
            oficial = bloque("oficial") { oficial() },
            denuncias = bloque("denuncias") { denuncias(usuario, roles, empresa, periodo) },
            cabina = bloque("cabina") { cabina(usuario, roles, empresa, periodo) }
        )
    }

    /**
     * Cabina de mando: solo para quien mira toda la empresa (administrador universal o de empresa). Cada instrumento usa
     * el alcance de su modulo; la licencia la ven el universal y el titular (administrador de empresa).
     */
    private fun cabina(usuario: Usuario, roles: Set<String>, empresa: String?, periodo: YearMonth): CabinaInicio? {
        val universal = ROL_ADMINISTRADOR in roles
        if (!universal && !(ROL_ADMINISTRADOR_EMPRESA in roles && empresa != null)) return null
        val alcance = alcanceListas(usuario, roles, empresa)
        return cabinaService.cabina(
            AlcancesCabina(
                listas = alcance, scoring = alcance, empresaOperaciones = empresa,
                denuncias = alcanceDenuncias(usuario, roles, empresa), empresaLicencia = empresa, veLicencia = true
            ),
            periodo, finSerie(periodo)
        )
    }

    private fun alcanceListas(usuario: Usuario, roles: Set<String>, empresa: String?): AlcanceInicio = when {
        empresa != null && (ROL_ADMINISTRADOR in roles || ROL_ADMINISTRADOR_EMPRESA in roles) -> AlcanceInicio(false, empresa, null)
        empresa == null && ROL_ADMINISTRADOR in roles -> AlcanceInicio(true, null, null)
        else -> AlcanceInicio(false, null, usuario.id)
    }

    private fun alcanceDenuncias(usuario: Usuario, roles: Set<String>, empresa: String?): AlcanceInicio? = when {
        ROL_ADMINISTRADOR in roles -> if (empresa != null) AlcanceInicio(false, empresa, null) else AlcanceInicio(true, null, null)
        ROL_ADMINISTRADOR_EMPRESA in roles && empresa != null -> AlcanceInicio(false, empresa, null)
        "SUPERVISOR" in roles && usuario.id != null -> AlcanceInicio(false, null, usuario.id)
        else -> null
    }

    // Un bloque que falla no tumba la pagina principal: se registra y ese bloque va null.
    private fun <T> bloque(nombre: String, calculo: () -> T?): T? = try {
        calculo()
    } catch (e: InicioException) {
        throw e
    } catch (e: Exception) {
        log.warn("Inicio: no se pudo calcular el bloque {}: {}", nombre, e.message)
        null
    }

    private fun rolesDe(u: Usuario): Set<String> =
        u.roles.filter { it.rol.activo }.map { it.rol.codigo.trim().uppercase() }.toSet()

    /** El dia final de las series: hoy si el periodo es el mes en curso; si no, el ultimo dia del mes. */
    private fun finSerie(periodo: YearMonth): LocalDate {
        val hoy = reloj.hoy()
        return if (YearMonth.from(hoy) == periodo) hoy else periodo.atEndOfMonth()
    }

    // ================================================================== Listas negativas

    private fun listas(usuario: Usuario, roles: Set<String>, empresa: String?, periodo: YearMonth): ListasInicio {
        // Siempre dentro de la empresa de la cuenta. Sin empresa: el universal ve todo; las demas cuentas, lo propio.
        val a = alcanceListas(usuario, roles, empresa)
        val desde = periodo.atDay(1).atStartOfDay()
        val hasta = periodo.plusMonths(1).atDay(1).atStartOfDay()
        val grupos = consultas.gruposConsultas(a, desde, hasta)
        val total = grupos.sumOf { it.cantidad }
        val anterior = consultas.contarConsultas(a, periodo.minusMonths(1).atDay(1).atStartOfDay(), desde)

        // Reparto por la peor coincidencia: Critica (clasificacion CRITICA), Alta (MEDIA con alguna fila del grupo de
        // peso ALTA), Media (el resto de MEDIA). Las filas con coincidencia estan en listas_coincidencia_texto.
        val reglas = reglasListas.reglas()
        val filaPorNombre = reglas.filas.associateBy { CalculoListas.normalizar(it.nombre) }
        var criticas = 0L
        var altas = 0L
        var medias = 0L
        var sin = 0L
        val porFuente = linkedMapOf<String, Long>()
        grupos.forEach { g ->
            val filas = g.filas?.split(" | ")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                .map { filaPorNombre[CalculoListas.normalizar(it)] }
            when (g.clasificacion) {
                Clasificacion.CRITICA -> criticas += g.cantidad
                Clasificacion.MEDIA -> if (filas.any { it?.grupoPeso.equals("ALTA", ignoreCase = true) }) altas += g.cantidad else medias += g.cantidad
                else -> sin += g.cantidad
            }
            if (g.clasificacion != Clasificacion.SIN_COINCIDENCIAS) {
                filas.map { it?.fuenteNombre ?: CalculoListas.FUENTE_OTRAS_NOMBRE }.distinct().forEach {
                    porFuente[it] = (porFuente[it] ?: 0L) + g.cantidad
                }
            }
        }
        val con = criticas + altas + medias

        val fin = finSerie(periodo)
        val inicioSerie = fin.minusDays((DIAS_SERIE - 1).toLong())
        val dias = consultas.consultasPorDia(a, inicioSerie.atStartOfDay(), fin.plusDays(1).atStartOfDay()).associateBy { it.dia }
        val serie = (0 until DIAS_SERIE).map { dias[inicioSerie.plusDays(it.toLong())] }
        val (pendientes, masAntigua) = consultas.criticasSinDecidir(a)

        return ListasInicio(
            consultas = total,
            consultasVariacionPct = if (anterior == 0L) null else Math.round((total - anterior) * 100.0 / anterior).toDouble(),
            consultasSerie = serie.map { it?.total ?: 0L },
            conCoincidenciaPct = porcentaje(con, total),
            conCoincidenciaSerie = serie.map { d -> d?.let { porcentaje(it.conCoincidencia, it.total) } ?: 0.0 },
            pepDetectados = grupos.sumOf { it.pep },
            pepSerie = serie.map { it?.pep ?: 0L },
            homonimosDescartados = grupos.sumOf { it.homonimos },
            homonimosSerie = serie.map { it?.homonimos ?: 0L },
            resultado = ResultadoConsultas(
                criticaPct = porcentaje(criticas, total) ?: 0.0, altaPct = porcentaje(altas, total) ?: 0.0,
                mediaPct = porcentaje(medias, total) ?: 0.0, sinCoincidenciaPct = porcentaje(sin, total) ?: 0.0
            ),
            porFuente = porFuente.entries.sortedByDescending { it.value }.take(TOP_FUENTES).map { FuenteCoincidencias(it.key, it.value) },
            criticasSinDecidir = CriticasSinDecidir(
                cantidad = pendientes,
                masAntiguaDias = masAntigua?.let { maxOf(0L, ChronoUnit.DAYS.between(it.toLocalDate(), reloj.hoy())).toInt() }
            )
        )
    }

    // ================================================================== Registro de operaciones

    private fun operaciones(roles: Set<String>, empresa: String?, periodo: YearMonth): OperacionesInicio? {
        // Como el modulo: todas las cuentas de la empresa ven sus registros; sin empresa no hay a cual mirar.
        val e = empresa ?: return null
        val c = consultas.cifrasOperaciones(e, periodo.atDay(1), periodo.atEndOfMonth())
        val cat = catalogosOperaciones.cargar()
        val ultimos = consultas.ultimosRegistros(e, ULTIMOS_REGISTROS).map { f ->
            UltimoRegistro(
                id = f.id,
                codigo = f.numero,
                persona = when {
                    !f.apellidos.isNullOrBlank() || !f.nombres.isNullOrBlank() ->
                        listOfNotNull(f.apellidos?.trim()?.takeIf { it.isNotEmpty() }, f.nombres?.trim()?.takeIf { it.isNotEmpty() }).joinToString(", ")
                    else -> f.razonSocial?.trim()?.takeIf { it.isNotEmpty() }
                },
                tipo = cat.nombre(CatalogosOperacion.TIPO_OPERACION, f.tipoOperacion),
                monto = f.monto,
                moneda = f.moneda?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() },
                estado = when {
                    f.responsableId == null -> "SIN_RESPONSABLE"
                    f.estadoAnalisis == "EN_ANALISIS" || f.estadoAnalisis == "SOSPECHOSA" -> "INUSUAL"
                    f.fechaEnvio != null -> "ENVIADO"
                    else -> "REGISTRADO"
                }
            )
        }
        return OperacionesInicio(
            registros = c.registros,
            montoTotalUsd = c.montoUsd.setScale(2, RoundingMode.HALF_UP).toDouble(),
            inusuales = c.inusuales,
            sobreUmbral = c.sobreUmbral,
            mismoDiaPct = porcentaje(c.mismoDia, c.registros),
            metaMismoDiaPct = META_MISMO_DIA,
            ultimos = ultimos
        )
    }

    // ================================================================== Oficial de cumplimiento

    private fun oficial(): OficialInicio? {
        // Misma regla que el modulo (todas las cuentas de la empresa lo leen); sin empresa: sin bloque.
        val ctx = try {
            accesoOficial.contexto(null)
        } catch (_: OficialException) {
            return null
        }
        val estado = fichasOficial.estado(ctx)
        val titular = fichasOficial.titularActivo(ctx.empresaId)
        if (estado.estado == "SIN_OFICIAL") {
            return OficialInicio("SIN_OFICIAL", null, null, null, null, null, null)
        }
        val vencidos = estado.pendientes.any { it.plazo?.estado == EstadoPlazo.VENCIDO.name } ||
            estado.plazoComunicacion?.estado == EstadoPlazo.VENCIDO.name ||
            estado.vacancia?.plazoDesignar?.estado == EstadoPlazo.VENCIDO.name ||
            estado.vacancia?.plazoComunicarRemocion?.estado == EstadoPlazo.VENCIDO.name
        val comunicado = titular?.comFecha?.let { fecha ->
            val vence = reglasOficial.motor().vencimiento(ReglasPorDefecto.PLAZO_COMUNICAR_DESIGNACION, titular.fechaDesignacion)
            ComunicadoUif(fecha, !fecha.isAfter(vence))
        }
        return OficialInicio(
            estado = estado.estado,
            nombre = estado.titular?.nombreCompleto ?: estado.vacancia?.nombreCompleto,
            cargo = estado.titular?.tipoCargo,
            alDia = !vencidos,
            comunicadoUif = comunicado,
            declaraciones = titular?.let { d ->
                DeclaracionesOficial(FichaOficialService.puntos(d).size, TextosOc.LETRAS.size)
            },
            alterno = null
        )
    }

    // ================================================================== Canal de denuncias

    private fun denuncias(usuario: Usuario, roles: Set<String>, empresa: String?, periodo: YearMonth): DenunciasInicio? {
        // Misma regla que AutorizacionDenunciaService: universal y administrador de empresa, su empresa; supervisor, lo
        // asignado a el; las demas cuentas no gestionan denuncias (bloque null).
        val a = alcanceDenuncias(usuario, roles, empresa) ?: return null
        val desde = periodo.atDay(1).atStartOfDay()
        val hasta = periodo.plusMonths(1).atDay(1).atStartOfDay()
        val plazoDenunciasDias = plazoDenuncias.dias
        // Vencida = no cerrada y hoy > creacion + plazo (misma regla que el modulo de denuncias).
        val abiertas = consultas.abiertas(a, reloj.hoy().minusDays(plazoDenunciasDias).atStartOfDay())
        val porResultado = consultas.cierresPorResultado(a, desde, hasta)
        val conResultado = porResultado.values.sum()
        val cerradas = consultas.cerradas(a, desde, hasta)
        val duraciones = cerradas.map { (creada, cerrada) -> maxOf(0L, ChronoUnit.DAYS.between(creada.toLocalDate(), cerrada.toLocalDate())) }
        return DenunciasInicio(
            periodoEtiqueta = etiqueta(periodo),
            vencidas = abiertas.vencidas,
            recibidas = consultas.recibidas(a, desde, hasta),
            porAtender = abiertas.porAtender,
            sinAbrir = abiertas.sinAbrir,
            plazosATiempoPct = porcentaje(duraciones.count { it <= plazoDenunciasDias }.toLong(), duraciones.size.toLong()),
            porSituacion = consultas.porCausa(a, desde, hasta).sortedByDescending { it.second }
                .take(6).map { SituacionDenuncias(it.first.etiqueta, it.second) },
            cierres = if (conResultado == 0L) null else CierresDenuncias(
                fundadaPct = porcentaje(porResultado[ResultadoCierreDenuncia.FUNDADA] ?: 0L, conResultado) ?: 0.0,
                infundadaPct = porcentaje(porResultado[ResultadoCierreDenuncia.INFUNDADA] ?: 0L, conResultado) ?: 0.0,
                archivadaPct = porcentaje(porResultado[ResultadoCierreDenuncia.ARCHIVADA] ?: 0L, conResultado) ?: 0.0
            ),
            tiempoPromedioCierreDias = if (duraciones.isEmpty()) null else Math.round(duraciones.average()).toInt(),
            derivadasAlOficial = consultas.derivadas(a, desde, hasta)
        )
    }
}
