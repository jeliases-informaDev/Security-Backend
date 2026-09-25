package com.security.modules.matrices.application.dto

import com.security.modules.matrices.domain.enums.NivelProbabilidad
import java.math.BigDecimal

data class CalcularRiesgoInherenteRequest(
    val probabilidad: NivelProbabilidad,
    val impactoEstimado: BigDecimal
)
