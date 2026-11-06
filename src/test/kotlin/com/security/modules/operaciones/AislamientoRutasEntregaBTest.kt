package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.*
import com.security.modules.operaciones.controller.infrastructure.AnalisisOperacionController
import com.security.modules.operaciones.controller.infrastructure.ConfiguracionOperacionesController
import com.security.modules.operaciones.controller.infrastructure.ExportacionOperacionesController
import com.security.shared.exceptions.GlobalExceptionHandler
import com.security.shared.exceptions.ResourceNotFoundException
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * Entrega B: el registro de otra empresa responde 404 en todas las rutas de inusualidad y ROS; lo que es solo del
 * administrador responde 403 a una cuenta comun; y la configuracion, el tipo de cambio, la verificacion y la exportacion
 * usan la empresa que decide el acceso (la del token), no la que pide la cuenta.
 */
class AislamientoRutasEntregaBTest {
    private val acceso = mockk<AccesoEmpresaService>()
    private val analisis = mockk<AnalisisOperacionService>()
    private val configuracion = mockk<ConfiguracionOperacionesService>()
    private val tiposCambio = mockk<TipoCambioService>()
    private val umbrales = mockk<UmbralService>()
    private val exportaciones = mockk<ExportacionOperacionesService>()

    private val mvc = MockMvcBuilders.standaloneSetup(
        AnalisisOperacionController(acceso, analisis),
        ConfiguracionOperacionesController(acceso, configuracion, tiposCambio, umbrales),
        ExportacionOperacionesController(acceso, exportaciones)
    ).setControllerAdvice(GlobalExceptionHandler()).build()

    private val json = MediaType.APPLICATION_JSON
    private val comun = AccesoEmpresaService.Actor(7, "ana", "Empresa Gamma", false)

    @Test
    fun `inusualidad, adjuntos, decision y ROS de otra empresa responden 404 sin tocar nada`() {
        every { acceso.comprobarRegistro(42L) } throws ResourceNotFoundException("No existe", CodigosOperaciones.NO_ENCONTRADA)
        val archivo = MockMultipartFile("archivo", "prueba.pdf", "application/pdf", byteArrayOf(1))
        listOf(
            get("/api/operaciones/42/inusualidad"),
            put("/api/operaciones/42/inusualidad").contentType(json).content("{}"),
            multipart("/api/operaciones/42/inusualidad/adjuntos").file(archivo),
            get("/api/operaciones/42/inusualidad/adjuntos/5"),
            post("/api/operaciones/42/inusualidad/decision").contentType(json).content("""{"decision":"SOSPECHOSA"}"""),
            get("/api/operaciones/42/ros"),
            put("/api/operaciones/42/ros").contentType(json).content("""{"numeroRos":"X"}""")
        ).forEach { pedido ->
            mvc.perform(pedido).andExpect(status().isNotFound).andExpect(jsonPath("$.code").value(CodigosOperaciones.NO_ENCONTRADA))
        }
        verify { analisis wasNot Called }
    }

    @Test
    fun `lo que es del administrador responde 403 a una cuenta comun`() {
        every { acceso.comprobarRegistro(any()) } just Runs
        every { acceso.actor() } returns comun
        every { acceso.empresaPermitida(any()) } returns "Empresa Gamma"
        every { acceso.administrador() } throws OperacionesException(HttpStatus.FORBIDDEN, CodigosOperaciones.SOLO_ADMINISTRADOR, "Solo administrador")
        val archivo = MockMultipartFile("archivo", "prueba.pdf", "application/pdf", byteArrayOf(1))
        listOf(
            put("/api/operaciones/configuracion").contentType(json).content("{}"),
            put("/api/operaciones/tipos-cambio").contentType(json).content("{}"),
            post("/api/operaciones/42/inusualidad/decision").contentType(json).content("""{"decision":"SOSPECHOSA"}"""),
            put("/api/operaciones/42/ros").contentType(json).content("""{"numeroRos":"X"}"""),
            post("/api/operaciones/exportaciones").contentType(json).content("""{"periodo":"2026-10"}"""),
            multipart("/api/operaciones/exportaciones/5/constancia").file(archivo).param("numeroAcuse", "A")
        ).forEach { pedido ->
            mvc.perform(pedido).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value(CodigosOperaciones.SOLO_ADMINISTRADOR))
        }
        verify { configuracion wasNot Called; tiposCambio wasNot Called; analisis wasNot Called; exportaciones wasNot Called }
    }

    @Test
    fun `la configuracion, los tipos de cambio, la verificacion y la exportacion usan la empresa del acceso`() {
        every { acceso.actor() } returns comun
        every { acceso.empresaPermitida("Empresa Delta") } returns "Empresa Gamma"
        every { configuracion.ver("Empresa Gamma", false) } returns mockk(relaxed = true)
        every { tiposCambio.listar("Empresa Gamma", 2026, false) } returns mockk(relaxed = true)
        every { umbrales.evaluar("Empresa Gamma", any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { exportaciones.validacion("Empresa Gamma", "2026-10", null, false) } returns mockk(relaxed = true)
        every { exportaciones.listar("Empresa Gamma", 0, 10, false) } returns mockk(relaxed = true)
        every { exportaciones.ver("Empresa Gamma", 5, false) } throws
            ResourceNotFoundException("No existe", CodigosOperaciones.EXPORTACION_NO_ENCONTRADA)

        val controlador = ConfiguracionOperacionesController(acceso, configuracion, tiposCambio, umbrales)
        controlador.configuracion("Empresa Delta")
        controlador.tiposCambio("Empresa Delta", 2026)
        controlador.verificarUmbral("Empresa Delta", com.security.modules.operaciones.dto.VerificarUmbralRequest())
        val exportar = ExportacionOperacionesController(acceso, exportaciones)
        exportar.validacion("Empresa Delta", "2026-10", null)
        exportar.listar("Empresa Delta", 0, 10)
        mvc.perform(get("/api/operaciones/exportaciones/5").param("empresa", "Empresa Delta"))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value(CodigosOperaciones.EXPORTACION_NO_ENCONTRADA))
        verify(exactly = 0) {
            configuracion.ver("Empresa Delta", any()); tiposCambio.listar("Empresa Delta", any(), any())
            umbrales.evaluar("Empresa Delta", any(), any(), any(), any())
            exportaciones.validacion("Empresa Delta", any(), any(), any()); exportaciones.listar("Empresa Delta", any(), any(), any())
        }
    }
}
