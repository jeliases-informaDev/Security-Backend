package com.security.modules.auth

import com.security.modules.auth.dto.LoginRequest
import com.security.modules.auth.dto.LoginResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid


@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest   // ← el @Valid activa las anotaciones de arriba
    ): ResponseEntity<LoginResponse> {
        val resultado = authService.login(request)
        return ResponseEntity.ok(resultado)
    }
}