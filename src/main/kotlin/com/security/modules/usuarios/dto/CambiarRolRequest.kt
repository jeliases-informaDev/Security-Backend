package com.security.modules.usuarios.dto

import jakarta.validation.constraints.NotBlank

data class CambiarRolRequest(
    @field:NotBlank(message = "El rol es obligatorio")
    val rolCodigo: String
)
