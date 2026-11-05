package com.security.modules.auth.sso

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

/**
 * Inicio único con la cuenta Sulotec (Keycloak, OpenID Connect con PKCE). Con SSO_ISSUER vacío (o sin cliente o sin
 * dirección de retorno) está apagado: GET /api/auth/sso/config responde activo = false y POST /api/auth/sso, 404.
 */
@ConfigurationProperties(prefix = "sso")
data class SsoProperties(
    val issuer: String = "",
    val clientId: String = "plaft",
    // Dirección exacta de retorno del front (en producción https://plaft.sulotec.com/sso); va igual en el canje.
    val redirectUri: String = "",
    // Espera máxima por llamada a Keycloak.
    val timeoutSegundos: Long = 8
) {
    val activo: Boolean
        get() = issuer.isNotBlank() && clientId.isNotBlank() && redirectUri.isNotBlank()

    /** Emisor sin espacios ni barra final: es el valor exacto del "iss" del id_token. */
    val issuerBase: String
        get() = issuer.trim().trimEnd('/')
}

@Configuration
@EnableConfigurationProperties(SsoProperties::class)
class SsoConfig

/** GET /api/auth/sso/config: { activo: false } o los datos que el front necesita para ir a Sulotec. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SsoConfigResponse(
    val activo: Boolean,
    val issuer: String? = null,
    val clientId: String? = null,
    val redirectUri: String? = null
)

/** POST /api/auth/sso. Los dos son obligatorios; se validan a mano para responder SSO_DATOS. */
data class SsoLoginRequest(
    val code: String? = null,
    val codeVerifier: String? = null
)

/** Lo que dice el id_token verificado de la persona. */
data class SsoIdentidad(
    val correo: String?,
    // null = el token no lo dice (solo se rechaza si dice false explícito).
    val correoVerificado: Boolean?,
    val nombres: String?,
    val apellidos: String?,
    val roles: Set<String>,
    val empresaUuid: String?,
    val empresaNombre: String?
)

/** Error del inicio único: status, code y un mensaje para una persona no técnica (lo resuelve GlobalExceptionHandler). */
class SsoException(
    val status: HttpStatus,
    val code: String,
    message: String
) : RuntimeException(message)

/**
 * Canje del código y verificación del id_token, detrás de una interfaz para probar las reglas con dobles (como
 * LicenciasCliente). Devuelve la identidad verificada o lanza SsoException (SSO_CODIGO, SSO_TOKEN o SSO_EMISOR).
 */
interface SsoProveedor {
    fun canjear(code: String, codeVerifier: String): SsoIdentidad
}
