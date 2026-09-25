package com.security.modules.matrices.application.usecase

import com.security.modules.matrices.application.dto.CalcularRiesgoResidualRequest
import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import com.security.modules.matrices.domain.enums.NivelSupervision
import com.security.modules.matrices.domain.enums.OperatividadControl
import com.security.modules.matrices.domain.enums.PeriodicidadControl
import com.security.modules.matrices.domain.enums.RespuestaControl
import com.security.modules.matrices.domain.enums.TipoControl
import com.security.modules.matrices.domain.enums.TipoEmpresa
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CalcularRiesgoResidualUseCaseTest {

    private val useCase = CalcularRiesgoResidualUseCase()

    @Test
    fun `debe calcular riesgo residual`() {

        val request = CalcularRiesgoResidualRequest(
            tipoEmpresa = TipoEmpresa.GRAN_EMPRESA,
            probabilidadInherente = NivelProbabilidad.ALTA,
            impactoEstimado = BigDecimal("500000"),

            supervision = NivelSupervision.DIRECTIVO_AUTOMATICO,
            tipoControl = TipoControl.PREVENTIVO,
            operatividad = OperatividadControl.AUTOMATICO,
            periodicidad = PeriodicidadControl.PERMANENTE,
            frecuenciaOportuna = RespuestaControl.SI,
            seguimientoAdecuado = RespuestaControl.SI
        )

        val resultado = useCase.ejecutar(request)

        assertEquals(
            BigDecimal("0.800"),
            resultado.mitigacion
        )

        assertEquals(
            NivelProbabilidad.MEDIA,
            resultado.probabilidadResidual
        )

        assertEquals(
            NivelImpacto.MENOR,
            resultado.impactoResidual
        )

        assertEquals(
            NivelRiesgo.LEVE,
            resultado.riesgoResidual
        )
    }
}
