package com.security.modules.matrices.application.dto

import com.security.modules.matrices.domain.enums.EstadoAnalisis
import com.security.modules.matrices.domain.enums.NivelRiesgo
import java.time.LocalDate
import java.time.LocalDateTime

data class MatrizRiesgoResumenResponse(

    val id: Int,
    val titulo: String?,
    val area: String?,
    val proceso: String?,
    val riesgoInherente: NivelRiesgo?,
    val riesgoResidual: NivelRiesgo?,
    val estado: EstadoAnalisis,
    val fechaCreacion: LocalDateTime,
    val fechaCierre: LocalDate?
)