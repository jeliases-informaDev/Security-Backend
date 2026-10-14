package com.security.modules.operaciones

import com.security.modules.operaciones.domain.*
import com.security.modules.operaciones.dto.*
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.modules.operaciones.infrastructure.adapter.out.persistence.OperacionActualizacionAdapter
import com.security.modules.operaciones.application.usecase.ActualizarOperacionUseCase
import com.security.shared.exceptions.ResourceNotFoundException
import com.tuempresa.operaciones.dto.*
import jakarta.persistence.EntityManager
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.math.BigDecimal
import java.util.Optional
import kotlin.test.*

class OperacionActualizacionTest {
    private val repository = mockk<OperacionRepository>()
    private val em = mockk<EntityManager>(relaxed = true)
    private val useCase = ActualizarOperacionUseCase(OperacionActualizacionAdapter(repository, em))
    private val fecha = LocalDate.of(2026, 10, 13)
    private val persona = PersonaDto("DNI", "00123456", "Ana", "Torres", fecha.minusYears(30),
        null, null, "Abogada", null, null, null, null, null)
    private fun request() = ActualizarOperacionRequest(RegistroEditableDto("Nueva oficina", fecha), persona, null,
        OperacionDto(fecha, "Lima", "Transferencia", "PEN", BigDecimal("15.25"), "Deposito", "001", null, null),
        listOf(BeneficiarioDto("Nuevo", "DNI", "00987654", fecha.minusYears(20))))

    @Test
    fun `actualiza campos editables conservando identidad y reemplaza beneficiarios`() {
        val anterior = Persona().apply { id = 30 }
        val operacion = Operacion(9, fecha, null, "Efectivo", "USD", BigDecimal.ONE, "Otro", null, null, null)
        val registro = RegistroOperacionesEntity(42, "OP-ORIGINAL", 1, "Empresa original", "Anterior", fecha.minusDays(1),
            personaFisica = Persona().apply { id = 20 }, personaRepresentada = anterior, operacion = operacion,
            beneficiarios = listOf(BeneficiarioEntity(nombre = "Anterior", tipoDocumento = "DNI",
                numeroDocumento = "111", fechaNacimiento = fecha.minusYears(40))))
        val creacion = registro.fechaCreacion
        every { repository.findById(42) } returns Optional.of(registro)
        every { repository.flush() } just Runs
        val resultado = useCase.ejecutar(42, request())
        assertEquals(42L, resultado.id)
        assertEquals("OP-ORIGINAL", registro.numeroRegistro)
        assertEquals(1, registro.idUsuario)
        assertEquals("Empresa original", registro.empresa)
        assertEquals(creacion, registro.fechaCreacion)
        assertEquals("Nueva oficina", registro.oficina)
        assertEquals(20L, registro.personaFisica.id)
        assertEquals("Ana", registro.personaFisica.nombres)
        assertEquals(9L, registro.operacion.id)
        assertEquals(BigDecimal("15.25"), registro.operacion.monto)
        assertEquals("Nuevo", registro.beneficiarios.single().nombre)
        assertNull(registro.personaRepresentada)
        verify { em.remove(anterior) }
    }

    @Test
    fun `rechaza inexistentes IDs invalidos y montos invalidos`() {
        every { repository.findById(999) } returns Optional.empty()
        assertFailsWith<ResourceNotFoundException> { useCase.ejecutar(999, request()) }
        assertFailsWith<ResponseStatusException> { useCase.ejecutar(0, request()) }
        assertFailsWith<ResponseStatusException> {
            useCase.ejecutar(42, request().copy(operacion = request().operacion.copy(monto = BigDecimal("1.001"))))
        }
        verify(exactly = 0) { repository.flush() }
    }
}
