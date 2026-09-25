package com.security.modules.matrices.application.dto

import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import java.math.BigDecimal

data class CalcularRiesgoResidualResponse(
    val mitigacion: BigDecimal,
    val probabilidadResidual: NivelProbabilidad,
    val impactoResidual: NivelImpacto,
    val riesgoResidual: NivelRiesgo
)

