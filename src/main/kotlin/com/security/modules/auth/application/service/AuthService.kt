package com.security.modules.auth

import com.security.modules.auth.dto.LoginRequest
import com.security.modules.auth.dto.LoginResponse
import com.security.modules.auth.dto.UsuarioLoginResponse
import com.security.modules.auth.security.JwtService
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    ) {

    fun login(request: LoginRequest): LoginResponse {

        if (request.usuario.isBlank() || request.clave.isBlank()) {
            throw BadCredentialsException("Usuario o contraseña incorrectos")
        }

        val usuario = usuarioRepository.findByUsuario(request.usuario)

        // Ejecuta el matches() aunque el usuario no exista, con un hash dummy,
        // para no filtrar por tiempo de respuesta si el usuario existe o no
        val claveValida = if (usuario != null) {
            passwordEncoder.matches(request.clave, usuario.clave)
        } else {
            passwordEncoder.matches(request.clave, DUMMY_HASH)
            false
        }

        if (usuario == null || !claveValida) {
            throw BadCredentialsException("Usuario o contraseña incorrectos")
        }

        if (!usuario.activo) {
            throw BadCredentialsException("Usuario o contraseña incorrectos")
        }

        val token = jwtService.generateToken(usuario)

        val rol = usuario.roles.firstOrNull()?.rol
            ?: throw BadCredentialsException("El usuario no tiene un rol asignado")

        return LoginResponse(
            accessToken = token,
            tokenType = "Bearer",
            expiresIn = 900,
            usuario = UsuarioLoginResponse(
                nombreCompleto = listOfNotNull(usuario.nombres, usuario.apePat, usuario.apeMat)
                    .joinToString(" "),
                usuario = usuario.usuario,
                codigo = rol.codigo,
                rol = rol.nombre
            )
        )
    }

    companion object {
        // Hash BCrypt de un valor cualquiera, generado una sola vez, solo para
        // igualar el tiempo de respuesta cuando el usuario no existe
        private const val DUMMY_HASH = "\$2a\$10\$7EqJtq98hPqEX7fNZaFWoOa8jXW1e9EYJmxb6M6MCd6Y8FUiJ8CmC"
    }

}