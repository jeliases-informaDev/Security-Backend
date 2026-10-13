package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.RolIncidente
import com.security.modules.denuncias.domain.enums.TipoPersonaInvolucrada
import java.time.LocalDateTime

data class DenunciaInvolucradoResponse(

    val id: Int,

    val tipoPersona: TipoPersonaInvolucrada,

    val rolIncidente: RolIncidente,

    val documento: String?,

    val nombreRazonSocial: String,

    val cargo: String,

    val fechaCreacion: LocalDateTime
)