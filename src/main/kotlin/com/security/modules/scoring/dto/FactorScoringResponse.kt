package com.security.modules.scoring.dto

import java.math.BigDecimal

data class FactorScoringResponse(
    val nombre: String,
    val valor: String,
    val puntaje: Int,
    val peso: Int,
    val puntajePonderado: BigDecimal
)
