package com.security.modules.matrices.application.dto

import com.security.modules.matrices.domain.enums.EstadoAnalisis
import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import java.math.BigDecimal
import java.time.LocalDateTime

data class MatrizRiesgoRegistroResponse(

    val id: Int,

    val estado: EstadoAnalisis,

    val impactoInherente: NivelImpacto?,

    val riesgoInherente: NivelRiesgo?,

    val mitigacion: BigDecimal?,

    val probabilidadResidual: NivelProbabilidad?,

    val impactoResidual: NivelImpacto?,

    val riesgoResidual: NivelRiesgo?,

    val fechaActualizacion: LocalDateTime
)