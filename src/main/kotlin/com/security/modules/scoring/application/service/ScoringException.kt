package com.security.modules.scoring.application.service

import com.security.modules.scoring.dto.BloqueoResponse
import org.springframework.http.HttpStatus

/** Error de negocio del scoring con su codigo (p. ej. SCORING_DATOS_INCOMPLETOS) y, si aplica, que falta. */
class ScoringException(
    val status: HttpStatus,
    val code: String,
    message: String,
    val bloqueos: List<BloqueoResponse>? = null
) : RuntimeException(message)
