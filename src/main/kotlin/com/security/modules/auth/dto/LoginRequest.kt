package com.security.modules.auth.dto
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank(message = "El usuario es obligatorio")
    @field:Size(min = 8, max = 12, message = "El usuario debe tener entre 8 y 12 caracteres")
    val usuario: String,

    @field:NotBlank(message = "La contraseña es obligatoria")
    @field:Size(min = 8, max = 12, message = "La contraseña debe tener entre 8 y 12 caracteres")
    val clave: String
)