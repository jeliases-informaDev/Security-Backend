package com.security.modules.operaciones

import com.security.modules.operaciones.domain.*
import com.security.modules.operaciones.infraestructure.*
import com.security.modules.operaciones.infraestructure.CatalogoExportacion.*
import com.security.modules.operaciones.infrastructure.adapter.out.persistence.OperacionConsultaAdapter
import com.security.modules.operaciones.infrastructure.adapter.out.pdf.PdfBoxGeneradorPdfAdapter
import io.mockk.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.rendering.PDFRenderer
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class OperacionPdfCatalogosTest {
    private val operaciones = mockk<OperacionRepository>()
    private val catalogos = mockk<CatalogoRepository>()
    private val adapter = OperacionConsultaAdapter(operaciones, catalogos)
    private val fecha = LocalDate.of(2026, 10, 13)

    private fun registro(): RegistroOperacionesEntity {
        val persona = Persona().apply {
            nombres = "Ana"; apellidos = "Torres"; tipoDocumento = "1"
            numeroDocumento = "00123456"; nacionalidad = "2"; profesionOcupacion = "10"
            departamento = "15"; provincia = "128"; distrito = "Miraflores"
        }
        return RegistroOperacionesEntity(id = 77, numeroRegistro = "OP-CATALOGOS", idUsuario = 1,
            empresa = "Empresa de prueba", oficina = "Lima", fechaRegistro = fecha,
            personaFisica = persona, personaRepresentada = persona,
            operacion = Operacion(fechaOperacion = fecha, lugar = "Lima", modalidadPago = "Transferencia",
                moneda = "1", monto = BigDecimal("1500.25"), tipoOperacion = "2",
                cuenta1 = "001234", cuenta2 = null, cuenta3 = null),
            beneficiarios = listOf(BeneficiarioEntity(nombre = "Luis Torres", tipoDocumento = "1",
                numeroDocumento = "00876543", fechaNacimiento = fecha.minusYears(30))))
    }

    @Test
    fun `resuelve catalogos de ambas personas beneficiarios y operacion al generar PDF`() {
        val registro = registro()
        every { operaciones.findById(77) } returns Optional.of(registro)
        every { operaciones.buscarNombreUsuarioPorId(1) } returns "usuario_prueba"
        val nombres = mapOf((DOCUMENTO to 1L) to "Documento nacional de identidad",
            (PAIS to 2L) to "Peru", (OCUPACION to 10L) to "Administradora de empresas",
            (DEPARTAMENTO to 15L) to "Lima", (PROVINCIA to 128L) to "Provincia de prueba",
            (MONEDA to 1L) to "Sol peruano")
        nombres.forEach { (clave, nombre) ->
            every { catalogos.nombreParaExportacion(clave.first, clave.second) } returns nombre
        }
        val exportacion = requireNotNull(adapter.obtenerParaExportacion(77))
        assertEquals("Documento nacional de identidad", exportacion.personaFisica.tipoDocumento)
        assertEquals("Peru", exportacion.personaRepresentada!!.nacionalidad)
        assertEquals("Administradora de empresas", exportacion.personaFisica.ocupacion)
        assertEquals("Provincia de prueba", exportacion.personaFisica.provincia)
        assertEquals("Documento nacional de identidad", exportacion.beneficiarios.single().tipoDocumento)
        assertEquals("00123456", exportacion.personaFisica.numeroDocumento)
        assertEquals("2", registro.personaFisica.nacionalidad)
        assertEquals("2", exportacion.operacion.tipoOperacion)
        verify(exactly = 6) { catalogos.nombreParaExportacion(any(), any()) }
        nombres.keys.forEach { verify(exactly = 1) { catalogos.nombreParaExportacion(it.first, it.second) } }
        val bytes = PdfBoxGeneradorPdfAdapter().generar(exportacion)
        Loader.loadPDF(bytes).use { pdf ->
            val texto = PDFTextStripper().getText(pdf)
            listOf("Peru", "Administradora de empresas", "Provincia de prueba", "Sol peruano").forEach {
                assertTrue(texto.contains(it), "Falta $it en el PDF")
            }
            val output = Path.of("output/pdf/operacion-catalogos-ejemplo.pdf")
            Files.createDirectories(output.parent)
            Files.write(output, bytes)
            val preview = Path.of("tmp/pdfs/operacion-catalogos-ejemplo.png")
            Files.createDirectories(preview.parent)
            ImageIO.write(PDFRenderer(pdf).renderImageWithDPI(0, 140f), "png", preview.toFile())
        }
    }

    @Test
    fun `conserva texto valores opcionales y codigos que no existen sin alterar datos guardados`() {
        val registro = registro()
        registro.personaFisica.apply {
            tipoDocumento = "DNI"; nacionalidad = "Peruana"; profesionOcupacion = null
            departamento = "Lima"; provincia = "999999"
        }
        every { operaciones.findById(77) } returns Optional.of(registro)
        every { operaciones.buscarNombreUsuarioPorId(1) } returns "usuario_prueba"
        every { catalogos.nombreParaExportacion(any(), any()) } returns null
        val resultado = requireNotNull(adapter.obtenerParaExportacion(77))
        assertEquals("DNI", resultado.personaFisica.tipoDocumento)
        assertEquals("Peruana", resultado.personaFisica.nacionalidad)
        assertNull(resultado.personaFisica.ocupacion)
        assertEquals("999999", resultado.personaFisica.provincia)
        verify(exactly = 0) { catalogos.nombreParaExportacion(PAIS, any()) }
    }
}
