package com.security.modules.licencias

import com.security.modules.licencias.dominio.ESTADO_ACTIVA
import com.security.modules.licencias.dominio.ESTADO_CANCELADA
import com.security.modules.licencias.dominio.ESTADO_SIN_LICENCIA
import com.security.modules.licencias.dominio.ESTADO_SOLO_LECTURA
import com.security.modules.licencias.dominio.ESTADO_SUSPENDIDA
import com.security.modules.licencias.dominio.LicenciaPublica
import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.modules.licencias.dominio.MOTIVO_BOLSA_AGOTADA
import com.security.modules.licencias.dominio.MOTIVO_SIN_LICENCIA
import com.security.modules.licencias.dominio.MOTIVO_SOLO_LECTURA
import com.security.modules.licencias.dominio.MOTIVO_SUSPENDIDA
import com.security.modules.licencias.dominio.NIVEL_BLOQUEADO
import com.security.modules.licencias.dominio.TIPO_BUSQUEDA_LISTAS
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Qué hacer con una petición: pasar, o bloquearla con un 403 (code, estado y texto para la persona). */
data class DecisionLicencia(
    val permitir: Boolean,
    val code: String? = null,
    val estado: String? = null,
    val mensaje: String? = null
) {
    companion object {
        val PERMITIR = DecisionLicencia(permitir = true)
    }
}

/**
 * Reglas de la licencia de PLAFT, sin red ni base para poder probarlas solas. PLAFT no decide reglas de negocio de
 * la licencia: solo traduce el estado que respondió el servicio común a qué pasa, qué se bloquea y qué texto se ve.
 * Ante la duda (estado nulo o uno que aún no conocemos) se permite.
 */
object LicenciaReglas {

    const val ENLACE_MI_LICENCIA = "https://sulotec.com/mi-licencia"
    const val ENLACE_LICENCIAS = "https://sulotec.com/licencias"

    const val CODE_BOLSA_AGOTADA = "LICENCIA_BOLSA_AGOTADA"
    const val CODE_SOLO_LECTURA = "LICENCIA_SOLO_LECTURA"
    const val CODE_SUSPENDIDA = "LICENCIA_SUSPENDIDA"
    const val CODE_CANCELADA = "LICENCIA_CANCELADA"
    const val CODE_SIN_CUPO = "LICENCIA_SIN_CUPO"

    const val MENSAJE_SOLO_LECTURA =
        "La licencia de PLAFT de tu empresa venció: puedes consultar la información, pero no registrar cambios. Renueva en Sulotec."
    const val MENSAJE_SUSPENDIDA =
        "La licencia de PLAFT de tu empresa está suspendida: no se pueden registrar cambios. Escribe a contacto@sulotec.com."
    const val MENSAJE_CANCELADA = "La licencia de PLAFT de tu empresa está cancelada. Escribe a contacto@sulotec.com."
    const val MENSAJE_BOLSA_AGOTADA =
        "Tu empresa usó todos los tokens de este mes, incluida la tolerancia. Amplía tu plan en Sulotec para seguir consultando."
    const val MENSAJE_SIN_LICENCIA = "Esta empresa no tiene una licencia de PLAFT registrada en Sulotec."

    private val LECTURAS = setOf("GET", "HEAD", "OPTIONS")

    // Rutas que nunca se miran: la sesión, lo público, la propia licencia (sin ella nadie vería el aviso) y las empresas.
    private val PREFIJOS_EXENTOS = listOf("/api/auth", "/api/public", "/api/licencia", "/api/empresas")

    // Escrituras que no se cortan por la licencia: el canal de denuncias y los cálculos de matriz que no guardan nada.
    private val POST_EXENTOS = setOf(
        "/api/denuncias",
        "/api/denuncias/seguimiento",
        "/api/matrices/calcular-inherente",
        "/api/matrices/calcular-residual"
    )
    private val ARCHIVOS_DENUNCIA = Regex("^/api/denuncias/[^/]+/archivos$")
    private val ESTADO_USUARIO = Regex("^/api/usuarios/[^/]+/estado$")
    private val INVITAR_USUARIO = Regex("^/api/usuarios/[^/]+/invitar$")

    // ---------- Textos y códigos ----------

    /** Texto para la persona según el estado; ACTIVA, nulo o desconocido no necesitan aviso. */
    fun mensajeDeEstado(estado: String?): String = when (estado) {
        ESTADO_SOLO_LECTURA -> MENSAJE_SOLO_LECTURA
        ESTADO_SUSPENDIDA -> MENSAJE_SUSPENDIDA
        ESTADO_CANCELADA -> MENSAJE_CANCELADA
        ESTADO_SIN_LICENCIA -> MENSAJE_SIN_LICENCIA
        else -> ""
    }

    /** Código del 403 de cada estado que bloquea; los demás (ACTIVA, SIN_LICENCIA, nulo o nuevo) devuelven null. */
    fun codigoDeEstado(estado: String?): String? = when (estado) {
        ESTADO_SOLO_LECTURA -> CODE_SOLO_LECTURA
        ESTADO_SUSPENDIDA -> CODE_SUSPENDIDA
        ESTADO_CANCELADA -> CODE_CANCELADA
        else -> null
    }

    /**
     * Estado que corresponde a un consumo rechazado. SIN_LICENCIA en un consumo rechazado es una licencia cancelada
     * (el 404 de una empresa sin PLAFT no llega aquí). BOLSA_AGOTADA o un motivo desconocido devuelven null.
     */
    fun estadoDeMotivo(motivo: String?): String? = when (motivo) {
        MOTIVO_SOLO_LECTURA -> ESTADO_SOLO_LECTURA
        MOTIVO_SUSPENDIDA -> ESTADO_SUSPENDIDA
        MOTIVO_SIN_LICENCIA -> ESTADO_CANCELADA
        else -> null
    }

    fun esBolsaAgotada(motivo: String?): Boolean = motivo == MOTIVO_BOLSA_AGOTADA

    /** Enlace del 409 de cupo: el administrador de Sulotec va al panel de licencias; el cliente, a la suya. */
    fun enlaceCupo(actorEsSulotec: Boolean): String = if (actorEsSulotec) ENLACE_LICENCIAS else ENLACE_MI_LICENCIA

    fun textoCupo(activos: Long?, usuariosMax: Long?): String {
        val cuenta = if (activos != null && usuariosMax != null) " ($activos de $usuariosMax)" else ""
        return "Tu plan llegó al máximo de usuarios activos$cuenta. Desactiva un usuario o amplía tu licencia en Sulotec."
    }

    fun textoCupoLote(cuentasNuevas: Int, libres: Long, activos: Long?, usuariosMax: Long?): String {
        val enUso = if (activos != null && usuariosMax != null) " ($activos de $usuariosMax en uso)" else ""
        return "La carga crea $cuentasNuevas cuentas y tu plan solo tiene $libres libres$enUso. " +
            "Reduce el archivo o amplía tu licencia en Sulotec."
    }

    // ---------- Guard de escrituras por estado (P5) ----------

    /** La ruta se compara sin query, sin barras repetidas ni barra final. */
    fun normalizarRuta(ruta: String?): String {
        val limpia = (ruta ?: "").substringBefore('?').replace(Regex("/+"), "/").trimEnd('/')
        return limpia.ifEmpty { "/" }
    }

    private fun bajo(ruta: String, prefijo: String) = ruta == prefijo || ruta.startsWith("$prefijo/")

    private fun desactiva(parametroActivo: String?) = parametroActivo?.trim().equals("false", ignoreCase = true)

    /**
     * Peticiones que nunca se miran: lecturas, sin autenticar, el administrador de Sulotec, las rutas exentas, el canal
     * de denuncias y los cálculos de matriz que no guardan.
     */
    fun esExenta(metodo: String?, ruta: String?, parametroActivo: String?, esSulotec: Boolean, autenticado: Boolean): Boolean {
        val m = (metodo ?: "").uppercase()
        if (m in LECTURAS) return true
        if (!autenticado || esSulotec) return true
        val r = normalizarRuta(ruta)
        if (PREFIJOS_EXENTOS.any { bajo(r, it) }) return true
        if (m == "POST" && (r in POST_EXENTOS || ARCHIVOS_DENUNCIA.matches(r))) return true
        return false
    }

    /** Desactivar una cuenta siempre se puede: libera cupo y no registra trabajo. */
    private fun esDesactivarUsuario(metodo: String, ruta: String, parametroActivo: String?) =
        metodo == "PATCH" && ESTADO_USUARIO.matches(ruta) && desactiva(parametroActivo)

    /**
     * Decide según el estado de la licencia. SOLO_LECTURA bloquea toda escritura salvo desactivar e invitar usuarios;
     * SUSPENDIDA y CANCELADA, todo salvo desactivar. ACTIVA, SIN_LICENCIA, desconocido o apagado (null): todo pasa.
     */
    fun decidir(
        metodo: String?,
        ruta: String?,
        parametroActivo: String?,
        esSulotec: Boolean,
        autenticado: Boolean,
        estado: String?
    ): DecisionLicencia {
        if (esExenta(metodo, ruta, parametroActivo, esSulotec, autenticado)) return DecisionLicencia.PERMITIR
        val code = codigoDeEstado(estado) ?: return DecisionLicencia.PERMITIR
        val m = (metodo ?: "").uppercase()
        val r = normalizarRuta(ruta)
        if (esDesactivarUsuario(m, r, parametroActivo)) return DecisionLicencia.PERMITIR
        if (estado == ESTADO_SOLO_LECTURA && m == "POST" && INVITAR_USUARIO.matches(r)) return DecisionLicencia.PERMITIR
        return DecisionLicencia(permitir = false, code = code, estado = estado, mensaje = mensajeDeEstado(estado))
    }

    // ---------- Lo que ve la web (C1) ----------

    /** null = licencias apagadas o nunca se pudo leer (la web no muestra nada). */
    fun publica(licencia: LicenciaRemota?): LicenciaPublica? {
        if (licencia == null) return null
        val estado = licencia.estado?.takeIf { it.isNotBlank() } ?: return null
        if (estado == ESTADO_SIN_LICENCIA) {
            return LicenciaPublica(
                estado = estado, plan = null, usuariosMax = null, usuariosActivos = null, puedeCrearUsuarios = null,
                vencimiento = null, diasParaVencer = null, periodo = null, tokens = null,
                mensaje = MENSAJE_SIN_LICENCIA, enlace = ENLACE_MI_LICENCIA
            )
        }
        // Con la licencia activa pero la bolsa agotada se avisa igual, para que no sea una sorpresa al consultar.
        val mensaje = if (estado == ESTADO_ACTIVA && licencia.tokens?.nivel == NIVEL_BLOQUEADO) MENSAJE_BOLSA_AGOTADA
        else mensajeDeEstado(estado)
        return LicenciaPublica(
            estado = estado,
            plan = licencia.plan,
            usuariosMax = licencia.usuariosMax,
            usuariosActivos = licencia.usuariosActivos,
            puedeCrearUsuarios = licencia.puedeCrearUsuarios,
            vencimiento = soloFecha(licencia.vencimiento),
            diasParaVencer = licencia.diasParaVencer,
            periodo = licencia.periodo,
            tokens = licencia.tokens,
            mensaje = mensaje,
            enlace = ENLACE_MI_LICENCIA,
            tarifas = licencia.tarifas.orEmpty()
        )
    }

    /** "AAAA-MM-DD" aunque el servicio mande fecha y hora. */
    private fun soloFecha(valor: String?): String? {
        val texto = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (texto.length >= 10 && Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(texto)) texto.substring(0, 10) else texto
    }

    // ---------- Claves idempotentes (P4) ----------

    /** Scoring, Indira y los registros: cada petición es una acción nueva. */
    fun claveAleatoria(uuid: String?, tipo: String): String = "plaft:${uuid ?: "sin-empresa"}:$tipo:${UUID.randomUUID()}"

    /**
     * Búsqueda en listas: la misma persona con los mismos criterios dentro de la misma ventana de dos minutos da la
     * misma clave, así un doble clic o recargar la búsqueda no cobra dos veces.
     */
    fun claveBusqueda(uuid: String?, usuario: String, criterios: List<String?>, ahora: Instant): String =
        claveDeterminista(uuid, usuario, TIPO_BUSQUEDA_LISTAS, criterios, ahora)

    /**
     * Acciones que un reintento del navegador repetiría tal cual (la búsqueda y las descargas de PDF): la misma persona,
     * la misma acción y los mismos datos dentro de la misma ventana de dos minutos dan la misma clave.
     */
    fun claveDeterminista(uuid: String?, usuario: String, tipo: String, criterios: List<String?>, ahora: Instant): String {
        val texto = (listOf(usuario) + criterios).joinToString("|") { normalizarCriterio(it) }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(texto.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        val ventana = ahora.epochSecond / 60 / 2
        return "plaft:${uuid ?: "sin-empresa"}:$tipo:$hash:$ventana"
    }

    private fun normalizarCriterio(valor: String?): String =
        valor?.trim()?.lowercase()?.replace(Regex("\\s+"), " ") ?: ""
}
