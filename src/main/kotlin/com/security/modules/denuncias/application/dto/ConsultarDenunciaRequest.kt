package com.security.modules.denuncias.application.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ConsultarDenunciaRequest(

    @field:NotBlank(
        message = "El identificador es obligatorio."
    )
    @field:Size(
        max = 30,
        message = "El identificador no es válido."
    )
    val codigoSeguimiento: String,

    @field:NotBlank(
        message = "El PIN es obligatorio."
    )
    @field:Size(
        max = 50,
        message = "El PIN no es válido."
    )
    val pin: String
)