package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.parameters.MatrizRiesgoParametros
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.TipoEmpresa
import com.security.modules.matrices.domain.model.ResultadoRiesgoResidual
import java.math.BigDecimal
import java.math.RoundingMode

class RiesgoResidualService(
    private val clasificadorImpactoService: ClasificadorImpactoService =
        ClasificadorImpactoService(),

    private val riesgoInherenteService: RiesgoInherenteService =
        RiesgoInherenteService()
) {

    fun calcular(
        probabilidadInherente: NivelProbabilidad,
        impactoEstimado: BigDecimal,
        tipoEmpresa: TipoEmpresa,
        mitigacion: BigDecimal
    ): ResultadoRiesgoResidual {

        require(
            mitigacion >= BigDecimal.ZERO &&
                    mitigacion <= BigDecimal.ONE
        ) {
            "La mitigación debe estar entre 0 y 1"
        }

        require(impactoEstimado >= BigDecimal.ZERO) {
            "El impacto estimado no puede ser negativo"
        }

        /*
         * Se conserva tipoEmpresa en la firma porque forma
         * parte del modelo y del formulario actual.
         *
         * Los Excel revisados no definen umbrales de impacto
         * distintos por tamaño de empresa, por lo que no se
         * utilizará para alterar el cálculo hasta disponer
         * de una regla documental que lo sustente.
         */
        @Suppress("UNUSED_VARIABLE")
        val tipoEmpresaRegistrado = tipoEmpresa

        val factorResidual =
            BigDecimal.ONE.subtract(mitigacion)

        // ==============================
        // PROBABILIDAD RESIDUAL
        // ==============================

        val valorProbabilidadInherente =
            MatrizRiesgoParametros.VALOR_PROBABILIDAD
                .getValue(probabilidadInherente)

        val valorProbabilidadResidual =
            valorProbabilidadInherente
                .multiply(factorResidual)
                .setScale(
                    6,
                    RoundingMode.HALF_UP
                )

        val probabilidadResidual =
            clasificarProbabilidadResidual(
                valorProbabilidadResidual
            )

        // ==============================
        // IMPACTO RESIDUAL
        // ==============================

        val montoImpactoResidual =
            impactoEstimado
                .multiply(factorResidual)
                .setScale(
                    2,
                    RoundingMode.HALF_UP
                )

        val impactoResidual =
            clasificadorImpactoService.clasificar(
                montoImpactoResidual
            )

        // ==============================
        // NIVEL DE RIESGO RESIDUAL
        // ==============================

        val nivelRiesgoResidual =
            riesgoInherenteService.calcular(
                probabilidadResidual,
                impactoResidual
            )

        return ResultadoRiesgoResidual(
            mitigacion = mitigacion,
            probabilidadResidual = probabilidadResidual,
            impactoResidual = impactoResidual,
            nivelRiesgoResidual = nivelRiesgoResidual
        )
    }

    private fun clasificarProbabilidadResidual(
        valor: BigDecimal
    ): NivelProbabilidad {

        return MatrizRiesgoParametros
            .RANGOS_PROBABILIDAD_RESIDUAL
            .first { rango ->
                valor <= rango.limiteSuperior
            }
            .nivel
    }
}