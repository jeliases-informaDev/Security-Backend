package com.security.modules.matrices.application.dto

import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo

data class HeatmapMatrizResponse(
    val probabilidades: List<HeatmapProbabilidadResponse>,
    val impactos: List<HeatmapImpactoResponse>,
    val celdas: List<HeatmapCeldaResponse>
)

data class HeatmapProbabilidadResponse(
    val codigo: NivelProbabilidad,
    val nivel: Int,
    val descripcion: String
)

data class HeatmapImpactoResponse(
    val codigo: NivelImpacto,
    val nivel: Int
)

data class HeatmapCeldaResponse(
    val probabilidad: NivelProbabilidad,
    val impacto: NivelImpacto,
    val riesgo: NivelRiesgo
)