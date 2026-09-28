package com.security.modules.matrices.application.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CrearCatalogoMatrizRequest(

    @field:NotBlank(
        message = "El nombre es obligatorio"
    )
    @field:Size(
        max = 250,
        message = "El nombre no puede superar los 250 caracteres"
    )
    val nombre: String
)