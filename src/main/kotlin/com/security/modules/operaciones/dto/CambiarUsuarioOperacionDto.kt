package com.security.modules.operaciones.dto

import jakarta.validation.constraints.Positive

data class CambiarUsuarioOperacionRequest(@field:Positive val usuarioId: Int)

data class CambiarUsuarioOperacionResponse(
    val id: Long,
    val usuarioId: Int,
    val nombreCompleto: String
)
