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

    private val service =
        RiesgoResidualService()

    @Test
    fun `riesgo alto con mitigacion alta debe reducir probabilidad`() {

        val resultado =
            service.calcular(
                probabilidadInherente =
                    NivelProbabilidad.ALTA,

                impactoEstimado =
                    BigDecimal("500000"),

                tipoEmpresa =
                    TipoEmpresa.GRAN_EMPRESA,

                mitigacion =
                    BigDecimal("0.800")
            )

        /*
         * ALTA = 0.699
         *
         * 0.699 * 0.20
         * = 0.1398
         *
         * = MEDIA
         */
        assertEquals(
            NivelProbabilidad.MEDIA,
            resultado.probabilidadResidual
        )
    }

    @Test
    fun `tipo empresa no debe modificar clasificacion del impacto residual`() {

        val resultado =
            service.calcular(
                probabilidadInherente =
                    NivelProbabilidad.ALTA,

                impactoEstimado =
                    BigDecimal("500000"),

                tipoEmpresa =
                    TipoEmpresa.MICROEMPRESA,

                mitigacion =
                    BigDecimal("0.800")
            )

        /*
         * 500,000 * 0.20
         * = 100,000
         *
         * 100,000 pertenece a MENOR.
         *
         * TipoEmpresa no modifica esta clasificación.
         */
        assertEquals(
            NivelImpacto.MENOR,
            resultado.impactoResidual
        )
    }

    @Test
    fun `debe devolver nivel de riesgo residual`() {

        val resultado =
            service.calcular(
                probabilidadInherente =
                    NivelProbabilidad.ALTA,

                impactoEstimado =
                    BigDecimal("500000"),

                tipoEmpresa =
                    TipoEmpresa.GRAN_EMPRESA,

                mitigacion =
                    BigDecimal("0.800")
            )

        /*
         * Probabilidad residual = MEDIA
         * Impacto residual = MENOR
         *
         * MEDIA x MENOR = LEVE
         */
        assertEquals(
            NivelRiesgo.LEVE,
            resultado.nivelRiesgoResidual
        )
    }

    @Test
    fun `mitigacion de 0600 debe calcular residual con parametros reales`() {

        val resultado =
            service.calcular(
                probabilidadInherente =
                    NivelProbabilidad.ALTA,

                impactoEstimado =
                    BigDecimal("500000"),

                tipoEmpresa =
                    TipoEmpresa.GRAN_EMPRESA,

                mitigacion =
                    BigDecimal("0.600")
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
            resultado.nivelRiesgoResidual
        )
    }

    @Test
    fun `mitigacion mayor a uno debe generar error`() {

        assertThrows(
            IllegalArgumentException::class.java
        ) {

            service.calcular(
                probabilidadInherente =
                    NivelProbabilidad.ALTA,

                impactoEstimado =
                    BigDecimal("100000"),

                tipoEmpresa =
                    TipoEmpresa.MICROEMPRESA,

                mitigacion =
                    BigDecimal("1.10")
            )
        }
    }

    @Test
    fun `mitigacion negativa debe generar error`() {

        assertThrows(
            IllegalArgumentException::class.java
        ) {

            service.calcular(
                probabilidadInherente =
                    NivelProbabilidad.ALTA,

                impactoEstimado =
                    BigDecimal("100000"),

                tipoEmpresa =
                    TipoEmpresa.GRAN_EMPRESA,

                mitigacion =
                    BigDecimal("-0.10")
            )
        }
    }
}