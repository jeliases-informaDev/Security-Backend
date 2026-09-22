package com.security.modules.auth

import com.security.modules.auth.application.service.PasswordResetService
import com.security.modules.auth.dto.ChangePasswordRequest
import com.security.modules.auth.dto.ForgotPasswordRequest
import com.security.modules.auth.dto.LoginRequest
import com.security.modules.auth.dto.LoginResponse
import com.security.modules.auth.dto.MessageResponse
import com.security.modules.auth.dto.ResetPasswordRequest
import com.security.modules.auth.dto.UsuarioLoginResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid


@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val passwordResetService: PasswordResetService
) {

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest   // ← el @Valid activa las anotaciones de arriba
    ): ResponseEntity<LoginResponse> {
        val resultado = authService.login(request)
        return ResponseEntity.ok(resultado)
    }

    @GetMapping("/me")
    fun me(authentication: Authentication): ResponseEntity<UsuarioLoginResponse> {
        val resultado = authService.me(authentication.name)
        return ResponseEntity.ok(resultado)
    }

    @PutMapping("/change-password")
    fun changePassword(
        authentication: Authentication,
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<MessageResponse> {
        authService.cambiarClave(authentication.name, request.claveActual, request.nuevaClave)
        return ResponseEntity.ok(MessageResponse("Contraseña actualizada correctamente"))
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest
    ): ResponseEntity<MessageResponse> {
        passwordResetService.solicitarRecuperacion(request.correo)
        // Mensaje genérico: no confirmamos ni negamos si el correo existe
        return ResponseEntity.ok(
            MessageResponse("Si el correo está registrado, se enviaron las instrucciones de recuperación")
        )
    }

    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @RequestBody request: ResetPasswordRequest
    ): ResponseEntity<MessageResponse> {
        passwordResetService.restablecerClave(request.token, request.nuevaClave)
        return ResponseEntity.ok(MessageResponse("Contraseña restablecida correctamente"))
    }
}