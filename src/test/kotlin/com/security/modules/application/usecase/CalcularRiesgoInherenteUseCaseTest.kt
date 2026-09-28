package com.security.modules.matrices.application.usecase

import com.security.modules.matrices.application.dto.CalcularRiesgoInherenteRequest
import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CalcularRiesgoInherenteUseCaseTest {

    private val useCase =
        CalcularRiesgoInherenteUseCase()

    @Test
    fun `debe calcular riesgo inherente`() {

        val request =
            CalcularRiesgoInherenteRequest(
                probabilidad = NivelProbabilidad.ALTA,
                impactoEstimado = BigDecimal("500000")
            )

        val resultado =
            useCase.ejecutar(request)

        assertEquals(
            NivelProbabilidad.ALTA,
            resultado.probabilidad
        )

        /*
         * Nuevo rango:
         *
         * > 200,000 y <= 850,000
         * = MODERADO
         */
        assertEquals(
            NivelImpacto.MODERADO,
            resultado.impacto
        )

        /*
         * ALTA x MODERADO
         * = MODERADO
         */
        assertEquals(
            NivelRiesgo.MODERADO,
            resultado.riesgoInherente
        )
    }
}