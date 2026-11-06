package com.security.modules.oficial

import com.security.modules.oficial.aplicacion.CifradoCodigosOficial
import com.security.modules.oficial.aplicacion.OficialException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CifradoCodigosOficialTest {
    // Clave de prueba (bytes 0..31), no es un secreto real.
    private val clavePrueba = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="

    @Test
    fun `cifra con AES-GCM, cada vez distinto, y solo descifra con el mismo contexto`() {
        val c = CifradoCodigosOficial(clavePrueba)
        assertTrue(c.disponible)
        val a = c.cifrar("CODIGO-FICTICIO", "uuid-1|codigoOficial")
        val b = c.cifrar("CODIGO-FICTICIO", "uuid-1|codigoOficial")
        assertTrue(a.startsWith("v1:"))
        assertFalse(a.contains("CODIGO-FICTICIO"))
        assertNotEquals(a, b)
        assertEquals("CODIGO-FICTICIO", c.descifrar(a, "uuid-1|codigoOficial"))
        assertFailsWith<Exception> { c.descifrar(a, "uuid-2|codigoOficial") }
    }

    @Test
    fun `sin clave o con una clave invalida no esta disponible y responde 409`() {
        for (clave in listOf("", "   ", "corta", "AAECAwQ=")) {
            val c = CifradoCodigosOficial(clave)
            assertFalse(c.disponible)
            val e = assertFailsWith<OficialException> { c.cifrar("X", "ctx") }
            assertEquals(409, e.status.value())
            assertEquals("OC_CIFRADO_NO_CONFIGURADO", e.code)
            assertFailsWith<OficialException> { c.exigirDisponible() }
        }
    }

    @Test
    fun `acepta la clave en hexadecimal de 64 caracteres`() {
        val hex = (0 until 32).joinToString("") { "%02x".format(it) }
        assertTrue(CifradoCodigosOficial(hex).disponible)
    }
}
