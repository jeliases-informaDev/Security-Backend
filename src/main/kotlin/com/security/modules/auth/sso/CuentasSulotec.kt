package com.security.modules.auth.sso

import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.cliente.LicenciasCliente
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

/** Lo que ve la web de la cuenta Sulotec de una persona. claveTemporal solo se devuelve: nunca se guarda ni va al log. */
data class CuentaSulotecResponse(
    val estado: String,
    val invitacion: String,
    val mensaje: String,
    val claveTemporal: String?
) {
    override fun toString(): String = "CuentaSulotecResponse(estado=$estado, invitacion=$invitacion)"
}

/** POST /api/usuarios/{id}/invitacion. */
data class InvitacionSulotecResponse(val cuentaSulotec: CuentaSulotecResponse)

/**
 * Cuentas de los usuarios en Sulotec. «Modo Sulotec» de una empresa = inicio único activo + licencias activas + la
 * empresa tiene licencia en Sulotec. En ese modo las cuentas nuevas se crean también en Sulotec (Sulotec manda la
 * invitación) y en PLAFT se guarda una contraseña aleatoria que nadie conoce. Fuera de ese modo nada cambia.
 */
@Service
class CuentasSulotec(
    private val sso: SsoProperties,
    private val licencias: LicenciaService,
    private val cliente: LicenciasCliente
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Inicio único y licencias encendidos (sin mirar ninguna empresa). */
    val encendido: Boolean
        get() = sso.activo && licencias.activo

    /**
     * ¿La empresa está en modo Sulotec? Sin UUID, no. Si su licencia nunca se pudo leer (Sulotec caído) se toma como sí:
     * así nunca se guarda una contraseña en PLAFT por una caída, y el alta responde 424 hasta que Sulotec vuelva.
     */
    fun modoSulotec(empresaUuid: String?): Boolean {
        if (!encendido) return false
        val uuid = empresaUuid?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val licencia = licencias.licenciaDe(uuid)
        return licencia == null || !licencia.esSinLicencia()
    }

    /**
     * Pide a Sulotec que la persona tenga cuenta en esa empresa. Devuelve lo que dijo Sulotec si la cuenta quedó
     * (creada o existente); si no, lanza el error para la persona (409 o 424, nunca 502 ni 504).
     */
    fun asegurar(empresaUuid: String, correo: String, nombre: String?, reinvitar: Boolean): CuentaSulotecResponse {
        val respuesta = try {
            cliente.asegurarCuenta(empresaUuid, correo, nombre, reinvitar)
        } catch (e: Exception) {
            log.warn("Sulotec no respondió al asegurar la cuenta de la empresa {}: {}", empresaUuid, e.message)
            throw noDisponible()
        }
        val mensaje = respuesta.mensaje?.trim()?.takeIf { it.isNotEmpty() }
        if (respuesta.sinLicencia) {
            throw noDisponible("Tu empresa no tiene una licencia de PLAFT registrada en Sulotec: no se pudo crear la cuenta.")
        }
        return when (val estado = respuesta.estado?.trim()?.lowercase()) {
            ESTADO_CREADA, ESTADO_EXISTENTE -> CuentaSulotecResponse(
                estado = estado,
                invitacion = respuesta.invitacion?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "ninguna",
                mensaje = mensaje ?: if (estado == ESTADO_CREADA) "Se creó la cuenta Sulotec." else "La persona ya tenía cuenta Sulotec.",
                claveTemporal = respuesta.claveTemporal?.takeIf { it.isNotBlank() }
            )
            "otra_empresa" -> throw SsoException(
                HttpStatus.CONFLICT, CODE_OTRA_EMPRESA,
                mensaje ?: "Ese correo ya tiene una cuenta Sulotec de otra empresa."
            )
            "sin_empresa" -> throw SsoException(
                HttpStatus.CONFLICT, CODE_SIN_EMPRESA,
                mensaje ?: "Ese correo ya tiene una cuenta Sulotec que no pertenece a ninguna empresa. Escribe a contacto@sulotec.com."
            )
            else -> {
                log.warn("Sulotec no pudo asegurar la cuenta de la empresa {} (estado {})", empresaUuid, estado)
                throw noDisponible()
            }
        }
    }

    companion object {
        const val ESTADO_CREADA = "creada"
        const val ESTADO_EXISTENTE = "existente"
        const val CODE_OTRA_EMPRESA = "CUENTA_SULOTEC_OTRA_EMPRESA"
        const val CODE_SIN_EMPRESA = "CUENTA_SULOTEC_SIN_EMPRESA"
        const val CODE_NO_DISPONIBLE = "CUENTA_SULOTEC_NO_DISPONIBLE"
        const val CODE_APAGADA = "CUENTA_SULOTEC_APAGADA"
        const val CODE_CARGA_MASIVA = "CUENTA_SULOTEC_CARGA_MASIVA"
        const val MENSAJE_NO_DISPONIBLE = "No se pudo crear la cuenta en Sulotec; inténtalo en unos minutos."

        fun noDisponible(mensaje: String = MENSAJE_NO_DISPONIBLE) =
            SsoException(HttpStatus.FAILED_DEPENDENCY, CODE_NO_DISPONIBLE, mensaje)

        fun apagada() = SsoException(
            HttpStatus.CONFLICT, CODE_APAGADA,
            "Esta cuenta no usa la cuenta Sulotec: no hay invitación que reenviar."
        )
    }
}
