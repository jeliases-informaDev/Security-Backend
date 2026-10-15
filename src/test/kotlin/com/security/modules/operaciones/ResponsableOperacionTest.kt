package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.OperacionService
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.*

class ResponsableOperacionTest {
    private val operaciones = mockk<OperacionRepository>()
    private val usuarios = mockk<UsuarioRepository>()
    private val service = OperacionService(operaciones, usuarios)
    private fun usuario(id: Int, empresa: String? = "Empresa", activo: Boolean = true) =
        Usuario(id = id, usuario = "usuario$id", clave = "test", correo = "$id@example.test",
            nombres = " Carlos ", apePat = "Perez", empresa = empresa, activo = activo)

    private fun autenticar(empresa: String? = "Empresa") {
        every { usuarios.findByUsuario("actor") } returns usuario(1, empresa)
    }

    @Test
    fun `consulta la empresa enviada sin depender de la cuenta autenticada`() {
        every { usuarios.findAllByEmpresaOrderByNombresAscApePatAscIdAsc("Peru Luz S.A.C") } returns
            listOf(usuario(1, "Peru Luz S.A.C"), usuario(2, "Peru Luz S.A.C", activo = false))
        val resultado = service.listarUsuariosPorEmpresa(" Peru Luz S.A.C ")
        assertEquals(listOf(1, 2), resultado.map { it.id })
        assertEquals("Carlos Perez", resultado.first().nombreCompleto)
        assertFalse(resultado.last().activo)
        assertTrue(resultado.all { it.empresa == "Peru Luz S.A.C" })
        verify(exactly = 0) { usuarios.findByUsuario(any()) }
    }

    @Test
    fun `empresa sin usuarios devuelve lista vacia`() {
        every { usuarios.findAllByEmpresaOrderByNombresAscApePatAscIdAsc("Otra") } returns emptyList()
        assertTrue(service.listarUsuariosPorEmpresa("Otra").isEmpty())
    }

    @Test
    fun `consulta por empresa rechaza vacia`() {
        assertEquals(400, assertFailsWith<ResponseStatusException> {
            service.listarUsuariosPorEmpresa(" ")
        }.statusCode.value())
        verify(exactly = 0) { usuarios.findAllByEmpresaOrderByNombresAscApePatAscIdAsc(any()) }
    }

    @Test
    fun `lista usuarios activos de la empresa con nombres limpios`() {
        autenticar()
        every { usuarios.findAllByEmpresaAndActivoTrueOrderByNombresAscApePatAsc("Empresa") } returns listOf(usuario(2))
        val lista = service.listarUsuariosAsignables("actor")
        assertEquals(2, lista.single().id)
        assertEquals("Carlos Perez", lista.single().nombreCompleto)
    }

    @Test
    fun `rechaza usuario autenticado sin empresa`() {
        autenticar(" ")
        assertEquals(403, assertFailsWith<ResponseStatusException> {
            service.listarUsuariosAsignables("actor")
        }.statusCode.value())
        verify(exactly = 0) { usuarios.findAllByEmpresaAndActivoTrueOrderByNombresAscApePatAsc(any()) }
    }

    @Test
    fun `asigna y permite reemplazar al responsable`() {
        autenticar()
        val registro = mockk<RegistroOperacionesEntity>(relaxed = true)
        every { registro.empresa } returns "Empresa"
        every { operaciones.findById(3L) } returns Optional.of(registro)
        every { usuarios.findById(2) } returns Optional.of(usuario(2))
        every { operaciones.flush() } just Runs
        val resultado = service.asignarResponsable(3L, 2, "actor")
        assertEquals(2, resultado.responsable.id)
        verify { registro.idUsuarioResponsable = 2; operaciones.flush() }
        every { usuarios.findById(4) } returns Optional.of(usuario(4))
        assertEquals(4, service.asignarResponsable(3L, 4, "actor").responsable.id)
        verify { registro.idUsuarioResponsable = 4 }

    }

    @Test
    fun `rechaza operacion de otra empresa`() {
        autenticar()
        val registro = mockk<RegistroOperacionesEntity>()
        every { registro.empresa } returns "Otra"
        every { operaciones.findById(3L) } returns Optional.of(registro)
        assertEquals(404, assertFailsWith<ResponseStatusException> {
            service.asignarResponsable(3L, 2, "actor")
        }.statusCode.value())
        verify(exactly = 0) { usuarios.findById(any()); operaciones.flush() }
    }

    @Test
    fun `rechaza responsables ajenos inactivos o inexistentes`() {
        autenticar()
        val registro = mockk<RegistroOperacionesEntity>()
        every { registro.empresa } returns "Empresa"
        every { operaciones.findById(3L) } returns Optional.of(registro)
        listOf(usuario(2, "Otra"), usuario(2, activo = false), null).forEach { candidato ->
            every { usuarios.findById(2) } returns Optional.ofNullable(candidato)
            assertEquals(400, assertFailsWith<ResponseStatusException> {
                service.asignarResponsable(3L, 2, "actor")
            }.statusCode.value())
        }
        verify(exactly = 0) { registro.idUsuarioResponsable = any(); operaciones.flush() }
    }
}
