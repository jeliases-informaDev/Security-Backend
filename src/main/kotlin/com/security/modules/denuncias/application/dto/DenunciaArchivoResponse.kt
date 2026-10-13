package com.security.modules.denuncias.application.dto

import java.time.LocalDateTime

data class DenunciaArchivoResponse(

    val id: Int,

    val nombreOriginal: String,

    val tipoContenido: String?,

    val tamanoBytes: Long?,

    val fechaCreacion: LocalDateTime
)