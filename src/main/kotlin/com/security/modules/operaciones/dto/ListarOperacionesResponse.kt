package com.security.modules.operaciones.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ListarOperacionesResponse(
    val content: List<OperacionItemDto>,
    val totalPages: Int,
    val totalElements: Long,
    val currentPage: Int,
    val size: Int = 10
)

data class OperacionItemDto(
    // ID de registro_operaciones, utilizado para las acciones de la lista.
    val id: Long,
    val numeroRegistro: String?,
    val personaFisica: String?,
    val personaNombre: String?,
    val oficina: String?,
    // Fecha de registro; se conserva el nombre JSON usado por el frontend.
    val fecha: LocalDate?,
    val responsable: UsuarioAsignableResponse? = null,
    val creadoEn: LocalDateTime? = null,
    val fechaOperacion: LocalDate? = null,
    val tipoOperacion: String? = null,
    val tipoOperacionNombre: String? = null,
    val modalidadPago: String? = null,
    val modalidadPagoNombre: String? = null,
    val monto: BigDecimal? = null,
    val moneda: String? = null,
    val monedaCodigo: String? = null,
    val monedaSimbolo: String? = null,
    val estado: String = "PENDIENTE"
)
