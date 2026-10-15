package com.security.modules.auth.dto
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank(message = "El usuario es obligatorio")
    @field:Size(max = 255, message = "El usuario no puede superar los 255 caracteres")
    val usuario: String,

    @field:NotBlank(message = "La contraseña es obligatoria")
    @field:Size(max = 128, message = "La contraseña no puede superar los 128 caracteres")
    val clave: String
)