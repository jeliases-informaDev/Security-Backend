package com.security.modules.usuarios.dto

import jakarta.validation.constraints.NotBlank

data class CambiarEmpresaRequest(
    @field:NotBlank(message = "La empresa es obligatoria")
    val empresa: String
)
