package com.security.modules.licencias

import com.security.modules.licencias.cliente.LicenciasCliente
import com.security.modules.licencias.cliente.LicenciasNoDisponibleException
import com.security.modules.licencias.cola.ColaLicencias
import com.security.modules.licencias.config.LicenciasProperties
import com.security.modules.licencias.dominio.ConsumoRemoto
import com.security.modules.licencias.dominio.CupoRemoto
import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.modules.licencias.dominio.TokensRemotos
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor
import kotlin.test.*

/** LicenciaService sin red ni base: el servicio común es un mock y la cola también. */
class LicenciaServiceTest {
    private val cliente = mockk<LicenciasCliente>()
    private val cola = mockk<ColaLicencias>(relaxed = true)
    private val usuarios = mockk<UsuarioRepository>()
    private val empresas = mockk<EmpresaRepository>()

    private val uuid = "11111111-2222-3333-4444-555555555555"

    private fun servicio(token: String = "token-de-prueba") =
        LicenciaService(LicenciasProperties(url = "https://licencias.test", token = token), cliente, cola, usuarios, empresas).also {
            it.reloj = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)
            it.ejecutor = Executor { tarea -> tarea.run() }
        }

    private fun LicenciaService.avanzar(segundos: Long) {
        reloj = Clock.offset(reloj, Duration.ofSeconds(segundos))
    }

    private fun caida(status: Int? = null) = LicenciasNoDisponibleException("RED", "sin respuesta", status)

    private fun activa(tokens: TokensRemotos? = null, puedeCrear: Boolean = true) =
        LicenciaRemota(estado = "ACTIVA", usuariosMax = 10, usuariosActivos = 4, puedeCrearUsuarios = puedeCrear, tokens = tokens)

    private fun consumir(s: LicenciaService, sulotec: Boolean = false) =
        s.consumir(uuid, "scoring", "plaft:$uuid:scoring:1", "ana", sulotec, "entidad 7")

    // ---------- Licencia y caché ----------

    @Test
    fun `dentro de los 5 minutos no vuelve a preguntar`() {
        val s = servicio()
        every { cliente.obtenerLicencia(uuid) } returns activa()

        assertEquals("ACTIVA", s.licenciaDe(uuid)?.estado)
        s.avanzar(299)
        assertEquals("ACTIVA", s.licenciaDe(uuid)?.estado)
        verify(exactly = 1) { cliente.obtenerLicencia(uuid) }

        s.avanzar(2)
        s.licenciaDe(uuid)
        verify(exactly = 2) { cliente.obtenerLicencia(uuid) }
    }

    @Test
    fun `si el servicio cae usa la ultima conocida y espera 30 segundos`() {
        val s = servicio()
        every { cliente.obtenerLicencia(uuid) } returns LicenciaRemota(estado = "SOLO_LECTURA")
        s.licenciaDe(uuid)
        s.avanzar(301)
        every { cliente.obtenerLicencia(uuid) } throws caida(503)

        assertEquals("SOLO_LECTURA", s.licenciaDe(uuid)?.estado)
        s.avanzar(10)
        assertEquals("SOLO_LECTURA", s.licenciaDe(uuid)?.estado)
        verify(exactly = 2) { cliente.obtenerLicencia(uuid) }

        s.avanzar(21)
        s.licenciaDe(uuid)
        verify(exactly = 3) { cliente.obtenerLicencia(uuid) }
        assertEquals("SOLO_LECTURA", s.estadoConocido(uuid))
    }

    @Test
    fun `si nunca se conocio y el servicio cae se trata como activa`() {
        val s = servicio()
        every { cliente.obtenerLicencia(uuid) } throws caida(401)
        assertNull(s.licenciaDe(uuid))
        assertTrue(LicenciaReglas.decidir("POST", "/api/scoring/evaluar", null, false, true, s.estadoConocido(uuid)).permitir)
    }

    @Test
    fun `sin licencia no bloquea ni cobra ni encola`() {
        val s = servicio()
        every { cliente.obtenerLicencia(uuid) } returns LicenciaRemota.SIN_LICENCIA
        every { cliente.consumir(any(), any(), any(), any(), any(), any(), any()) } returns
            ConsumoRemoto(permitido = false, motivo = "SIN_LICENCIA", sinLicencia = true)
        every { cliente.consultarCupo(uuid) } returns CupoRemoto(sinLicencia = true)

        assertEquals("SIN_LICENCIA", s.licenciaDe(uuid)?.estado)
        assertTrue(LicenciaReglas.decidir("POST", "/api/scoring/evaluar", null, false, true, "SIN_LICENCIA").permitir)
        consumir(s)
        s.verificarCupo(uuid, 50)
        verify(exactly = 0) { cola.encolarConsumo(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `apagado no toca la red ni la base`() {
        val s = servicio(token = "")
        assertNull(s.licenciaDe(uuid))
        consumir(s)
        s.verificarCupo(uuid, 3)
        s.anular("clave")
        s.reportarActivosLuego("Gamma")
        s.alinearActivosAlArrancar()
        verify { cliente wasNot Called }
        verify { cola wasNot Called }
        verify { usuarios wasNot Called }
        verify { empresas wasNot Called }
    }

    @Test
    fun `sin uuid de empresa no se consulta nada`() {
        val s = servicio()
        assertNull(s.licenciaDe(null))
        s.consumir(null, "scoring", "c", "ana", false, null)
        s.verificarCupo(" ", 1)
        verify { cliente wasNot Called }
    }

    // ---------- Consumo de tokens ----------

    @Test
    fun `consumo permitido devuelve normalmente`() {
        val s = servicio()
        every { cliente.consumir(uuid, "plaft:$uuid:scoring:1", "scoring", 1, "ana", false, "entidad 7") } returns
            ConsumoRemoto(permitido = true, nivel = "normal", disponibles = 90)
        consumir(s)
        verify(exactly = 1) { cliente.consumir(uuid, "plaft:$uuid:scoring:1", "scoring", 1, "ana", false, "entidad 7") }
        verify(exactly = 0) { cola.encolarConsumo(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `bolsa agotada responde 402`() {
        val s = servicio()
        every { cliente.consumir(any(), any(), any(), any(), any(), any(), any()) } returns
            ConsumoRemoto(permitido = false, motivo = "BOLSA_AGOTADA")
        val e = assertFailsWith<LicenciaBolsaAgotadaException> { consumir(s) }
        assertEquals("LICENCIA_BOLSA_AGOTADA", e.code)
        assertEquals(LicenciaReglas.ENLACE_MI_LICENCIA, e.enlace)
        assertEquals(LicenciaReglas.MENSAJE_BOLSA_AGOTADA, e.message)
    }

    @Test
    fun `consumo rechazado por estado responde 403 con su codigo`() {
        val s = servicio()
        val casos = mapOf(
            "SOLO_LECTURA" to "LICENCIA_SOLO_LECTURA",
            "SUSPENDIDA" to "LICENCIA_SUSPENDIDA",
            "SIN_LICENCIA" to "LICENCIA_CANCELADA"
        )
        casos.forEach { (motivo, code) ->
            every { cliente.consumir(any(), any(), any(), any(), any(), any(), any()) } returns
                ConsumoRemoto(permitido = false, motivo = motivo)
            val e = assertFailsWith<LicenciaBloqueadaException> { consumir(s) }
            assertEquals(code, e.code)
        }
    }

    @Test
    fun `al administrador de Sulotec nunca se le bloquea`() {
        val s = servicio()
        every { cliente.consumir(any(), any(), any(), any(), any(), any(), true) } returns
            ConsumoRemoto(permitido = false, motivo = "BOLSA_AGOTADA")
        consumir(s, sulotec = true)
    }

    @Test
    fun `si el servicio cae se permite y el consumo queda en la cola con la misma clave`() {
        val s = servicio()
        every { cliente.consumir(any(), any(), any(), any(), any(), any(), any()) } throws caida()
        consumir(s)
        verify(exactly = 1) {
            cola.encolarConsumo(uuid, "plaft:$uuid:scoring:1", "scoring", 1, "ana", false, "entidad 7", any())
        }
        // en la pausa tras el fallo ya no se espera al servicio: se encola directo
        consumir(s)
        verify(exactly = 1) { cliente.consumir(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 2) { cola.encolarConsumo(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `si el servicio cae y la ultima conocida estaba bloqueada se responde 403 o 402`() {
        val s = servicio()
        every { cliente.consumir(any(), any(), any(), any(), any(), any(), any()) } throws caida()

        every { cliente.obtenerLicencia(uuid) } returns LicenciaRemota(estado = "SUSPENDIDA")
        s.licenciaDe(uuid)
        assertEquals("LICENCIA_SUSPENDIDA", assertFailsWith<LicenciaBloqueadaException> { consumir(s) }.code)

        every { cliente.obtenerLicencia(uuid) } returns activa(tokens = TokensRemotos(nivel = "bloqueado", disponibles = 0))
        s.licenciaDe(uuid, forzar = true)
        assertFailsWith<LicenciaBolsaAgotadaException> { consumir(s) }
        verify(exactly = 0) { cola.encolarConsumo(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `anular encola la anulacion si el servicio no responde`() {
        val s = servicio()
        every { cola.quitarConsumoPendiente("clave-1") } returns false
        every { cliente.anular("clave-1") } throws caida()
        s.anular("clave-1")
        verify(exactly = 1) { cola.encolarAnulacion("clave-1", any()) }
    }

    @Test
    fun `anular un consumo que seguia en la cola solo lo quita`() {
        val s = servicio()
        every { cola.quitarConsumoPendiente("clave-2") } returns true
        s.anular("clave-2")
        verify(exactly = 0) { cliente.anular(any()) }
        verify(exactly = 0) { cola.encolarAnulacion(any(), any()) }
    }

    // ---------- Cupo de usuarios ----------

    @Test
    fun `sin cupo responde 409 con el enlace segun quien actua`() {
        val s = servicio()
        every { cliente.consultarCupo(uuid) } returns CupoRemoto(puedeCrear = false, activos = 10, usuariosMax = 10, estado = "ACTIVA")

        val cliente409 = assertFailsWith<LicenciaSinCupoException> { s.verificarCupo(uuid, actorEsSulotec = false) }
        assertEquals("LICENCIA_SIN_CUPO", cliente409.code)
        assertEquals(10L, cliente409.activos)
        assertEquals(10L, cliente409.usuariosMax)
        assertEquals(LicenciaReglas.ENLACE_MI_LICENCIA, cliente409.enlace)
        assertEquals(LicenciaReglas.textoCupo(10, 10), cliente409.message)

        val sulotec409 = assertFailsWith<LicenciaSinCupoException> { s.verificarCupo(uuid, actorEsSulotec = true) }
        assertEquals(LicenciaReglas.ENLACE_LICENCIAS, sulotec409.enlace)
    }

    @Test
    fun `con cupo pasa`() {
        val s = servicio()
        every { cliente.consultarCupo(uuid) } returns CupoRemoto(puedeCrear = true, activos = 4, usuariosMax = 10)
        s.verificarCupo(uuid)
        s.verificarCupo(uuid, cuentasNuevas = 6)
    }

    @Test
    fun `la carga masiva necesita lugar para todo el lote`() {
        val s = servicio()
        every { cliente.consultarCupo(uuid) } returns CupoRemoto(puedeCrear = true, activos = 8, usuariosMax = 10)
        val e = assertFailsWith<LicenciaSinCupoException> { s.verificarCupo(uuid, cuentasNuevas = 3) }
        assertEquals(
            "La carga crea 3 cuentas y tu plan solo tiene 2 libres (8 de 10 en uso). Reduce el archivo o amplía tu licencia en Sulotec.",
            e.message
        )
    }

    @Test
    fun `si el servicio cae el cupo se permite salvo que la ultima conocida diga que no`() {
        val s = servicio()
        every { cliente.consultarCupo(uuid) } throws caida()
        s.verificarCupo(uuid)

        val otro = servicio()
        every { cliente.obtenerLicencia(uuid) } returns activa(puedeCrear = false)
        otro.licenciaDe(uuid)
        assertFailsWith<LicenciaSinCupoException> { otro.verificarCupo(uuid) }
    }

    // ---------- Cuentas activas ----------

    @Test
    fun `reporta las cuentas activas de la empresa`() {
        val s = servicio()
        every { empresas.findByNombreIgnoreCase("Gamma") } returns Empresa(id = 1, nombre = "Gamma S.A.C.", uuid = uuid)
        every { usuarios.contarActivosPorEmpresa("Gamma S.A.C.") } returns 4
        every { cliente.reportarActivos(uuid, 4) } returns CupoRemoto(puedeCrear = true, activos = 4, usuariosMax = 10)

        s.reportarActivosLuego(" Gamma ", "gamma", null)

        // la misma empresa escrita de dos formas se reporta una sola vez
        verify(exactly = 1) { cliente.reportarActivos(uuid, 4) }
    }

    @Test
    fun `reportar nunca lanza aunque el servicio caiga`() {
        val s = servicio()
        every { empresas.findByNombreIgnoreCase("Gamma") } returns Empresa(id = 1, nombre = "Gamma", uuid = uuid)
        every { usuarios.contarActivosPorEmpresa("Gamma") } returns 4
        every { cliente.reportarActivos(uuid, 4) } throws caida()
        assertNull(s.contarYReportarActivos("Gamma"))
    }

    @Test
    fun `al arrancar reporta solo las empresas activas con uuid`() {
        val s = servicio()
        every { empresas.findAll() } returns listOf(
            Empresa(id = 1, nombre = "Gamma", uuid = uuid),
            Empresa(id = 2, nombre = "Delta", uuid = null),
            Empresa(id = 3, nombre = "Omega", uuid = "otro-uuid", activa = false)
        )
        every { empresas.findByNombreIgnoreCase("Gamma") } returns Empresa(id = 1, nombre = "Gamma", uuid = uuid)
        every { usuarios.contarActivosPorEmpresa("Gamma") } returns 2
        every { cliente.reportarActivos(uuid, 2) } returns CupoRemoto(puedeCrear = true, activos = 2, usuariosMax = 10)

        s.alinearActivosEnSegundoPlano()

        verify(exactly = 1) { cliente.reportarActivos(uuid, 2) }
        verify(exactly = 1) { cliente.reportarActivos(any(), any()) }
    }
}
