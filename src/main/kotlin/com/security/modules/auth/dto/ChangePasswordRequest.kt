package com.security.modules.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank(message = "La contraseña actual es obligatoria")
    val claveActual: String,

    @field:NotBlank(message = "La nueva contraseña es obligatoria")
    @field:Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    val nuevaClave: String
)
