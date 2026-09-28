package com.security.modules.scoring.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class ScoringResultResponse(
    val id: Int,
    val entidadId: Int,
    val nombreCompleto: String,
    val documento: String,
    val factores: List<FactorScoringResponse>,
    val puntajeTotal: BigDecimal,
    val categoria: String,
    val fechaCreacion: LocalDateTime
)
