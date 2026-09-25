package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import com.security.modules.matrices.domain.enums.TipoEmpresa
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RiesgoResidualServiceTest {

    private val service = RiesgoResidualService()

    @Test
    fun `riesgo alto con mitigacion alta debe reducir probabilidad`() {

        val resultado = service.calcular(
            probabilidadInherente = NivelProbabilidad.ALTA,
            impactoEstimado = BigDecimal("500000"),
            tipoEmpresa = TipoEmpresa.GRAN_EMPRESA,
            mitigacion = BigDecimal("0.800")
        )

        assertEquals(
            NivelProbabilidad.MEDIA,
            resultado.probabilidadResidual
        )
    }

    @Test
    fun `microempresa debe clasificar impacto residual`() {

        val resultado = service.calcular(
            probabilidadInherente = NivelProbabilidad.ALTA,
            impactoEstimado = BigDecimal("500000"),
            tipoEmpresa = TipoEmpresa.MICROEMPRESA,
            mitigacion = BigDecimal("0.800")
        )

        assertEquals(
            NivelImpacto.MAYOR,
            resultado.impactoResidual
        )
    }

    @Test
    fun `debe devolver nivel de riesgo residual`() {

        val resultado = service.calcular(
            probabilidadInherente = NivelProbabilidad.ALTA,
            impactoEstimado = BigDecimal("500000"),
            tipoEmpresa = TipoEmpresa.GRAN_EMPRESA,
            mitigacion = BigDecimal("0.800")
        )

        assertEquals(
            NivelRiesgo.LEVE,
            resultado.nivelRiesgoResidual
        )
    }

    @Test
    fun `mitigacion mayor a uno debe generar error`() {

        assertThrows(IllegalArgumentException::class.java) {
            service.calcular(
                probabilidadInherente = NivelProbabilidad.ALTA,
                impactoEstimado = BigDecimal("100000"),
                tipoEmpresa = TipoEmpresa.MICROEMPRESA,
                mitigacion = BigDecimal("1.10")
            )
        }
    }
}