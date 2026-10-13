package com.security.modules.operaciones.dto

import java.time.LocalDate

data class ListarOperacionesResponse(
    val content: List<OperacionItemDto>,
    val totalPages: Int,
    val totalElements: Long,
    val currentPage: Int
)

data class OperacionItemDto(
    // ID de registro_operaciones, utilizado para las acciones de la lista.
    val id: Long,
    val numeroRegistro: String?,
    val personaFisica: String?,
    val personaNombre: String?,
    val oficina: String?,
    // Fecha de registro; se conserva el nombre JSON usado por el frontend.
    val fecha: LocalDate?
)
