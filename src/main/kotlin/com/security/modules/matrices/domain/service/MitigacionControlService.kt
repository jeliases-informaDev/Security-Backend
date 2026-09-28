package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.parameters.MatrizRiesgoParametros
import com.security.modules.matrices.domain.enums.NivelSupervision
import com.security.modules.matrices.domain.enums.OperatividadControl
import com.security.modules.matrices.domain.enums.PeriodicidadControl
import com.security.modules.matrices.domain.enums.RespuestaControl
import com.security.modules.matrices.domain.enums.TipoControl
import java.math.BigDecimal
import java.math.RoundingMode

class MitigacionControlService {

    fun calcular(
        supervision: NivelSupervision,
        tipoControl: TipoControl,
        operatividad: OperatividadControl,
        periodicidad: PeriodicidadControl,
        frecuenciaOportuna: RespuestaControl,
        seguimientoAdecuado: RespuestaControl
    ): BigDecimal {

        /*
         * La supervisión se mantiene como dato del análisis
         * porque forma parte del formulario y de la trazabilidad.
         *
         * Sin embargo, la fórmula efectiva de Matriz_Riesgo.xlsx
         * no utiliza supervisión para calcular la mitigación.
         */
        @Suppress("UNUSED_VARIABLE")
        val supervisionRegistrada = supervision

        val valorPeriodicidad =
            MatrizRiesgoParametros.VALOR_PERIODICIDAD
                .getValue(periodicidad)

        val valorOperatividad =
            MatrizRiesgoParametros.VALOR_OPERATIVIDAD
                .getValue(operatividad)

        val valorTipoControl =
            MatrizRiesgoParametros.VALOR_TIPO_CONTROL
                .getValue(tipoControl)

        val valorFrecuencia =
            MatrizRiesgoParametros.VALOR_RESPUESTA
                .getValue(frecuenciaOportuna)

        val valorSeguimiento =
            MatrizRiesgoParametros.VALOR_RESPUESTA
                .getValue(seguimientoAdecuado)

        val diseno =
            valorPeriodicidad
                .multiply(
                    MatrizRiesgoParametros.PESO_PERIODICIDAD
                )
                .add(
                    valorOperatividad.multiply(
                        MatrizRiesgoParametros.PESO_OPERATIVIDAD
                    )
                )
                .add(
                    valorTipoControl.multiply(
                        MatrizRiesgoParametros.PESO_TIPO_CONTROL
                    )
                )

        val ejecucion =
            valorFrecuencia
                .multiply(
                    MatrizRiesgoParametros.PESO_FRECUENCIA_OPORTUNA
                )
                .add(
                    valorSeguimiento.multiply(
                        MatrizRiesgoParametros.PESO_SEGUIMIENTO_ADECUADO
                    )
                )

        return diseno
            .multiply(ejecucion)
            .setScale(
                3,
                RoundingMode.HALF_UP
            )
    }
}