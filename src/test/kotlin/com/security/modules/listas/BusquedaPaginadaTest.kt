package com.security.modules.listas

import com.security.SecurityConfig
import com.security.modules.licencias.ContextoLicencia
import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.dominio.TIPO_BUSQUEDA_LISTAS
import com.security.modules.listas.application.service.ListaNegativaService
import com.security.modules.listas.controller.infraestructure.ListaNegativaController
import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.domain.HistorialConsulta
import com.security.modules.listas.domain.HistorialMancha
import com.security.modules.listas.domain.PersonaNatural
import com.security.modules.listas.domain.TipoLista
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.listas.infrastructure.HistorialConsultaRepository
import com.security.shared.config.CabecerasPaginacion
import com.security.shared.datos.entities.TipoDocumento
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException
import kotlin.test.*

class BusquedaPaginadaTest {
    private val entidades = mockk<EntidadRepository>()
    private val historial = mockk<HistorialConsultaRepository>()
    private val usuarios = mockk<UsuarioRepository>()
    private val contexto = mockk<LicenciaContexto>()
    private val licencias = mockk<LicenciaService>(relaxed = true)
    private val service = ListaNegativaService(entidades, historial, usuarios, mockk(), contexto, licencias)
    private val controller = ListaNegativaController(service)
    private val autenticacion = mockk<Authentication>()
    private val primeraPagina = PageRequest.of(0, 20)

    init {
        every { autenticacion.name } returns "analista"
        every { usuarios.findByUsuario("analista") } returns Usuario(id = 1, usuario = "analista", clave = "x", correo = "a@b.c")
        every { historial.saveAll(any<Iterable<HistorialConsulta>>()) } answers { firstArg<Iterable<HistorialConsulta>>().toList() }
    }

    private fun persona(id: Int, conAparicion: Boolean = true): Entidad {
        val entidad = Entidad(id = id, tipoDocumento = TipoDocumento(id = 1, nombre = "DNI"), documento = "%08d".format(id), tipoEntidad = Entidad.TIPO_NATURAL)
        entidad.personaNatural = PersonaNatural(entidad = entidad, nombre = "JUAN", apePat = "GARCIA", apeMat = "PEREZ")
        if (conAparicion) {
            entidad.manchas.add(HistorialMancha(id = id * 10, entidad = entidad, tipoLista = TipoLista(id = 1, codigo = "OFAC", nombre = "OFAC")))
        }
        return entidad
    }

    @Test
    fun `buscar en pagina 0 devuelve resultados y guarda historial`() {
        val p = persona(1)
        every { entidades.buscar(any(), any(), any(), any(), any(), any()) } returns PageImpl(listOf(p), primeraPagina, 1)
        every { entidades.cargarManchas(any()) } returns listOf(p)

        val resultado = service.buscar("analista", "12345678", null, null, null, null, 0, 20)
        assertEquals(1, resultado.totalElements)
        assertEquals("JUAN GARCIA PEREZ", resultado.content.first().nombreCompleto)
    }

    @Test
    fun `controlador responde la lista de la pagina y pone el total en cabeceras`() {
        val pagina = listOf(persona(1), persona(2))
        every { entidades.buscar(any(), any(), any(), any(), any(), any()) } returns PageImpl(pagina, primeraPagina, 523)
        every { entidades.cargarManchas(any()) } returns pagina

        val respuesta = controller.buscar(autenticacion, null, null, null, "GARCIA", null, page = 0, size = 20)

        assertEquals(2, respuesta.body!!.size)
        assertEquals("523", respuesta.headers.getFirst(CabecerasPaginacion.TOTAL))
        assertEquals("27", respuesta.headers.getFirst(CabecerasPaginacion.TOTAL_PAGINAS))
        assertEquals("0", respuesta.headers.getFirst(CabecerasPaginacion.PAGINA))
        assertEquals("20", respuesta.headers.getFirst(CabecerasPaginacion.TAMANO))
    }

    @Test
    fun `exportarPdf entrega el binario PDF con cabeceras y no consume tokens`() {
        val p = persona(42)
        every { entidades.buscarPorIdConDetalle(42) } returns p

        val respuesta = controller.exportarPdf(autenticacion, 42)

        assertEquals(MediaType.APPLICATION_PDF, respuesta.headers.contentType)
        assertTrue(respuesta.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!.contains("listas-negativas-00000042.pdf"))
        assertTrue(respuesta.body!!.isNotEmpty())
        verify(exactly = 0) { licencias.consumirComo(any(), any(), any(), any()) }
    }

    private val quien = ContextoLicencia(usuario = "analista", empresa = "Peru Luz", empresaUuid = "u-1", esSulotec = false)

    private fun licenciasEncendidas() {
        every { licencias.activo } returns true
        every { contexto.actual() } returns quien
        every { licencias.claveBusqueda("u-1", "analista", any()) } returns "clave-1"
    }

    @Test
    fun `la pagina 0 pide 1 token antes de consultar y la pagina 1 no`() {
        licenciasEncendidas()
        val p = persona(1)
        every { entidades.buscar(any(), any(), any(), any(), any(), any()) } returns PageImpl(listOf(p), primeraPagina, 30)
        every { entidades.cargarManchas(any()) } returns listOf(p)

        service.buscar("analista", null, null, null, "GARCIA", null, 0, 20)
        verify(exactly = 1) { licencias.consumirComo(quien, TIPO_BUSQUEDA_LISTAS, "clave-1", any()) }

        service.buscar("analista", null, null, null, "GARCIA", null, 1, 20)
        verify(exactly = 1) { licencias.consumirComo(any(), any(), any(), any()) }
        verify(exactly = 0) { licencias.anular(any()) }
    }

    @Test
    fun `si la consulta falla despues de consumir se devuelve el token`() {
        licenciasEncendidas()
        every { entidades.buscar(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("sin base")

        assertFailsWith<IllegalStateException> { service.buscar("analista", null, null, null, "GARCIA", null, 0, 20) }
        verify(exactly = 1) { licencias.anular("clave-1") }
    }

    @Test
    fun `CORS deja que la web lea las cabeceras del total`() {
        val filtro = SecurityConfig(mockk(), mockk(), mockk(), listOf("http://localhost:3000")).corsFilter()
        val peticion = MockHttpServletRequest("GET", "/api/listas-negativas/buscar").apply { addHeader("Origin", "http://localhost:3000") }
        val respuesta = MockHttpServletResponse()

        filtro.doFilter(peticion, respuesta, MockFilterChain())

        val expuestas = respuesta.getHeader("Access-Control-Expose-Headers")!!.split(",").map { it.trim() }
        assertTrue(expuestas.containsAll(CabecerasPaginacion.TODAS), "cabeceras expuestas: $expuestas")
    }
}
