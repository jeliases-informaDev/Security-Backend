package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.OperacionService
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.modules.operaciones.infraestructure.UsuarioProjection
import com.tuempresa.operaciones.dto.*
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.*

class OperacionServiceTest {
    private val repository = mockk<OperacionRepository>()
    private val service = OperacionService(repository, mockk())
    private val date = LocalDate.of(2026, 10, 12)

    private fun request() = RegistrarOperacionRequest(
        RegistroDto("7", "demo", "Empresa", "Lima", date),
        PersonaDto("DNI", "12345678", "Ana", "Torres", date.minusYears(30),
            null, null, "Analista", null, null, null, null, null),
        null,
        OperacionDto(date, null, "Transferencia", "PEN", BigDecimal("1250.25"),
            "Deposito", "001", null, null),
        listOf(BeneficiarioDto("Luis", "DNI", "87654321", date.minusYears(25))),
        numeroRegistro = "  REG-2026-001  "
    )

    private fun usuario() {
        val user = mockk<UsuarioProjection>()
        every { user.id } returns 7
        every { user.empresa } returns "Empresa de la cuenta"
        every { repository.buscarPorUsuario("demo") } returns user
    }

    @Test
    fun `guarda el registro completo y conserva decimales y persona opcional`() {
        usuario()
        val saved = slot<RegistroOperacionesEntity>()
        every { repository.saveAndFlush(capture(saved)) } answers {
            spyk(firstArg<RegistroOperacionesEntity>()).also { entity ->
                every { entity.id } returns 42L
            }
        }
        val request = request()
        val response = service.registrar(request)
        assertEquals(42L, response.id)
        assertEquals(saved.captured.numeroRegistro, response.numeroRegistro)
        assertEquals(BigDecimal("1250.25"), saved.captured.operacion.monto)
        assertEquals("Analista", saved.captured.personaFisica.profesionOcupacion)
        assertNull(saved.captured.personaRepresentada)
        assertEquals("Luis", saved.captured.beneficiarios.single().nombre)
        assertEquals(7, saved.captured.idUsuario)
        // la empresa guardada es la de la cuenta, no el texto del pedido
        assertEquals("Empresa de la cuenta", saved.captured.empresa)
        assertEquals("REG-2026-001", saved.captured.numeroRegistro)
    }

    @Test
    fun `no devuelve exito si falla la persistencia`() {
        usuario()
        every { repository.saveAndFlush(any<RegistroOperacionesEntity>()) } throws
            org.springframework.dao.DataIntegrityViolationException("FK invalida")
        assertFailsWith<org.springframework.dao.DataIntegrityViolationException> {
            service.registrar(request())
        }
    }

    @Test
    fun `rechaza un usuario inconsistente antes de persistir`() {
        usuario()
        val request = request()
        assertFailsWith<ResponseStatusException> {
            service.registrar(request.copy(registro = request.registro.copy(usuarioId = "8")))
        }
        verify(exactly = 0) { repository.saveAndFlush(any<RegistroOperacionesEntity>()) }
    }

    @Test
    fun `rechaza decimales que se perderian en la base de datos`() {
        usuario()
        val request = request()
        assertFailsWith<ResponseStatusException> {
            service.registrar(request.copy(operacion = request.operacion.copy(monto = BigDecimal("1.001"))))
        }
        verify(exactly = 0) { repository.saveAndFlush(any<RegistroOperacionesEntity>()) }
    }
}
