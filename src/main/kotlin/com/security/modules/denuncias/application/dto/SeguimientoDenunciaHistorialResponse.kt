package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import java.time.LocalDateTime

data class SeguimientoDenunciaHistorialResponse(

    val estadoAnterior: EstadoDenuncia?,

    val estadoNuevo: EstadoDenuncia,

    val comentario: String?,

    val fechaCreacion: LocalDateTime
)