package com.security.modules.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ResetPasswordRequest(
    @field:NotBlank(message = "El token es obligatorio")
    val token: String,

    @field:NotBlank(message = "La nueva contraseña es obligatoria")
    @field:Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    val nuevaClave: String
)
