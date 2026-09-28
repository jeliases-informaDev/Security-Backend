package com.security.modules.matrices.application.service

import com.security.modules.matrices.application.dto.HeatmapCeldaResponse
import com.security.modules.matrices.application.dto.HeatmapImpactoResponse
import com.security.modules.matrices.application.dto.HeatmapMatrizResponse
import com.security.modules.matrices.application.dto.HeatmapProbabilidadResponse
import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.service.RiesgoInherenteService
import org.springframework.stereotype.Service

@Service
class HeatmapMatrizService(
    private val riesgoInherenteService: RiesgoInherenteService
) {

    fun obtenerHeatmap(): HeatmapMatrizResponse {

        val probabilidades =
            NivelProbabilidad.entries
                .sortedByDescending { it.nivel }

        val impactos =
            NivelImpacto.entries
                .sortedBy { it.nivel }

        val celdas =
            probabilidades.flatMap { probabilidad ->

                impactos.map { impacto ->

                    HeatmapCeldaResponse(
                        probabilidad = probabilidad,
                        impacto = impacto,
                        riesgo = riesgoInherenteService.calcular(
                            probabilidad,
                            impacto
                        )
                    )
                }
            }

        return HeatmapMatrizResponse(

            probabilidades =
                probabilidades.map {
                    HeatmapProbabilidadResponse(
                        codigo = it,
                        nivel = it.nivel,
                        descripcion = it.descripcion
                    )
                },

            impactos =
                impactos.map {
                    HeatmapImpactoResponse(
                        codigo = it,
                        nivel = it.nivel
                    )
                },

            celdas = celdas
        )
    }
}