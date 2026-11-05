package com.security.modules.licencias

import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.modules.licencias.dominio.TokensRemotos
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/** Reglas puras del guard de escrituras (P5), los textos (P7), la licencia pública (C1) y las claves (P4). */
class LicenciaReglasTest {

    private fun decidir(metodo: String, ruta: String, estado: String?, activo: String? = null, sulotec: Boolean = false, autenticado: Boolean = true) =
        LicenciaReglas.decidir(metodo, ruta, activo, sulotec, autenticado, estado)

    @Test
    fun `las lecturas nunca se bloquean`() {
        listOf("GET", "HEAD", "OPTIONS").forEach {
            assertTrue(decidir(it, "/api/scoring/evaluar", "CANCELADA").permitir)
        }
    }

    @Test
    fun `sin autenticar o el administrador de Sulotec nunca se bloquean`() {
        assertTrue(decidir("POST", "/api/scoring/evaluar", "CANCELADA", autenticado = false).permitir)
        assertTrue(decidir("POST", "/api/scoring/evaluar", "CANCELADA", sulotec = true).permitir)
    }

    @Test
    fun `rutas exentas pasan con cualquier estado`() {
        val exentas = listOf(
            "POST" to "/api/auth/login",
            "PUT" to "/api/auth/change-password",
            "POST" to "/api/public/algo",
            "POST" to "/api/licencia",
            "POST" to "/api/empresas",
            "PATCH" to "/api/empresas/3/estado",
            "POST" to "/api/denuncias",
            "POST" to "/api/denuncias/seguimiento",
            "POST" to "/api/denuncias/ABC123/archivos",
            "POST" to "/api/matrices/calcular-inherente",
            "POST" to "/api/matrices/calcular-residual/"
        )
        exentas.forEach { (metodo, ruta) ->
            assertTrue(decidir(metodo, ruta, "SUSPENDIDA").permitir, "$metodo $ruta debería pasar")
        }
    }

    @Test
    fun `cambiar el estado de una denuncia si se bloquea`() {
        val d = decidir("PATCH", "/api/denuncias/7/estado", "SOLO_LECTURA")
        assertFalse(d.permitir)
        assertEquals("LICENCIA_SOLO_LECTURA", d.code)
    }

    @Test
    fun `solo lectura bloquea el trabajo pero deja desactivar e invitar`() {
        val bloqueo = decidir("POST", "/api/scoring/evaluar", "SOLO_LECTURA")
        assertFalse(bloqueo.permitir)
        assertEquals("LICENCIA_SOLO_LECTURA", bloqueo.code)
        assertEquals("SOLO_LECTURA", bloqueo.estado)
        assertEquals(LicenciaReglas.MENSAJE_SOLO_LECTURA, bloqueo.mensaje)

        assertTrue(decidir("PATCH", "/api/usuarios/5/estado", "SOLO_LECTURA", activo = "false").permitir)
        assertTrue(decidir("POST", "/api/usuarios/5/invitar", "SOLO_LECTURA").permitir)
        assertFalse(decidir("PATCH", "/api/usuarios/5/estado", "SOLO_LECTURA", activo = "true").permitir)
        assertFalse(decidir("POST", "/api/usuarios", "SOLO_LECTURA").permitir)
        assertFalse(decidir("POST", "/api/usuarios/carga-masiva", "SOLO_LECTURA").permitir)
    }

    @Test
    fun `suspendida y cancelada solo dejan desactivar`() {
        listOf("SUSPENDIDA" to "LICENCIA_SUSPENDIDA", "CANCELADA" to "LICENCIA_CANCELADA").forEach { (estado, code) ->
            assertTrue(decidir("PATCH", "/api/usuarios/5/estado", estado, activo = "false").permitir)
            val invitar = decidir("POST", "/api/usuarios/5/invitar", estado)
            assertFalse(invitar.permitir)
            assertEquals(code, invitar.code)
            assertFalse(decidir("PUT", "/api/operaciones/9", estado).permitir)
        }
        assertEquals(LicenciaReglas.MENSAJE_SUSPENDIDA, decidir("POST", "/api/ml/indira/chat", "SUSPENDIDA").mensaje)
        assertEquals(LicenciaReglas.MENSAJE_CANCELADA, decidir("POST", "/api/ml/indira/chat", "CANCELADA").mensaje)
    }

    @Test
    fun `activa, sin licencia, desconocida o apagada dejan pasar todo`() {
        listOf("ACTIVA", "SIN_LICENCIA", "OTRO_ESTADO", null).forEach {
            assertTrue(decidir("POST", "/api/scoring/evaluar", it).permitir)
        }
    }

    @Test
    fun `motivos de un consumo rechazado`() {
        assertTrue(LicenciaReglas.esBolsaAgotada("BOLSA_AGOTADA"))
        assertEquals("SOLO_LECTURA", LicenciaReglas.estadoDeMotivo("SOLO_LECTURA"))
        assertEquals("SUSPENDIDA", LicenciaReglas.estadoDeMotivo("SUSPENDIDA"))
        // SIN_LICENCIA en un consumo rechazado es una licencia cancelada
        assertEquals("CANCELADA", LicenciaReglas.estadoDeMotivo("SIN_LICENCIA"))
        assertNull(LicenciaReglas.estadoDeMotivo("BOLSA_AGOTADA"))
    }

    @Test
    fun `textos de cupo`() {
        assertEquals(
            "Tu plan llegó al máximo de usuarios activos (10 de 10). Desactiva un usuario o amplía tu licencia en Sulotec.",
            LicenciaReglas.textoCupo(10, 10)
        )
        assertEquals(
            "La carga crea 5 cuentas y tu plan solo tiene 2 libres (8 de 10 en uso). Reduce el archivo o amplía tu licencia en Sulotec.",
            LicenciaReglas.textoCupoLote(5, 2, 8, 10)
        )
        assertEquals(LicenciaReglas.ENLACE_LICENCIAS, LicenciaReglas.enlaceCupo(true))
        assertEquals(LicenciaReglas.ENLACE_MI_LICENCIA, LicenciaReglas.enlaceCupo(false))
    }

    @Test
    fun `licencia publica`() {
        assertNull(LicenciaReglas.publica(null))

        val sin = assertNotNull(LicenciaReglas.publica(LicenciaRemota.SIN_LICENCIA))
        assertEquals("SIN_LICENCIA", sin.estado)
        assertEquals(LicenciaReglas.MENSAJE_SIN_LICENCIA, sin.mensaje)
        assertEquals(LicenciaReglas.ENLACE_MI_LICENCIA, sin.enlace)
        assertNull(sin.tokens)

        val activa = assertNotNull(
            LicenciaReglas.publica(
                LicenciaRemota(
                    estado = "ACTIVA", plan = "Base", usuariosMax = 10, usuariosActivos = 4, puedeCrearUsuarios = true,
                    periodo = "2026-11", vencimiento = "2026-12-31T00:00:00Z", diasParaVencer = 56,
                    tokens = TokensRemotos(bolsa = 100, capacidad = 110, usados = 110, disponibles = 0, nivel = "bloqueado")
                )
            )
        )
        assertEquals("2026-12-31", activa.vencimiento)
        assertEquals(10L, activa.usuariosMax)
        // activa pero sin tokens: se avisa igual
        assertEquals(LicenciaReglas.MENSAJE_BOLSA_AGOTADA, activa.mensaje)

        val vencida = assertNotNull(LicenciaReglas.publica(LicenciaRemota(estado = "SOLO_LECTURA")))
        assertEquals(LicenciaReglas.MENSAJE_SOLO_LECTURA, vencida.mensaje)
    }

    @Test
    fun `la clave de una busqueda se repite en la misma ventana de dos minutos`() {
        val t0 = Instant.parse("2026-11-05T12:00:10Z")
        val a = LicenciaReglas.claveBusqueda("u-1", "ana", listOf(" Perez ", null, "JUAN  carlos"), t0)
        val b = LicenciaReglas.claveBusqueda("u-1", "ana", listOf("perez", "", "juan carlos"), t0.plusSeconds(60))
        val otraVentana = LicenciaReglas.claveBusqueda("u-1", "ana", listOf("perez", "", "juan carlos"), t0.plusSeconds(240))
        val otraPersona = LicenciaReglas.claveBusqueda("u-1", "luis", listOf("perez", "", "juan carlos"), t0)

        assertEquals(a, b)
        assertNotEquals(a, otraVentana)
        assertNotEquals(a, otraPersona)
        assertTrue(Regex("^plaft:u-1:busqueda_listas:[0-9a-f]{32}:\\d+$").matches(a), a)

        val c1 = LicenciaReglas.claveAleatoria("u-1", "scoring")
        assertTrue(c1.startsWith("plaft:u-1:scoring:"))
        assertNotEquals(c1, LicenciaReglas.claveAleatoria("u-1", "scoring"))
    }
}
