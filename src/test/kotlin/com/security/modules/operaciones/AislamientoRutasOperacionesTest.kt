package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.*
import com.security.modules.operaciones.application.usecase.ExportarOperacionPdfUseCase
import com.security.modules.operaciones.application.usecase.ObtenerDetalleOperacionUseCase
import com.security.modules.operaciones.controller.infrastructure.OperacionController
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.modules.operaciones.infrastructure.adapter.`in`.web.OperacionDetalleController
import com.security.modules.operaciones.infrastructure.adapter.`in`.web.OperacionPdfController
import com.security.shared.exceptions.GlobalExceptionHandler
import com.security.shared.exceptions.ResourceNotFoundException
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * Acceso cruzado: un registro de otra empresa responde 404 OPERACIONES_NO_ENCONTRADA en TODAS las rutas por id (ver,
 * editar, historial, responsables, cambiar responsable, cambiar dueño y PDF), y ningun servicio llega a tocarlo.
 */
class AislamientoRutasOperacionesTest {
    private val acceso = mockk<AccesoEmpresaService>()
    private val operacionService = mockk<OperacionService>()
    private val alta = mockk<AltaOperacionService>()
    private val lista = mockk<ListaOperacionesService>()
    private val responsables = mockk<ResponsableOperacionService>()
    private val detalle = mockk<ObtenerDetalleOperacionUseCase>()
    private val edicion = mockk<EdicionOperacionService>()
    private val historial = mockk<HistorialOperacionService>()
    private val operaciones = mockk<OperacionRepository>()
    private val pdf = mockk<ExportarOperacionPdfUseCase>()

    private val mvc = MockMvcBuilders.standaloneSetup(
        OperacionController(operacionService, acceso, alta, lista, responsables),
        OperacionDetalleController(detalle, edicion, acceso, historial, operaciones),
        OperacionPdfController(pdf, acceso)
    ).setControllerAdvice(GlobalExceptionHandler()).build()

    @Test
    fun `todas las rutas por id niegan el registro de otra empresa con el mismo 404`() {
        every { acceso.comprobarRegistro(42L) } throws ResourceNotFoundException("No existe un registro de operación con ID 42", CodigosOperaciones.NO_ENCONTRADA)
        val json = MediaType.APPLICATION_JSON
        val pedidos = listOf(
            get("/api/operaciones/42"),
            put("/api/operaciones/42").contentType(json).content("""{"motivo":"x"}"""),
            get("/api/operaciones/42/historial"),
            get("/api/operaciones/42/responsables"),
            put("/api/operaciones/42/responsable").contentType(json).content("""{"usuarioId":2}"""),
            patch("/api/operaciones/42/usuario").contentType(json).content("""{"usuarioId":2}"""),
            get("/api/operaciones/42/exportar-pdf").accept(MediaType.APPLICATION_PDF)
        )
        pedidos.forEach { pedido ->
            mvc.perform(pedido)
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value(CodigosOperaciones.NO_ENCONTRADA))
        }
        verify(exactly = 0) {
            detalle.ejecutar(any()); edicion.editar(any(), any(), any()); historial.historial(any(), any())
            responsables.posibles(any()); responsables.cambiar(any(), any(), any()); operacionService.cambiarUsuarioOperacion(any(), any())
            pdf.ejecutar(any(), any()); operaciones.findById(any())
        }
    }

    @Test
    fun `un error de datos devuelve el codigo y el mensaje de cada campo`() {
        every { acceso.registroDelActor(any()) } answers { firstArg() }
        every { acceso.actor() } returns AccesoEmpresaService.Actor(7, "ana", "Empresa Gamma", false)
        every { alta.registrar(any(), any()) } throws OperacionesException(
            org.springframework.http.HttpStatus.BAD_REQUEST, CodigosOperaciones.DATOS_INVALIDOS, "Escribe el monto.",
            mapOf("operacion.monto" to "Escribe el monto.")
        )
        mvc.perform(post("/api/operaciones/insertar-operacion").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(CodigosOperaciones.DATOS_INVALIDOS))
            .andExpect(jsonPath("$.message").value("Escribe el monto."))
            .andExpect(jsonPath("$['campos']['operacion.monto']").value("Escribe el monto."))
    }
}
