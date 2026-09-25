package com.security.modules.matrices.application.usecase

import com.security.modules.matrices.application.dto.CalcularRiesgoResidualRequest
import com.security.modules.matrices.application.dto.CalcularRiesgoResidualResponse
import com.security.modules.matrices.domain.service.MitigacionControlService
import com.security.modules.matrices.domain.service.RiesgoResidualService
import org.springframework.stereotype.Service

@Service
class CalcularRiesgoResidualUseCase(
    private val mitigacionControlService: MitigacionControlService =
        MitigacionControlService(),

    private val riesgoResidualService: RiesgoResidualService =
        RiesgoResidualService()
) {

    fun ejecutar(
        request: CalcularRiesgoResidualRequest
    ): CalcularRiesgoResidualResponse {

        val mitigacion =
            mitigacionControlService.calcular(
                supervision = request.supervision,
                tipoControl = request.tipoControl,
                operatividad = request.operatividad,
                periodicidad = request.periodicidad,
                frecuenciaOportuna = request.frecuenciaOportuna,
                seguimientoAdecuado = request.seguimientoAdecuado
            )

        val resultado =
            riesgoResidualService.calcular(
                probabilidadInherente = request.probabilidadInherente,
                impactoEstimado = request.impactoEstimado,
                tipoEmpresa = request.tipoEmpresa,
                mitigacion = mitigacion
            )

        return CalcularRiesgoResidualResponse(
            mitigacion = mitigacion,
            probabilidadResidual = resultado.probabilidadResidual,
            impactoResidual = resultado.impactoResidual,
            riesgoResidual = resultado.nivelRiesgoResidual
        )
    }
}
