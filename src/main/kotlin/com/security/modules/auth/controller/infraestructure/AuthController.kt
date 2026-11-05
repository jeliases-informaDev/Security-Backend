package com.security.modules.auth

import com.security.modules.auth.application.service.PasswordResetService
import com.security.modules.auth.dto.ChangePasswordRequest
import com.security.modules.auth.dto.ForgotPasswordRequest
import com.security.modules.auth.dto.LoginRequest
import com.security.modules.auth.dto.LoginResponse
import com.security.modules.auth.dto.MessageResponse
import com.security.modules.auth.dto.ResetPasswordRequest
import com.security.modules.auth.dto.UsuarioLoginResponse
import com.security.modules.auth.sso.SsoConfigResponse
import com.security.modules.auth.sso.SsoLoginRequest
import com.security.modules.auth.sso.SsoService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid


@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val passwordResetService: PasswordResetService,
    private val ssoService: SsoService
) {

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest   // ← el @Valid activa las anotaciones de arriba
    ): ResponseEntity<LoginResponse> {
        val resultado = authService.login(request)
        return ResponseEntity.ok(resultado)
    }

    /** Público: si el inicio único con la cuenta Sulotec está encendido y con qué datos va el front a Sulotec. */
    @GetMapping("/sso/config")
    fun ssoConfig(): ResponseEntity<SsoConfigResponse> = ResponseEntity.ok(ssoService.config())

    /** Público: canjea el código de Sulotec y responde exactamente lo mismo que /login. */
    @PostMapping("/sso")
    fun sso(@RequestBody(required = false) request: SsoLoginRequest?): ResponseEntity<LoginResponse> =
        ResponseEntity.ok(ssoService.entrar(request ?: SsoLoginRequest()))

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