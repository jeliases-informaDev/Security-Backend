package com.security.modules.licencias

import com.security.modules.licencias.dominio.TIPO_DENUNCIA_CREAR
import com.security.modules.licencias.dominio.TIPO_LISTA_PDF
import com.security.modules.licencias.dominio.TIPO_MATRIZ_REGISTRAR
import com.security.modules.licencias.dominio.TIPO_OPERACION_PDF
import com.security.modules.listas.application.service.ListaNegativaService
import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.domain.PersonaNatural
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.operaciones.application.usecase.ExportarOperacionPdfUseCase
import com.security.modules.operaciones.infrastructure.adapter.`in`.web.OperacionPdfController
import com.security.shared.datos.entities.TipoDocumento
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Los avisos nuevos a Sulotec: permiso antes, anulación si la acción falla, claves y el canal de denuncias que nunca se frena. */
class CobroAccionesTest {
    private val contexto = mockk<LicenciaContexto>()
    private val licencias = mockk<LicenciaService>(relaxed = true)
    private val cobro = CobroAcciones(contexto, licencias)
    private val quien = ContextoLicencia(usuario = "analista", empresa = "Peru Luz", empresaUuid = "u-1", esSulotec = false)

    private fun encendidas() {
        every { licencias.activo } returns true
        every { contexto.actual() } returns quien
    }

    @Test
    fun `con licencias apagadas no avisa nada y ejecuta la accion`() {
        every { licencias.activo } returns false
        assertEquals("hecho", cobro.ejecutar(TIPO_MATRIZ_REGISTRAR, "registro") { "hecho" })
        verify(exactly = 0) { licencias.consumirComo(any(), any(), any(), any()) }
    }

    @Test
    fun `sin empresa con UUID no avisa nada`() {
        every { licencias.activo } returns true
        every { contexto.actual() } returns quien.copy(empresaUuid = null)
        assertNull(cobro.pedir(TIPO_MATRIZ_REGISTRAR, "registro"))
        verify(exactly = 0) { licencias.consumirComo(any(), any(), any(), any()) }
    }

    @Test
    fun `un registro pide permiso con una clave nueva cada vez`() {
        encendidas()
        val claves = mutableListOf<String>()
        every { licencias.consumirComo(quien, TIPO_MATRIZ_REGISTRAR, capture(claves), any()) } returns Unit

        cobro.ejecutar(TIPO_MATRIZ_REGISTRAR, "registro") { 1 }
        cobro.ejecutar(TIPO_MATRIZ_REGISTRAR, "registro") { 2 }

        assertEquals(2, claves.size)
        assertNotEquals(claves[0], claves[1])
        assertTrue(claves.all { it.startsWith("plaft:u-1:matriz_registrar:") })
        verify(exactly = 0) { licencias.anular(any()) }
    }

    @Test
    fun `si la accion falla despues de avisar se anula el consumo`() {
        encendidas()
        every { licencias.claveDeterminista("u-1", "analista", TIPO_OPERACION_PDF, listOf("7")) } returns "clave-op"

        assertFailsWith<IllegalStateException> {
            cobro.ejecutar(TIPO_OPERACION_PDF, "pdf", criterios = listOf("7")) { throw IllegalStateException("sin pdf") }
        }
        verify(exactly = 1) { licencias.consumirComo(quien, TIPO_OPERACION_PDF, "clave-op", any()) }
        verify(exactly = 1) { licencias.anular("clave-op") }
    }

    @Test
    fun `si Sulotec dice que no se responde 402 salvo en el canal de denuncias`() {
        encendidas()
        every { licencias.consumirComo(any(), any(), any(), any()) } throws LicenciaBolsaAgotadaException()

        var hecho = false
        assertFailsWith<LicenciaBolsaAgotadaException> { cobro.ejecutar(TIPO_MATRIZ_REGISTRAR, "registro") { hecho = true } }
        assertFalse(hecho)

        assertEquals("denuncia", cobro.ejecutar(TIPO_DENUNCIA_CREAR, "denuncia", nuncaBloquea = true) { "denuncia" })

        every { licencias.consumirComo(any(), any(), any(), any()) } throws
            LicenciaBloqueadaException(estado = "SUSPENDIDA", code = LicenciaReglas.CODE_SUSPENDIDA)
        assertEquals("denuncia", cobro.ejecutar(TIPO_DENUNCIA_CREAR, "denuncia", nuncaBloquea = true) { "denuncia" })
    }

    @Test
    fun `sin cobro inyectado solo se ejecuta la accion`() {
        val nada: CobroAcciones? = null
        assertEquals(3, nada.conCobro(TIPO_OPERACION_PDF, "pdf") { 3 })
    }

    // ---------- Puntos de descuento ----------

    @Test
    fun `el PDF de una coincidencia avisa lista_pdf con la clave de la persona y la entidad`() {
        encendidas()
        val entidades = mockk<EntidadRepository>()
        val usuarios = mockk<UsuarioRepository>()
        every { usuarios.findByUsuario("analista") } returns Usuario(id = 1, usuario = "analista", clave = "x", correo = "a@ejemplo.com")
        val entidad = Entidad(id = 42, tipoDocumento = TipoDocumento(id = 1, nombre = "DNI"), documento = "00000042", tipoEntidad = Entidad.TIPO_NATURAL)
        entidad.personaNatural = PersonaNatural(entidad = entidad, nombre = "JUAN", apePat = "GARCIA", apeMat = "PEREZ")
        every { entidades.buscarPorIdConDetalle(42) } returns entidad
        every { licencias.claveDeterminista("u-1", "analista", TIPO_LISTA_PDF, listOf("42")) } returns "clave-pdf"
        val service = ListaNegativaService(entidades, mockk(), usuarios, mockk(), contexto, licencias)

        val pdf = service.exportarPdf("analista", 42)

        assertTrue(pdf.isNotEmpty())
        verify(exactly = 1) { licencias.consumirComo(quien, TIPO_LISTA_PDF, "clave-pdf", any()) }
        verify(exactly = 0) { licencias.anular(any()) }
    }

    @Test
    fun `el PDF de una operacion avisa operacion_pdf y si falla se anula`() {
        encendidas()
        val exportar = mockk<ExportarOperacionPdfUseCase>()
        every { licencias.claveDeterminista("u-1", "analista", TIPO_OPERACION_PDF, listOf("7")) } returns "clave-op"
        val controller = OperacionPdfController(exportar, mockk(relaxed = true), cobro)

        every { exportar.ejecutar(7L) } returns byteArrayOf(1, 2, 3)
        assertEquals(3, controller.exportarPdf(7L).body!!.size)
        verify(exactly = 1) { licencias.consumirComo(quien, TIPO_OPERACION_PDF, "clave-op", any()) }

        every { exportar.ejecutar(7L) } throws IllegalStateException("sin pdf")
        assertFailsWith<IllegalStateException> { controller.exportarPdf(7L) }
        verify(exactly = 1) { licencias.anular("clave-op") }
    }
}
