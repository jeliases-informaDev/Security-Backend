package com.security.modules.operaciones

import com.security.modules.operaciones.application.usecase.ExportarOperacionPdfUseCase
import com.security.modules.operaciones.domain.*
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.modules.operaciones.infrastructure.adapter.`in`.web.OperacionPdfController
import com.security.modules.operaciones.infrastructure.adapter.out.persistence.OperacionConsultaAdapter
import com.security.modules.operaciones.infrastructure.adapter.out.pdf.PdfBoxGeneradorPdfAdapter
import io.mockk.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import javax.imageio.ImageIO
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import kotlin.test.*

class OperacionPdfTest {
    private val repository = mockk<OperacionRepository>()
    private val controller = OperacionPdfController(ExportarOperacionPdfUseCase(
        OperacionConsultaAdapter(repository), PdfBoxGeneradorPdfAdapter()
    ))
    private val mvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `exporta el registro completo como PDF por ambas rutas`() {
        val fecha = LocalDate.of(2026, 10, 13)
        val persona = Persona().apply {
            nombres = "Ana"; apellidos = "Torres"; numeroDocumento = "12345678"
            tipoDocumento = "DNI"; fechaNacimiento = fecha.minusYears(30)
            nacionalidad = "Peruana"; telefono = "999 123 456"
            profesionOcupacion = "Administradora de empresas"
            domicilio = "Av. Javier Prado Este 1234, oficina 502"
            codigoPostal = "15036"; departamento = "Lima"; provincia = "Lima"; distrito = "San Isidro"
        }
        val registro = RegistroOperacionesEntity(
            id = 42, numeroRegistro = "OP-PRUEBA", idUsuario = 1,
            empresa = "Empresa prueba", oficina = "Lima", fechaRegistro = fecha,
            personaFisica = persona, personaRepresentada = persona,
            operacion = Operacion(fechaOperacion = fecha, lugar = "Lima",
                modalidadPago = "Transferencia", moneda = "PEN", monto = BigDecimal("123.45"),
                tipoOperacion = "Deposito", cuenta1 = null, cuenta2 = null, cuenta3 = null),
            beneficiarios = listOf(BeneficiarioEntity(nombre = "Luis Beneficiario", tipoDocumento = "DNI",
                numeroDocumento = "87654321", fechaNacimiento = fecha.minusYears(20)),
                BeneficiarioEntity(nombre = "Maria Fernanda Ramirez Lopez", tipoDocumento = "Carné de extranjería",
                    numeroDocumento = "000123456", fechaNacimiento = fecha.minusYears(25)))
        )
        every { repository.findById(42) } returns Optional.of(registro)
        every { repository.buscarNombreUsuarioPorId(1) } returns "usuario_prueba"
        for (ruta in listOf("/api/operaciones", "/api/pdf")) {
            val response = mvc.perform(get("$ruta/42/exportar-pdf")).andReturn().response
            assertEquals(200, response.status)
            assertEquals("application/pdf", response.contentType)
            assertTrue(response.getHeader("Content-Disposition")!!.contains("operacion_42.pdf"))
            Loader.loadPDF(response.contentAsByteArray).use { pdf ->
                assertEquals(1, pdf.numberOfPages)
                assertTrue(pdf.getPage(0).resources.xObjectNames.any {
                    pdf.getPage(0).resources.getXObject(it) is PDImageXObject
                }, "El PDF debe incluir el logo")
                val texto = PDFTextStripper().getText(pdf)
                listOf("OP-PRUEBA", "Ana", "Luis Beneficiario", "123.45", "usuario_prueba").forEach {
                    assertTrue(texto.contains(it), "Falta $it en PDF")
                }
                if (ruta == "/api/operaciones") {
                    val output = Path.of("output", "pdf")
                    Files.createDirectories(output)
                    Files.write(output.resolve("operacion-diseno-ejemplo.pdf"), response.contentAsByteArray)
                    val preview = Path.of("tmp", "pdfs")
                    Files.createDirectories(preview)
                    ImageIO.write(PDFRenderer(pdf).renderImageWithDPI(0, 140f), "png",
                        preview.resolve("operacion-diseno-ejemplo.png").toFile())
                }
            }
        }
    }

    @Test
    fun `retorna 404 para registro inexistente y 400 para ID invalido`() {
        every { repository.findById(999) } returns Optional.empty()
        assertEquals(404, mvc.perform(get("/api/operaciones/999/exportar-pdf")).andReturn().response.status)
        assertEquals(400, mvc.perform(get("/api/operaciones/0/exportar-pdf")).andReturn().response.status)
        verify(exactly = 0) { repository.findById(0) }
    }
}
