package com.security.modules.matrices.application.usecase

import com.security.modules.matrices.application.dto.CalcularMatrizRiesgoRequest
import com.security.modules.matrices.application.dto.CalcularMatrizRiesgoResponse
import com.security.modules.matrices.domain.service.ClasificadorImpactoService
import com.security.modules.matrices.domain.service.MitigacionControlService
import com.security.modules.matrices.domain.service.RiesgoInherenteService
import com.security.modules.matrices.domain.service.RiesgoResidualService

class CalcularMatrizRiesgoUseCase(
    private val clasificadorImpactoService: ClasificadorImpactoService =
        ClasificadorImpactoService(),

    private val riesgoInherenteService: RiesgoInherenteService =
        RiesgoInherenteService(),

    private val mitigacionControlService: MitigacionControlService =
        MitigacionControlService(),

    private val riesgoResidualService: RiesgoResidualService =
        RiesgoResidualService()
) {

    fun ejecutar(
        request: CalcularMatrizRiesgoRequest
    ): CalcularMatrizRiesgoResponse {

        val impactoInherente =
            clasificadorImpactoService.clasificar(
                request.impactoEstimado
            )

        val riesgoInherente =
            riesgoInherenteService.calcular(
                probabilidad = request.probabilidad,
                impacto = impactoInherente
            )

        val mitigacion =
            mitigacionControlService.calcular(
                supervision = request.supervision,
                tipoControl = request.tipoControl,
                operatividad = request.operatividad,
                periodicidad = request.periodicidad,
                frecuenciaOportuna = request.frecuenciaOportuna,
                seguimientoAdecuado = request.seguimientoAdecuado
            )

        val resultadoResidual =
            riesgoResidualService.calcular(
                probabilidadInherente = request.probabilidad,
                impactoEstimado = request.impactoEstimado,
                tipoEmpresa = request.tipoEmpresa,
                mitigacion = mitigacion
            )

        return CalcularMatrizRiesgoResponse(
            impactoInherente = impactoInherente,
            riesgoInherente = riesgoInherente,
            mitigacion = mitigacion,
            probabilidadResidual =
                resultadoResidual.probabilidadResidual,
            impactoResidual =
                resultadoResidual.impactoResidual,
            riesgoResidual =
                resultadoResidual.nivelRiesgoResidual
        )
    }
}