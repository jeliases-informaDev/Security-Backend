package com.security.modules.auth.sso

import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.shared.security.ROL_ADMINISTRADOR
import org.springframework.http.HttpStatus
import java.text.Normalizer

/** La cuenta de PLAFT que ya tiene ese correo, con lo único que importa para decidir. */
data class CuentaLocalSso(
    val usuario: String,
    val activo: Boolean,
    val rolCodigo: String?
)

/** Qué hacer con quien llega desde Sulotec. */
sealed interface DecisionSso {
    /** Ya tiene cuenta en PLAFT: entra con su rol y su empresa de la base de PLAFT. */
    data class Entrar(val usuario: String) : DecisionSso

    /** Administrador universal de Sulotec sin cuenta: se le crea una con rol ADMINISTRADOR. */
    data object NuevoAdministradorSulotec : DecisionSso

    /** Titular de la licencia de su empresa sin cuenta: alta automática como ADMINISTRADOR_EMPRESA. */
    data class NuevoTitular(val empresaUuid: String, val empresaNombre: String?) : DecisionSso
}

/**
 * Reglas de quién entra con la cuenta Sulotec, sin red ni base para probarlas solas. El rol y la empresa salen siempre
 * de PLAFT, nunca del token; el token solo sirve para saber quién es y, en el alta, qué es en Sulotec.
 */
object SsoReglas {

    const val ROL_SULOTEC = "administrador-general"
    const val ROL_PRODUCTO = "producto-plaft"

    const val INACTIVO = "SSO_INACTIVO"
    const val DATOS = "SSO_DATOS"
    const val CODIGO = "SSO_CODIGO"
    const val TOKEN = "SSO_TOKEN"
    const val EMISOR = "SSO_EMISOR"
    const val SIN_CORREO = "SSO_SIN_CORREO"
    const val CORREO_NO_VERIFICADO = "SSO_CORREO_NO_VERIFICADO"
    const val CUENTA_INACTIVA = "SSO_CUENTA_INACTIVA"
    const val SIN_PERMISO = "SSO_SIN_PERMISO"
    const val SIN_CUENTA = "SSO_SIN_CUENTA"
    const val SIN_LICENCIA = "SSO_SIN_LICENCIA"
    const val EMPRESA_EN_CONFLICTO = "SSO_EMPRESA_EN_CONFLICTO"

    const val MENSAJE_SIN_CUENTA =
        "Tu cuenta Sulotec todavía no tiene usuario en PLAFT. Pide al administrador de tu empresa que te cree la cuenta con este mismo correo."

    fun inactivo() = SsoException(HttpStatus.NOT_FOUND, INACTIVO, "El inicio de sesión con la cuenta Sulotec no está habilitado en este servidor.")
    fun datos(mensaje: String = "Faltan datos para terminar el inicio de sesión con Sulotec. Vuelve a intentarlo.") =
        SsoException(HttpStatus.BAD_REQUEST, DATOS, mensaje)
    fun codigo() = SsoException(HttpStatus.UNAUTHORIZED, CODIGO, "Sulotec no aceptó el inicio de sesión: el enlace venció o ya se usó. Vuelve a intentarlo.")
    fun token() = SsoException(HttpStatus.UNAUTHORIZED, TOKEN, "No se pudo comprobar tu identidad con Sulotec. Vuelve a intentarlo.")
    // 424 y no 502/504: Cloudflare reemplaza esas respuestas y la persona no vería el mensaje.
    fun emisor(mensaje: String = "No se pudo conectar con la cuenta Sulotec. Intenta de nuevo en unos minutos.") =
        SsoException(HttpStatus.FAILED_DEPENDENCY, EMISOR, mensaje)

    /** El correo de la persona, o el error que corresponde. Se compara siempre sin distinguir mayúsculas. */
    fun correoDe(identidad: SsoIdentidad): String {
        val correo = identidad.correo?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw SsoException(HttpStatus.FORBIDDEN, SIN_CORREO, "Tu cuenta Sulotec no tiene un correo registrado. Agrégalo en cuenta.sulotec.com y vuelve a intentarlo.")
        if (identidad.correoVerificado == false) {
            throw SsoException(HttpStatus.FORBIDDEN, CORREO_NO_VERIFICADO, "Confirma tu correo en cuenta.sulotec.com antes de entrar a PLAFT.")
        }
        return correo
    }

    /**
     * Decide quién entra. local = la cuenta de PLAFT con ese correo (o null). licenciaDe(uuid) = la licencia de esa
     * empresa preguntada a Sulotec en vivo, o null si las licencias están apagadas; solo se llama en el alta del titular.
     */
    fun decidir(
        identidad: SsoIdentidad,
        local: CuentaLocalSso?,
        licenciaDe: (String) -> LicenciaRemota?
    ): DecisionSso {
        val correo = correoDe(identidad)
        val esSulotec = ROL_SULOTEC in identidad.roles

        if (local != null) {
            if (!local.activo) {
                throw SsoException(HttpStatus.FORBIDDEN, CUENTA_INACTIVA, "Tu usuario de PLAFT está desactivado. Pide al administrador de tu empresa que lo active.")
            }
            val rol = local.rolCodigo?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw SsoException(HttpStatus.FORBIDDEN, SIN_PERMISO, "Tu usuario de PLAFT no tiene un rol asignado. Pide al administrador de tu empresa que lo revise.")
            // Doble llave: el rol más poderoso de PLAFT exige además ser administrador universal en Sulotec.
            if (rol == ROL_ADMINISTRADOR && !esSulotec) {
                throw SsoException(HttpStatus.FORBIDDEN, SIN_PERMISO, "Tu usuario de PLAFT es de administrador de Sulotec, pero tu cuenta Sulotec no lo es. Entra con tu usuario y contraseña de PLAFT o pide a Sulotec que lo revise.")
            }
            return DecisionSso.Entrar(local.usuario)
        }

        if (esSulotec) return DecisionSso.NuevoAdministradorSulotec

        val uuid = identidad.empresaUuid?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (ROL_PRODUCTO !in identidad.roles || uuid == null) throw sinCuenta()

        val licencia = licenciaDe(uuid)
        if (licencia == null || licencia.esSinLicencia()) {
            throw SsoException(HttpStatus.FORBIDDEN, SIN_LICENCIA, "Tu empresa no tiene una licencia de PLAFT registrada en Sulotec. Escribe a contacto@sulotec.com.")
        }
        val titular = licencia.titular?.email?.trim()
        if (titular.isNullOrEmpty() || !titular.equals(correo, ignoreCase = true)) throw sinCuenta()

        val nombre = licencia.empresaNombre?.trim()?.takeIf { it.isNotEmpty() }
            ?: identidad.empresaNombre?.trim()?.takeIf { it.isNotEmpty() }
        return DecisionSso.NuevoTitular(uuid, nombre)
    }

    fun sinCuenta() = SsoException(HttpStatus.FORBIDDEN, SIN_CUENTA, MENSAJE_SIN_CUENTA)

    fun empresaEnConflicto(nombre: String) = SsoException(
        HttpStatus.CONFLICT,
        EMPRESA_EN_CONFLICTO,
        "En PLAFT ya existe la empresa «$nombre», pero con otro identificador que el de Sulotec. Pide a Sulotec que iguale el UUID de la empresa y vuelve a intentarlo."
    )

    /** Parte local del correo, apta como usuario: minúsculas, solo letras, dígitos, punto, guion y guion bajo. */
    fun usuarioBase(correo: String): String {
        val local = Normalizer.normalize(correo.substringBefore('@').trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        val limpio = local.replace(Regex("[^a-z0-9._-]"), "").trim('.', '-', '_').take(60)
        return limpio.ifEmpty { "usuario" }
    }
}
