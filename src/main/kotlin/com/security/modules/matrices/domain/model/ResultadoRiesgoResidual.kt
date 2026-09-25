package com.security.modules.matrices.domain.model

import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import java.math.BigDecimal

data class ResultadoRiesgoResidual(
    val mitigacion: BigDecimal,
    val probabilidadResidual: NivelProbabilidad,
    val impactoResidual: NivelImpacto,
    val nivelRiesgoResidual: NivelRiesgo
)