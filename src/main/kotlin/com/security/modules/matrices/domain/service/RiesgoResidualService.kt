package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.TipoEmpresa
import com.security.modules.matrices.domain.model.ResultadoRiesgoResidual
import java.math.BigDecimal
import java.math.RoundingMode

class RiesgoResidualService(
    private val riesgoService: RiesgoInherenteService = RiesgoInherenteService()
) {

    fun calcular(
        probabilidadInherente: NivelProbabilidad,
        impactoEstimado: BigDecimal,
        tipoEmpresa: TipoEmpresa,
        mitigacion: BigDecimal
    ): ResultadoRiesgoResidual {

        require(impactoEstimado >= BigDecimal.ZERO) {
            "El impacto estimado no puede ser negativo"
        }

        require(
            mitigacion >= BigDecimal.ZERO &&
                    mitigacion <= BigDecimal.ONE
        ) {
            "La mitigación debe estar entre 0 y 1"
        }

        val factorResidual = BigDecimal.ONE.subtract(mitigacion)

        val probabilidadResidual = calcularProbabilidadResidual(
            probabilidadInherente,
            factorResidual
        )

        val impactoResidual = calcularImpactoResidual(
            impactoEstimado,
            tipoEmpresa,
            factorResidual
        )

        val nivelResidual = riesgoService.calcular(
            probabilidadResidual,
            impactoResidual
        )

        return ResultadoRiesgoResidual(
            mitigacion = mitigacion,
            probabilidadResidual = probabilidadResidual,
            impactoResidual = impactoResidual,
            nivelRiesgoResidual = nivelResidual
        )
    }

    private fun calcularProbabilidadResidual(
        probabilidadInherente: NivelProbabilidad,
        factorResidual: BigDecimal
    ): NivelProbabilidad {

        val valorBase = when (probabilidadInherente) {
            NivelProbabilidad.MUY_ALTA -> BigDecimal("100.00")
            NivelProbabilidad.ALTA -> BigDecimal("99.90")
            NivelProbabilidad.MEDIA -> BigDecimal("29.99")
            NivelProbabilidad.BAJA -> BigDecimal("9.99")
            NivelProbabilidad.MUY_BAJA -> BigDecimal("4.99")
        }

        val resultado = factorResidual.multiply(valorBase)

        return when {
            resultado >= BigDecimal("99.91") ->
                NivelProbabilidad.MUY_ALTA

            resultado >= BigDecimal("30.00") ->
                NivelProbabilidad.ALTA

            resultado >= BigDecimal("10.00") ->
                NivelProbabilidad.MEDIA

            resultado >= BigDecimal("5.00") ->
                NivelProbabilidad.BAJA

            else ->
                NivelProbabilidad.MUY_BAJA
        }
    }

    private fun calcularImpactoResidual(
        impactoEstimado: BigDecimal,
        tipoEmpresa: TipoEmpresa,
        factorResidual: BigDecimal
    ): NivelImpacto {

        val impactoNormalizado = factorResidual
            .multiply(impactoEstimado)
            .divide(
                BigDecimal("4600"),
                6,
                RoundingMode.HALF_UP
            )

        return when (tipoEmpresa) {

            TipoEmpresa.MICROEMPRESA ->
                clasificarImpacto(
                    impactoNormalizado,
                    BigDecimal("1"),
                    BigDecimal("10"),
                    BigDecimal("20"),
                    BigDecimal("40")
                )

            TipoEmpresa.PEQUENA_EMPRESA ->
                clasificarImpacto(
                    impactoNormalizado,
                    BigDecimal("1"),
                    BigDecimal("50"),
                    BigDecimal("100"),
                    BigDecimal("200")
                )

            TipoEmpresa.MEDIANA_EMPRESA,
            TipoEmpresa.GRAN_EMPRESA ->
                clasificarImpacto(
                    impactoNormalizado,
                    BigDecimal("1"),
                    BigDecimal("250"),
                    BigDecimal("300"),
                    BigDecimal("450")
                )
        }
    }

    private fun clasificarImpacto(
        valor: BigDecimal,
        limiteMenor: BigDecimal,
        limiteModerado: BigDecimal,
        limiteMayor: BigDecimal,
        limiteCatastrofico: BigDecimal
    ): NivelImpacto {

        return when {
            valor <= limiteMenor ->
                NivelImpacto.INSIGNIFICANTE

            valor <= limiteModerado ->
                NivelImpacto.MENOR

            valor <= limiteMayor ->
                NivelImpacto.MODERADO

            valor <= limiteCatastrofico ->
                NivelImpacto.MAYOR

            else ->
                NivelImpacto.CATASTROFICO
        }
    }
}