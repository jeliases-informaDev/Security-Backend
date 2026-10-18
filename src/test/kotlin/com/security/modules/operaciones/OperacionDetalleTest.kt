package com.security.modules.operaciones

import com.security.modules.operaciones.application.usecase.ObtenerDetalleOperacionUseCase
import com.security.modules.operaciones.domain.model.*
import com.security.modules.operaciones.domain.port.out.OperacionConsultaPort
import com.security.modules.operaciones.infrastructure.adapter.`in`.web.OperacionDetalleController
import com.security.shared.exceptions.GlobalExceptionHandler
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate
import java.math.BigDecimal

class OperacionDetalleTest {
    private val consulta = mockk<OperacionConsultaPort>()
    private val mvc = MockMvcBuilders.standaloneSetup(
        OperacionDetalleController(ObtenerDetalleOperacionUseCase(consulta), mockk(), mockk(relaxed = true))
    ).setControllerAdvice(GlobalExceptionHandler()).build()

    @Test
    fun `devuelve datos de formulario con fechas ISO y persona opcional`() {
        val fecha = LocalDate.of(2026, 10, 13)
        every { consulta.obtenerParaExportacion(42) } returns OperacionExportacion(
            42, "OP-42", RegistroExportacion("1", "demo", "Empresa", "Lima", fecha),
            PersonaExportacion("DNI", "00123456", "Ana", "Torres", fecha.minusYears(30),
                "Peruana", "999999999", "Abogada", "Av. Lima", "15001", "Lima", "Lima", "Lima"),
            null,
            DatosOperacionExportacion(fecha, "Lima", "Transferencia", "PEN", BigDecimal("123.45"),
                "Deposito", "000123", null, null),
            listOf(BeneficiarioExportacion("Luis", "DNI", "00987654", fecha.minusYears(25)))
        )
        mvc.perform(get("/api/operaciones/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.numeroRegistro").value("OP-42"))
            .andExpect(jsonPath("$.registro.usuarioId").value("1"))
            .andExpect(jsonPath("$.registro.fechaRegistro").value("2026-10-13"))
            .andExpect(jsonPath("$.personaFisica.numeroDocumento").value("00123456"))
            .andExpect(jsonPath("$.personaFisica.ocupacion").value("Abogada"))
            .andExpect(jsonPath("$.personaRepresentada").isEmpty)
            .andExpect(jsonPath("$.operacion.cuenta1").value("000123"))
            .andExpect(jsonPath("$.operacion.monto").value(123.45))
            .andExpect(jsonPath("$.beneficiarios[0].nombre").value("Luis"))
    }

    @Test
    fun `devuelve 404 cuando no existe y 400 para ID invalido`() {
        every { consulta.obtenerParaExportacion(999) } returns null
        mvc.perform(get("/api/operaciones/999")).andExpect(status().isNotFound)
        mvc.perform(get("/api/operaciones/0")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/operaciones/abc")).andExpect(status().isBadRequest)
        verify(exactly = 0) { consulta.obtenerParaExportacion(0) }
    }
}
