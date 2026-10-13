package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import jakarta.validation.constraints.NotNull

data class ActualizarEstadoDenunciaRequest(

    @field:NotNull(
        message = "El nuevo estado es obligatorio"
    )
    val estado: EstadoDenuncia? = null,

    val comentario: String? = null
)