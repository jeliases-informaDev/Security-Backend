package com.security.modules.auth

import com.security.modules.auth.dto.LoginRequest
import com.security.modules.auth.security.JwtService
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginPorCorreoTest {
    private val usuarios = mockk<UsuarioRepository>()
    private val encoder = mockk<PasswordEncoder>()
    private val jwt = mockk<JwtService>()
    private val service = AuthService(usuarios, encoder, jwt)

    private fun cuenta(): Usuario {
        val ahora = LocalDateTime.now()
        val u = Usuario(id = 1, usuario = "ch.ana", clave = "hash", correo = "ana@caja.pe", nombres = "Ana", apePat = "Lopez",
            fechaCreacion = ahora, fechaActualizacion = ahora)
        u.roles = mutableListOf(UsuarioRol(usuario = u, rol = Roles(id = 1, codigo = "USUARIO", nombre = "Usuario", activo = true,
            fechaCreacion = ahora, fechaActualizacion = ahora)))
        return u
    }

    @Test
    fun `se puede entrar con el correo`() {
        every { usuarios.findByUsuario("Ana@Caja.pe") } returns null
        every { usuarios.findByCorreoConRoles("Ana@Caja.pe") } returns cuenta()
        every { encoder.matches("Clave12345", "hash") } returns true
        every { jwt.generateToken(any()) } returns "token"

        val respuesta = service.login(LoginRequest(usuario = " Ana@Caja.pe ", clave = "Clave12345"))

        assertEquals("ch.ana", respuesta.usuario.usuario)
        assertEquals("token", respuesta.accessToken)
    }

    @Test
    fun `si no es un correo no se busca por correo`() {
        every { usuarios.findByUsuario("nadie") } returns null
        every { encoder.matches(any(), any()) } returns false
        assertFailsWith<BadCredentialsException> { service.login(LoginRequest(usuario = "nadie", clave = "x")) }
        verify(exactly = 0) { usuarios.findByCorreoConRoles(any()) }
    }
}
