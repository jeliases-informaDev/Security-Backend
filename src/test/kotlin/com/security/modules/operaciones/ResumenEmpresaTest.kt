package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.OperacionService
import com.security.modules.operaciones.controller.infrastructure.OperacionController
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.modules.operaciones.infraestructure.OperacionResumenProjection
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.web.server.ResponseStatusException
import kotlin.test.*

class ResumenEmpresaTest {
    private val operaciones = mockk<OperacionRepository>()
    private val usuarios = mockk<UsuarioRepository>()
    private val service = OperacionService(operaciones, usuarios)

    @Test
    fun `usa empresa del frontend y conserva contenido y paginacion`() {
        val pagina = PageRequest.of(1, 2)
        val fila = mockk<OperacionResumenProjection>(relaxed = true)
        every { fila.id } returns 42L
        every { operaciones.listarResumenPorEmpresa("Peru Luz S.A.C", pagina) } returns
            PageImpl(listOf(fila), pagina, 3L)
        val resultado = OperacionController(service).listarResumen(" Peru Luz S.A.C ", 1, 2)
        assertEquals(42L, resultado.content.single().id)
        assertEquals(1, resultado.currentPage)
        assertEquals(2, resultado.totalPages)
        assertEquals(3L, resultado.totalElements)
        verify { usuarios wasNot Called }
    }

    @Test
    fun `sin coincidencias devuelve pagina vacia`() {
        val pagina = PageRequest.of(0, 10)
        every { operaciones.listarResumenPorEmpresa("Sin registros", pagina) } returns
            PageImpl(emptyList(), pagina, 0L)
        assertTrue(service.obtenerResumenPaginado(0, 10, "Sin registros").content.isEmpty())
    }

    @Test
    fun `rechaza empresa vacia y paginacion invalida`() {
        listOf(Triple(0, 10, " "), Triple(-1, 10, "Empresa"), Triple(0, 0, "Empresa")).forEach {
            assertEquals(400, assertFailsWith<ResponseStatusException> {
                service.obtenerResumenPaginado(it.first, it.second, it.third)
            }.statusCode.value())
        }
        verify { operaciones wasNot Called; usuarios wasNot Called }
    }
}
