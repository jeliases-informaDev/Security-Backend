package com.security.modules.listas

import com.security.SecurityConfig
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
    private val service = ListaNegativaService(entidades, historial, usuarios, mockk())
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
    fun `devuelve solo la pagina pedida con el total, lee las apariciones de una vez y guarda en el historial solo a esas personas`() {
        val pagina = listOf(persona(1), persona(2))
        every { entidades.buscar(null, null, null, "GARCIA", null, primeraPagina) } returns PageImpl(pagina, primeraPagina, 523)
        every { entidades.cargarManchas(listOf(1, 2)) } returns pagina

        val resultado = service.buscar("analista", documento = null, tipoDocumento = null, nombres = null, apellidoPaterno = "GARCIA", apellidoMaterno = null)

        assertEquals(523, resultado.totalElements)
        assertEquals(listOf(1, 2), resultado.content.map { it.entidadId })
        assertEquals(listOf("OFAC"), resultado.content[0].manchas.map { it.tipoListaCodigo })
        verify(exactly = 1) { entidades.cargarManchas(any()) }
        verify(exactly = 1) { historial.saveAll(match<Iterable<HistorialConsulta>> { guardadas -> guardadas.map { it.entidad.id } == listOf(1, 2) }) }
    }

    @Test
    fun `nunca pide mas de 50 por pagina y conserva el total aunque la pagina pedida este vacia`() {
        val pedida = PageRequest.of(2, 50)
        every { entidades.buscar(null, null, null, "GARCIA", null, pedida) } returns PageImpl(emptyList(), pedida, 120)

        val resultado = service.buscar("analista", null, null, null, "GARCIA", null, pagina = 2, tamano = 500)

        assertEquals(50, resultado.size)
        assertEquals(120, resultado.totalElements)
        assertTrue(resultado.content.isEmpty())
        verify(exactly = 0) { entidades.cargarManchas(any()) }
        verify { historial wasNot Called }
    }

    @Test
    fun `sin coincidencias no lee apariciones ni guarda historial`() {
        every { entidades.buscar(any(), any(), any(), any(), any(), any()) } returns Page.empty(primeraPagina)

        val resultado = service.buscar("analista", null, null, "ZZZZ", null, null)

        assertEquals(0, resultado.totalElements)
        verify(exactly = 0) { entidades.cargarManchas(any()) }
        verify { historial wasNot Called }
    }

    @Test
    fun `rechaza busquedas sin criterio, con menos de 3 letras o con paginacion invalida`() {
        val invalidas = listOf<() -> Any>(
            { service.buscar("analista", null, null, null, null, null) },
            { service.buscar("analista", " ", null, "  ", "", null) },
            { service.buscar("analista", null, null, "ab", null, null) },
            { service.buscar("analista", null, null, "%_", "a", null) },
            { service.buscar("analista", null, null, null, "GARCIA", null, pagina = -1) },
            { service.buscar("analista", null, null, null, "GARCIA", null, tamano = 0) }
        )
        invalidas.forEach { buscar ->
            assertEquals(400, assertFailsWith<ResponseStatusException> { buscar() }.statusCode.value())
        }
        verify { entidades wasNot Called; historial wasNot Called }
    }

    @Test
    fun `un apellido corto sirve si viene con documento o junto a otro texto de 3 letras o mas`() {
        every { entidades.buscar("12345678", "DNI", null, "LI", null, primeraPagina) } returns Page.empty(primeraPagina)
        every { entidades.buscar(null, null, null, "LI", "RUIZ", primeraPagina) } returns Page.empty(primeraPagina)

        service.buscar("analista", " 12345678 ", "DNI", null, "LI", null)
        service.buscar("analista", null, null, null, "LI", "RUIZ")

        verify(exactly = 1) { entidades.buscar("12345678", "DNI", null, "LI", null, primeraPagina) }
        verify(exactly = 1) { entidades.buscar(null, null, null, "LI", "RUIZ", primeraPagina) }
    }

    @Test
    fun `el controlador responde la lista de la pagina y pone el total en cabeceras`() {
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
    fun `CORS deja que la web lea las cabeceras del total`() {
        val filtro = SecurityConfig(mockk(), mockk(), mockk(), listOf("http://localhost:3000")).corsFilter()
        val peticion = MockHttpServletRequest("GET", "/api/listas-negativas/buscar").apply { addHeader("Origin", "http://localhost:3000") }
        val respuesta = MockHttpServletResponse()

        filtro.doFilter(peticion, respuesta, MockFilterChain())

        val expuestas = respuesta.getHeader("Access-Control-Expose-Headers")!!.split(",").map { it.trim() }
        assertTrue(expuestas.containsAll(CabecerasPaginacion.TODAS), "cabeceras expuestas: $expuestas")
    }
}
