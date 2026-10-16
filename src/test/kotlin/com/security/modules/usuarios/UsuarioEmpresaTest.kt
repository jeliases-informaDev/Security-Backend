package com.security.modules.usuarios

import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.usuarios.application.service.UsuarioService
import com.security.modules.usuarios.dto.CambiarEmpresaRequest
import com.security.modules.usuarios.dto.UsuarioCreateRequest
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import com.security.shared.exceptions.ResourceNotFoundException
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

class UsuarioEmpresaTest {
    private val usuarios = mockk<UsuarioRepository>()
    private val roles = mockk<RolesRepository>()
    private val usuarioRoles = mockk<UsuarioRolRepository>()
    private val encoder = mockk<PasswordEncoder>()
    private val empresaService = mockk<EmpresaService>()
    private val service = UsuarioService(usuarios, roles, usuarioRoles, encoder, empresaService)

    private fun rol(codigo: String = "USUARIO", nombre: String = "Usuario", activo: Boolean = true) =
        Roles(id = 3, codigo = codigo, nombre = nombre, activo = activo,
            fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now())

    private fun solicitud(empresa: String? = "peru luz") = UsuarioCreateRequest(
        usuario = "ana", clave = "Clave12345", correo = "ana@example.test", nombres = "Ana", apePat = "Perez",
        empresa = empresa, rolCodigo = "USUARIO"
    )

    private fun prepararCreacion() {
        every { usuarios.existsByUsuario("ana") } returns false
        every { usuarios.existsByCorreo("ana@example.test") } returns false
        every { roles.findByCodigo("USUARIO") } returns rol()
        every { encoder.encode(any()) } returns "hash"
        every { usuarioRoles.save(any()) } answers { firstArg() }
    }

    @Test
    fun `crear guarda la cuenta con el nombre exacto de la empresa de la lista`() {
        prepararCreacion()
        every { empresaService.resolverActiva("peru luz") } returns "Peru Luz S.A.C"
        val guardado = slot<Usuario>()
        every { usuarios.save(capture(guardado)) } answers { guardado.captured.also { it.id = 5 } }

        val respuesta = service.crear(solicitud())

        assertEquals("Peru Luz S.A.C", guardado.captured.empresa)
        assertEquals("Peru Luz S.A.C", respuesta.empresa)
        assertEquals("USUARIO", respuesta.rolCodigo)
    }

    @Test
    fun `crear no guarda nada si la empresa no esta en la lista o no se indica`() {
        prepararCreacion()
        every { empresaService.resolverActiva(any()) } throws ResponseStatusException(HttpStatus.BAD_REQUEST, "La empresa es obligatoria")

        listOf(null, "", "Fantasma").forEach {
            val error = assertFailsWith<ResponseStatusException> { service.crear(solicitud(it)) }
            assertEquals(400, error.statusCode.value())
        }
        verify(exactly = 0) { usuarios.save(any()) }
        verify(exactly = 0) { usuarioRoles.save(any()) }
    }

    @Test
    fun `cambiarEmpresa pasa la cuenta a la empresa de la lista`() {
        val cuenta = Usuario(id = 3, usuario = "ana", clave = "x", correo = "ana@example.test", empresa = "Otra",
            fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now())
        every { usuarios.findById(3) } returns Optional.of(cuenta)
        every { empresaService.resolverActiva("peru luz") } returns "Peru Luz S.A.C"
        every { usuarios.save(any()) } answers { firstArg() }

        val respuesta = service.cambiarEmpresa(3, CambiarEmpresaRequest("peru luz"))

        assertEquals("Peru Luz S.A.C", respuesta.empresa)
        assertEquals("Peru Luz S.A.C", cuenta.empresa)
    }

    @Test
    fun `cambiarEmpresa rechaza una empresa no valida y una cuenta que no existe`() {
        val cuenta = Usuario(id = 3, usuario = "ana", clave = "x", correo = "ana@example.test", empresa = "Otra",
            fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now())
        every { usuarios.findById(3) } returns Optional.of(cuenta)
        every { empresaService.resolverActiva("Fantasma") } throws ResponseStatusException(HttpStatus.BAD_REQUEST, "no esta en la lista")
        assertFailsWith<ResponseStatusException> { service.cambiarEmpresa(3, CambiarEmpresaRequest("Fantasma")) }
        assertEquals("Otra", cuenta.empresa)

        every { usuarios.findById(99) } returns Optional.empty()
        assertFailsWith<ResourceNotFoundException> { service.cambiarEmpresa(99, CambiarEmpresaRequest("peru luz")) }
        verify(exactly = 0) { usuarios.save(any()) }
    }

    @Test
    fun `listarRoles devuelve solo los roles activos ordenados por nombre`() {
        every { roles.findAll() } returns listOf(
            rol("USUARIO", "Usuario"),
            rol("ANTIGUO", "Antiguo", activo = false),
            rol("ADMINISTRADOR", "Administrador")
        )

        val lista = service.listarRoles()

        assertEquals(listOf("ADMINISTRADOR", "USUARIO"), lista.map { it.codigo })
        assertEquals(listOf("Administrador", "Usuario"), lista.map { it.nombre })
    }
}
