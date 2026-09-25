package com.security.modules.matrices.application.usecase

import com.security.modules.matrices.application.dto.CalcularRiesgoInherenteRequest
import com.security.modules.matrices.application.dto.CalcularRiesgoInherenteResponse
import com.security.modules.matrices.domain.service.ClasificadorImpactoService
import com.security.modules.matrices.domain.service.RiesgoInherenteService
import org.springframework.stereotype.Service

@Service
class CalcularRiesgoInherenteUseCase(
    private val clasificadorImpactoService: ClasificadorImpactoService =
        ClasificadorImpactoService(),

    private val riesgoInherenteService: RiesgoInherenteService =
        RiesgoInherenteService()
) {

    fun ejecutar(
        request: CalcularRiesgoInherenteRequest
    ): CalcularRiesgoInherenteResponse {

        val impacto =
            clasificadorImpactoService.clasificar(
                request.impactoEstimado
            )

        val riesgo =
            riesgoInherenteService.calcular(
                probabilidad = request.probabilidad,
                impacto = impacto
            )

        return CalcularRiesgoInherenteResponse(
            probabilidad = request.probabilidad,
            impactoEstimado = request.impactoEstimado,
            impacto = impacto,
            riesgoInherente = riesgo
        )
    }
}