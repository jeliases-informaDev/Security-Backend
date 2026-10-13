package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import java.time.LocalDateTime

data class DenunciaHistorialResponse(

    val id: Int,

    val estadoAnterior: EstadoDenuncia?,

    val estadoNuevo: EstadoDenuncia,

    val comentario: String?,

    val usuarioId: Int?,

    val usuario: String?,

    val fechaCreacion: LocalDateTime
)