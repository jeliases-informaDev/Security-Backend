package com.security.modules.auth.sso

import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Date
import kotlin.test.*

/** Verificación del id_token (RS256, emisor, audiencia y vencimiento) con llaves generadas en la prueba: sin red. */
class SsoProveedorKeycloakTest {
    private val emisor = "https://cuenta.ejemplo.com/realms/sulotec"
    private val props = SsoProperties(issuer = "$emisor/", clientId = "plaft", redirectUri = "https://plaft.ejemplo.com/sso")
    private val par: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val proveedor = SsoProveedorKeycloak(props).also { it.usarLlaves(mapOf("k1" to par.public)) }

    private fun token(
        audiencia: String = "plaft",
        iss: String = emisor,
        venceEnSegundos: Long = 300,
        llave: KeyPair = par,
        kid: String = "k1"
    ): String {
        val ahora = System.currentTimeMillis()
        return Jwts.builder()
            .header().keyId(kid).and()
            .issuer(iss)
            .audience().add(audiencia).and()
            .subject("id-sulotec-1")
            .issuedAt(Date(ahora - 1000))
            .expiration(Date(ahora + venceEnSegundos * 1000))
            .claim("email", "ana@ejemplo.com")
            .claim("email_verified", true)
            .claim("given_name", "Ana")
            .claim("family_name", "Torres")
            .claim("roles", listOf("producto-plaft", "offline_access"))
            .claim("empresa_uuid", "11111111-2222-3333-4444-555555555555")
            .claim("empresa_nombre", "Acme SAC")
            .signWith(llave.private, Jwts.SIG.RS256)
            .compact()
    }

    @Test
    fun `un id_token valido da la identidad`() {
        val identidad = proveedor.verificar(token())
        assertEquals("ana@ejemplo.com", identidad.correo)
        assertEquals(true, identidad.correoVerificado)
        assertEquals("Ana", identidad.nombres)
        assertEquals("Torres", identidad.apellidos)
        assertTrue("producto-plaft" in identidad.roles)
        assertEquals("11111111-2222-3333-4444-555555555555", identidad.empresaUuid)
        assertEquals("Acme SAC", identidad.empresaNombre)
    }

    @Test
    fun `otra audiencia otro emisor o vencido es SSO_TOKEN`() {
        for (malo in listOf(token(audiencia = "geomatrix"), token(iss = "https://otro.ejemplo.com/realms/x"), token(venceEnSegundos = -600))) {
            val error = assertFailsWith<SsoException> { proveedor.verificar(malo) }
            assertEquals(SsoReglas.TOKEN, error.code)
            assertEquals(401, error.status.value())
        }
    }

    @Test
    fun `firmado con otra llave o con una llave desconocida es SSO_TOKEN`() {
        val otra = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertEquals(SsoReglas.TOKEN, assertFailsWith<SsoException> { proveedor.verificar(token(llave = otra)) }.code)
        // Llave desconocida: como las llaves se acaban de leer, no se vuelve a preguntar (no sale a la red).
        assertEquals(SsoReglas.TOKEN, assertFailsWith<SsoException> { proveedor.verificar(token(kid = "k2")) }.code)
        assertEquals(SsoReglas.TOKEN, assertFailsWith<SsoException> { proveedor.verificar("no-es-un-token") }.code)
    }
}
