package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.AccesoEmpresaService
import com.security.modules.operaciones.application.service.OperacionService
import com.security.modules.operaciones.controller.infrastructure.OperacionController
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.dto.CambiarUsuarioOperacionRequest
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.*

class CambiarUsuarioOperacionTest {
    private val operaciones = mockk<OperacionRepository>()
    private val usuarios = mockk<UsuarioRepository>()
    private val service = OperacionService(operaciones, usuarios)
    private val acceso = mockk<AccesoEmpresaService>(relaxed = true)
    private fun usuario(id: Int, empresa: String? = "Peru Luz S.A.C", activo: Boolean = true) =
        Usuario(id = id, usuario = "usuario$id", clave = "test", correo = "$id@example.test",
            nombres = "Ana", apePat = "Perez", empresa = empresa, activo = activo)

    private fun preparar(nuevo: Usuario? = usuario(2)) {
        val registro = mockk<RegistroOperacionesEntity>()
        every { registro.idUsuario } returns 1
        every { operaciones.findById(77L) } returns Optional.of(registro)
        every { usuarios.findById(1) } returns Optional.of(usuario(1))
        every { usuarios.findById(2) } returns Optional.ofNullable(nuevo)
    }

    @Test
    fun `endpoint cambia id usuario usando la empresa del usuario anterior`() {
        preparar()
        every { operaciones.cambiarUsuario(77L, 1, 2) } returns 1
        val resultado = OperacionController(service, acceso).cambiarUsuarioOperacion(77L, CambiarUsuarioOperacionRequest(2))
        verify(exactly = 1) { acceso.comprobarRegistro(77L) }
        assertEquals(77L, resultado.id)
        assertEquals(2, resultado.usuarioId)
        assertEquals("Ana Perez", resultado.nombreCompleto)
        verify(exactly = 1) { operaciones.cambiarUsuario(77L, 1, 2) }
    }

    @Test
    fun `rechaza empresa diferente ausente usuario inactivo o inexistente`() {
        listOf(usuario(2, "Otra"), usuario(2, null), usuario(2, " "), usuario(2, activo = false), null).forEach {
            preparar(it)
            val error = assertFailsWith<ResponseStatusException> { service.cambiarUsuarioOperacion(77L, 2) }
            assertEquals(if (it == null) 404 else 400, error.statusCode.value())
        }
        verify(exactly = 0) { operaciones.cambiarUsuario(any(), any(), any()) }
    }

    @Test
    fun `rechaza operacion inexistente`() {
        every { operaciones.findById(77L) } returns Optional.empty()
        assertEquals(404, assertFailsWith<ResponseStatusException> {
            service.cambiarUsuarioOperacion(77L, 2)
        }.statusCode.value())
        verify { usuarios wasNot Called }
    }

    @Test
    fun `conflicto si otro cambio reemplazo al usuario anterior`() {
        preparar()
        every { operaciones.cambiarUsuario(77L, 1, 2) } returns 0
        assertEquals(409, assertFailsWith<ResponseStatusException> {
            service.cambiarUsuarioOperacion(77L, 2)
        }.statusCode.value())
    }

    @Test
    fun `elegir el mismo usuario no requiere actualizacion`() {
        preparar()
        assertEquals(1, service.cambiarUsuarioOperacion(77L, 1).usuarioId)
        verify(exactly = 0) { operaciones.cambiarUsuario(any(), any(), any()) }
    }

    @Test
    fun `rechaza ids no positivos`() {
        assertEquals(400, assertFailsWith<ResponseStatusException> {
            service.cambiarUsuarioOperacion(0L, 2)
        }.statusCode.value())
        assertEquals(400, assertFailsWith<ResponseStatusException> {
            service.cambiarUsuarioOperacion(77L, -1)
        }.statusCode.value())
        verify { operaciones wasNot Called; usuarios wasNot Called }
    }
}
