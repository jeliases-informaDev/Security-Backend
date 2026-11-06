package com.security.modules.operaciones

import com.security.modules.operaciones.FixturesOperaciones.alta
import com.security.modules.operaciones.FixturesOperaciones.beneficiarioDto
import com.security.modules.operaciones.FixturesOperaciones.catalogos
import com.security.modules.operaciones.FixturesOperaciones.operacionDto
import com.security.modules.operaciones.FixturesOperaciones.personaDto
import com.security.modules.operaciones.FixturesOperaciones.registro
import com.security.modules.operaciones.FixturesOperaciones.registroViejo
import com.security.modules.operaciones.FixturesOperaciones.usuario
import com.security.modules.operaciones.application.service.*
import com.security.modules.operaciones.domain.CambioCampo
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.dto.ActualizarOperacionRequest
import com.security.modules.operaciones.dto.RegistroEditableDto
import com.security.modules.operaciones.infraestructure.NumeradorRegistros
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.modules.operaciones.infraestructure.UsuarioProjection
import com.security.shared.datos.repositories.UsuarioRepository
import com.tuempresa.operaciones.dto.BeneficiarioDto
import com.tuempresa.operaciones.dto.OperacionDto
import com.tuempresa.operaciones.dto.PersonaDto
import io.mockk.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.Optional
import kotlin.test.*

class AltaEdicionOperacionTest {
    private val operaciones = mockk<OperacionRepository>()
    private val usuarios = mockk<UsuarioRepository>()
    private val catalogosRegistro = mockk<CatalogosRegistro> { every { cargar() } answers { catalogos() } }
    private val numerador = mockk<NumeradorRegistros>()
    private val historial = mockk<HistorialOperacionService>(relaxed = true)
    private val autor = AutorEvento(7, "ana", "Nombre7 Prueba")

    private val alta = AltaOperacionService(operaciones, usuarios, catalogosRegistro, numerador, historial)

    private fun cuenta(empresa: String? = "Empresa Gamma") {
        val p = mockk<UsuarioProjection>()
        every { p.id } returns 7
        every { p.usuario } returns "ana"
        every { p.empresa } returns empresa
        every { operaciones.buscarPorUsuario("ana") } returns p
        every { usuarios.findById(7) } returns Optional.of(usuario(7, "ana", empresa))
    }

    @Test
    fun `el alta pone numero, fecha, empresa de la cuenta y responsable, y anota la creacion`() {
        cuenta()
        every { numerador.siguiente("Empresa Gamma", any()) } returns "RO-2026-000011"
        val guardado = slot<RegistroOperacionesEntity>()
        every { operaciones.saveAndFlush(capture(guardado)) } answers {
            spyk(firstArg<RegistroOperacionesEntity>()).also { every { it.id } returns 11L }
        }

        val r = alta.registrar(alta(), autor)

        assertEquals(11L, r.id)
        assertEquals("RO-2026-000011", r.numeroRegistro)
        assertEquals("PENDIENTE", r.estado)
        assertEquals(7, r.responsable!!.id)
        assertEquals("Nombre7 Prueba", r.responsable!!.nombreCompleto)
        val e = guardado.captured
        assertEquals("RO-2026-000011", e.numeroRegistro)
        // la empresa es la de la cuenta, no el texto que mando el cliente
        assertEquals("Empresa Gamma", e.empresa)
        assertEquals(7, e.idUsuarioResponsable)
        assertEquals("AHORROS", e.operacion.origenFondos)
        assertEquals("F001-000001", e.operacion.numeroComprobante)
        assertEquals("SOLTERO", e.personaFisica.estadoCivil)
        assertNull(e.beneficiarios.single().fechaNacimiento)
        assertEquals(r.fechaRegistro, e.fechaRegistro)
        verify { historial.creacion(11L, autor, any()) }
    }

    @Test
    fun `datos incompletos no gastan numero ni guardan`() {
        cuenta()
        val pedido = alta().copy(beneficiarios = emptyList(), operacion = operacionDto().copy(origenFondos = null))
        val e = assertFailsWith<OperacionesException> { alta.registrar(pedido, autor) }
        assertEquals(setOf("beneficiarios", "operacion.origenFondos"), e.campos!!.keys)
        verify(exactly = 0) { numerador.siguiente(any(), any()); operaciones.saveAndFlush(any<RegistroOperacionesEntity>()) }
    }

    @Test
    fun `un id que no corresponde a la cuenta se rechaza antes de guardar`() {
        cuenta()
        assertFailsWith<ResponseStatusException> { alta.registrar(alta(usuarioId = "8"), autor) }
        verify(exactly = 0) { operaciones.saveAndFlush(any<RegistroOperacionesEntity>()) }
    }

    @Test
    fun `si falla el guardado no se anota nada en el historial`() {
        cuenta()
        every { numerador.siguiente(any(), any()) } returns "RO-2026-000012"
        every { operaciones.saveAndFlush(any<RegistroOperacionesEntity>()) } throws
            org.springframework.dao.DataIntegrityViolationException("duplicado")
        assertFailsWith<org.springframework.dao.DataIntegrityViolationException> { alta.registrar(alta(), autor) }
        verify(exactly = 0) { historial.creacion(any(), any(), any()) }
    }

    // ---------- Edicion ----------

    private val em = mockk<EntityManager>(relaxed = true)
    private val edicion = EdicionOperacionService(operaciones, catalogosRegistro, historial, em)

    private fun pedidoEdicion(motivo: String? = "Corrección según el contrato firmado.", monto: String = "128500.00") = ActualizarOperacionRequest(
        registro = RegistroEditableDto("Lima"),
        personaFisica = personaDto(),
        operacion = operacionDto().copy(monto = BigDecimal(monto)),
        beneficiarios = listOf(beneficiarioDto()),
        motivo = motivo
    )

    @Test
    fun `editar exige motivo de hasta 300 caracteres`() {
        listOf(null, "  ", "x".repeat(301)).forEach { motivo ->
            val e = assertFailsWith<OperacionesException> { edicion.editar(42, pedidoEdicion(motivo), autor) }
            assertEquals(CodigosOperaciones.MOTIVO_INVALIDO, e.code)
            assertTrue("motivo" in e.campos!!)
        }
        verify(exactly = 0) { operaciones.findById(any()) }
    }

    @Test
    fun `editar guarda los cambios campo por campo con el motivo, sin tocar numero, empresa ni responsable`() {
        val r = registro()
        every { operaciones.findById(42) } returns Optional.of(r)
        every { operaciones.flush() } just Runs
        val cambios = slot<List<CambioCampo>>()
        every { historial.edicion(42, autor, any(), "Corrección según el contrato firmado.", capture(cambios)) } returns mockk()

        val respuesta = edicion.editar(42, pedidoEdicion(), autor)

        assertEquals(listOf("operacion.monto"), respuesta.cambios.map { it.campo })
        assertEquals("125,800.00", respuesta.cambios.single().antes)
        assertEquals("128,500.00", respuesta.cambios.single().despues)
        assertEquals(BigDecimal("128500.00"), r.operacion.monto)
        assertEquals("RO-2026-000001", r.numeroRegistro)
        assertEquals("Empresa Gamma", r.empresa)
        assertEquals(7, r.idUsuarioResponsable)
        assertEquals("Monto", cambios.captured.single().etiqueta)
    }

    @Test
    fun `editar sin cambiar nada responde sin cambios y no anota nada`() {
        every { operaciones.findById(42) } returns Optional.of(registro())
        val e = assertFailsWith<OperacionesException> { edicion.editar(42, pedidoEdicion(monto = "125800.00"), autor) }
        assertEquals(CodigosOperaciones.SIN_CAMBIOS, e.code)
        verify(exactly = 0) { historial.edicion(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `un registro anterior se corrige sin rehacer lo que no se toca`() {
        val viejo = registroViejo()
        every { operaciones.findById(43) } returns Optional.of(viejo)
        every { operaciones.flush() } just Runs
        every { historial.edicion(any(), any(), any(), any(), any()) } returns mockk()
        val pedido = ActualizarOperacionRequest(
            registro = RegistroEditableDto("Sede"),
            personaFisica = PersonaDto(tipoDocumento = "DNI", numeroDocumento = "00000002", nombres = "Luis", apellidos = "Demo Rojas",
                fechaNacimiento = viejo.personaFisica.fechaNacimiento, telefono = "900000005"),
            operacion = OperacionDto(fechaOperacion = viejo.operacion.fechaOperacion, modalidadPago = "Efectivo", moneda = "PEN — Soles",
                monto = BigDecimal("1250.25"), tipoOperacion = "Deposito"),
            beneficiarios = listOf(BeneficiarioDto("Persona Ficticia", "DNI", "00000003", viejo.beneficiarios.single().fechaNacimiento)),
            motivo = "Faltaba el teléfono"
        )
        val r = edicion.editar(43, pedido, autor)
        assertEquals(listOf("personaFisica.telefono"), r.cambios.map { it.campo })
        assertEquals("OP-20261012-0001", r.numeroRegistro)
        assertEquals("Deposito", viejo.operacion.tipoOperacion)
    }

    @Test
    fun `editar un registro inexistente responde no encontrado con su codigo`() {
        every { operaciones.findById(999) } returns Optional.empty()
        val e = assertFailsWith<com.security.shared.exceptions.ResourceNotFoundException> { edicion.editar(999, pedidoEdicion(), autor) }
        assertEquals(CodigosOperaciones.NO_ENCONTRADA, e.code)
    }
}
