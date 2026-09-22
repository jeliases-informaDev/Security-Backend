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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder

class AuthServiceTest {

    private lateinit var usuarioRepository: UsuarioRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtService: JwtService
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        usuarioRepository = mockk()
        passwordEncoder = mockk()
        jwtService = mockk()

        authService = AuthService(
            usuarioRepository,
            passwordEncoder,
            jwtService
        )
    }

    @Test
    fun `login falla cuando el usuario no existe`() {

        val request = LoginRequest(
            usuario = "noexiste",
            clave = "1234"
        )

        every {
            usuarioRepository.findByUsuario("noexiste")
        } returns null

        // El AuthService hace esta validación BCrypt
        // incluso cuando el usuario no existe.
        every {
            passwordEncoder.matches(any(), any())
        } returns false

        assertThrows(BadCredentialsException::class.java) {
            authService.login(request)
        }

        verify(exactly = 0) {
            jwtService.generateToken(any())
        }
    }

    @Test
    fun `login falla cuando la contraseña es incorrecta`() {

        val request = LoginRequest(
            usuario = "jperez",
            clave = "incorrecta"
        )

        val usuarioMock = mockk<Usuario>()

        every {
            usuarioRepository.findByUsuario("jperez")
        } returns usuarioMock

        every {
            usuarioMock.clave
        } returns "hashEncriptado"

        every {
            passwordEncoder.matches(
                "incorrecta",
                "hashEncriptado"
            )
        } returns false

        assertThrows(BadCredentialsException::class.java) {
            authService.login(request)
        }

        verify(exactly = 0) {
            jwtService.generateToken(any())
        }
    }

    @Test
    fun `login falla cuando el usuario esta inactivo`() {

        val request = LoginRequest(
            usuario = "jperez",
            clave = "1234"
        )

        val usuarioMock = mockk<Usuario>()

        every {
            usuarioRepository.findByUsuario("jperez")
        } returns usuarioMock

        every {
            usuarioMock.clave
        } returns "hashEncriptado"

        every {
            passwordEncoder.matches(
                "1234",
                "hashEncriptado"
            )
        } returns true

        every {
            usuarioMock.activo
        } returns false

        assertThrows(BadCredentialsException::class.java) {
            authService.login(request)
        }

        verify(exactly = 0) {
            jwtService.generateToken(any())
        }
    }

    @Test
    fun `login exitoso genera token`() {

        val request = LoginRequest(
            usuario = "lavado_pru",
            clave = "lavado_demo1"
        )

        val usuarioMock = mockk<Usuario>()
        val usuarioRolMock = mockk<UsuarioRol>()
        val rolesMock = mockk<Roles>()

        // Usuario encontrado
        every {
            usuarioRepository.findByUsuario("lavado_pru")
        } returns usuarioMock

        // Contraseña almacenada
        every {
            usuarioMock.clave
        } returns "hashEncriptado"

        // Contraseña correcta
        every {
            passwordEncoder.matches(
                "lavado_demo1",
                "hashEncriptado"
            )
        } returns true

        // Usuario activo
        every {
            usuarioMock.activo
        } returns true

        // Datos del usuario para UsuarioLoginResponse
        every {
            usuarioMock.nombres
        } returns "Juan"

        every {
            usuarioMock.apePat
        } returns "Perez"

        every {
            usuarioMock.apeMat
        } returns "Gomez"

        every {
            usuarioMock.usuario
        } returns "lavado_pru"

        // Usuario -> UsuarioRol
        every {
            usuarioMock.roles
        } returns mutableListOf(usuarioRolMock)

        // UsuarioRol -> Roles
        every {
            usuarioRolMock.rol
        } returns rolesMock

        // Datos del rol
        every {
            rolesMock.codigo
        } returns "ADMINISTRADOR"

        every {
            rolesMock.nombre
        } returns "Administrador"

        // JWT
        every {
            jwtService.generateToken(usuarioMock)
        } returns "jwt-token"

        // Ejecutar login
        val response = authService.login(request)

        // Validar respuesta
        assertEquals(
            "jwt-token",
            response.accessToken
        )

        assertEquals(
            "Bearer",
            response.tokenType
        )

        assertEquals(
            900,
            response.expiresIn
        )

        assertEquals(
            "Juan Perez Gomez",
            response.usuario.nombreCompleto
        )

        assertEquals(
            "lavado_pru",
            response.usuario.usuario
        )

        assertEquals(
            "ADMINISTRADOR",
            response.usuario.codigo
        )

        assertEquals(
            "Administrador",
            response.usuario.rol
        )

        // Verificar que se generó exactamente un token
        verify(exactly = 1) {
            jwtService.generateToken(usuarioMock)
        }
    }
}