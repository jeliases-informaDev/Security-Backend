package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.RolIncidente
import com.security.modules.denuncias.domain.enums.TipoPersonaInvolucrada
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CrearInvolucradoRequest(

    @field:NotNull(
        message = "El tipo de persona es obligatorio"
    )
    val tipoPersona: TipoPersonaInvolucrada? = null,

    @field:NotNull(
        message = "El rol en el incidente es obligatorio"
    )
    val rolIncidente: RolIncidente? = null,

    val documento: String? = null,

    @field:NotBlank(
        message = "El nombre o razón social es obligatorio"
    )
    val nombreRazonSocial: String? = null,

    @field:NotBlank(
        message = "El cargo es obligatorio"
    )
    val cargo: String? = null
)