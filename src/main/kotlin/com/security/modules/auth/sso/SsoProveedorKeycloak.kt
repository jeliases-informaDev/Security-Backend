package com.security.modules.auth.sso

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Header
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Locator
import io.jsonwebtoken.ProtectedHeader
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.math.BigInteger
import java.net.http.HttpClient
import java.security.Key
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenKeycloak(@JsonProperty("id_token") val idToken: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JwkKeycloak(
    val kid: String? = null,
    val kty: String? = null,
    val use: String? = null,
    val n: String? = null,
    val e: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JwksKeycloak(val keys: List<JwkKeycloak>? = null)

/**
 * Canje del código en {issuer}/protocol/openid-connect/token (authorization_code + PKCE, cliente público) y
 * verificación del id_token: firma RS256 con las llaves públicas de {issuer}/protocol/openid-connect/certs (en caché
 * unos 10 minutos; ante una llave desconocida se relee, como mucho una vez por minuto), emisor, audiencia y vencimiento.
 */
@Component
class SsoProveedorKeycloak(private val props: SsoProperties) : SsoProveedor {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Reloj de la caché de llaves; solo las pruebas lo cambian. */
    var reloj: Clock = Clock.systemUTC()

    private data class Llaves(val porKid: Map<String, PublicKey>, val leidasEn: Instant)

    @Volatile
    private var llaves: Llaves? = null

    // Se arma al primer uso: con el inicio único apagado nunca se construye.
    private val http: RestClient by lazy { construir() }

    private fun construir(): RestClient {
        val espera = Duration.ofSeconds(props.timeoutSegundos)
        val httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(espera)
            .build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(espera) }
        return RestClient.builder()
            .requestFactory(factory)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    override fun canjear(code: String, codeVerifier: String): SsoIdentidad = verificar(pedirIdToken(code, codeVerifier))

    private fun pedirIdToken(code: String, codeVerifier: String): String {
        val formulario = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", props.clientId.trim())
            add("code", code)
            add("redirect_uri", props.redirectUri.trim())
            add("code_verifier", codeVerifier)
        }
        val respuesta = try {
            http.post()
                .uri("${props.issuerBase}/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario)
                .retrieve()
                .body(TokenKeycloak::class.java)
        } catch (e: RestClientResponseException) {
            val status = e.statusCode.value()
            if (status in 400..499) {
                // invalid_grant: el código venció, ya se usó o no corresponde al verificador o a la dirección de retorno.
                log.warn("Sulotec rechazó el canje del código ({}): {}", status, e.responseBodyAsString.take(300))
                throw SsoReglas.codigo()
            }
            log.warn("Sulotec respondió {} al canjear el código", status)
            throw SsoReglas.emisor()
        } catch (e: Exception) {
            log.warn("No se pudo canjear el código con Sulotec: {}", e.message)
            throw SsoReglas.emisor()
        }
        return respuesta?.idToken?.takeIf { it.isNotBlank() } ?: throw SsoReglas.token()
    }

    /** Verifica el id_token y devuelve la identidad. Pública para las pruebas (con usarLlaves). */
    fun verificar(idToken: String): SsoIdentidad {
        cargarLlaves(forzar = false)
        val claims: Claims = try {
            Jwts.parser()
                .keyLocator(Locator<Key> { header -> llaveDe(header) })
                .requireIssuer(props.issuerBase)
                .requireAudience(props.clientId.trim())
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(idToken)
                .payload
        } catch (e: SsoException) {
            throw e
        } catch (e: Exception) {
            log.info("id_token de Sulotec rechazado: {}", e.message)
            throw SsoReglas.token()
        }
        if (claims.expiration == null) throw SsoReglas.token()
        return identidadDe(claims)
    }

    /** Solo para las pruebas: fija las llaves públicas como si se acabaran de leer. */
    fun usarLlaves(porKid: Map<String, PublicKey>) {
        llaves = Llaves(porKid, reloj.instant())
    }

    private fun llaveDe(header: Header): Key {
        if (header.algorithm != "RS256") throw SsoReglas.token()
        val kid = (header as? ProtectedHeader)?.keyId ?: throw SsoReglas.token()
        llaves?.porKid?.get(kid)?.let { return it }
        // Llave nueva (Sulotec rotó las llaves): se relee la lista.
        cargarLlaves(forzar = true)
        return llaves?.porKid?.get(kid) ?: throw SsoReglas.token()
    }

    private fun cargarLlaves(forzar: Boolean) {
        val ahora = reloj.instant()
        val actuales = llaves
        if (actuales != null) {
            val edad = Duration.between(actuales.leidasEn, ahora)
            if (!forzar && edad < CACHE_LLAVES) return
            if (forzar && edad < RELECTURA_MINIMA) return
        }
        val leidas = try {
            leerLlaves()
        } catch (e: Exception) {
            log.warn("No se pudieron leer las llaves públicas de Sulotec: {}", e.message)
            null
        }
        if (leidas != null && leidas.isNotEmpty()) {
            llaves = Llaves(leidas, ahora)
            return
        }
        // Ante una caída se sigue con las últimas conocidas; si nunca se leyeron, no hay cómo verificar.
        if (actuales == null) throw SsoReglas.emisor()
    }

    private fun leerLlaves(): Map<String, PublicKey> {
        val jwks = http.get()
            .uri("${props.issuerBase}/protocol/openid-connect/certs")
            .retrieve()
            .body(JwksKeycloak::class.java)
        val fabrica = KeyFactory.getInstance("RSA")
        val decodificador = Base64.getUrlDecoder()
        return jwks?.keys.orEmpty()
            .filter { it.kty == "RSA" && (it.use == null || it.use == "sig") && !it.kid.isNullOrBlank() && it.n != null && it.e != null }
            .associate { jwk ->
                val modulo = BigInteger(1, decodificador.decode(jwk.n))
                val exponente = BigInteger(1, decodificador.decode(jwk.e))
                jwk.kid!! to fabrica.generatePublic(RSAPublicKeySpec(modulo, exponente))
            }
    }

    private fun identidadDe(claims: Claims): SsoIdentidad {
        val verificado = when (val valor = claims["email_verified"]) {
            is Boolean -> valor
            is String -> valor.trim().lowercase().toBooleanStrictOrNull()
            else -> null
        }
        val roles = listaDeTexto(claims["roles"]) +
            listaDeTexto((claims["realm_access"] as? Map<*, *>)?.get("roles"))
        return SsoIdentidad(
            correo = texto(claims["email"]),
            correoVerificado = verificado,
            nombres = texto(claims["given_name"]),
            apellidos = texto(claims["family_name"]),
            roles = roles,
            empresaUuid = texto(claims["empresa_uuid"]),
            empresaNombre = texto(claims["empresa_nombre"])
        )
    }

    private fun texto(valor: Any?): String? = (valor as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun listaDeTexto(valor: Any?): Set<String> =
        (valor as? Collection<*>)?.mapNotNull { (it as? String)?.trim()?.takeIf { r -> r.isNotEmpty() } }?.toSet() ?: emptySet()

    companion object {
        private val CACHE_LLAVES: Duration = Duration.ofMinutes(10)
        private val RELECTURA_MINIMA: Duration = Duration.ofMinutes(1)
    }
}
