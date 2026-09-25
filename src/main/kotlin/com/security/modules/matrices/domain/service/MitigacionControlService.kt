package com.security.modules.matrices.domain.service

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

        var resultado = obtenerValorBase(
            supervision = supervision,
            tipoControl = tipoControl,
            operatividad = operatividad,
            periodicidad = periodicidad
        )

        if (frecuenciaOportuna == RespuestaControl.NO) {
            resultado = resultado.divide(
                BigDecimal("2"),
                6,
                RoundingMode.HALF_UP
            )
        }

        if (seguimientoAdecuado == RespuestaControl.NO) {
            resultado = resultado.divide(
                BigDecimal("2"),
                6,
                RoundingMode.HALF_UP
            )
        }

        return resultado.setScale(
            3,
            RoundingMode.HALF_UP
        )
    }

    private fun obtenerValorBase(
        supervision: NivelSupervision,
        tipoControl: TipoControl,
        operatividad: OperatividadControl,
        periodicidad: PeriodicidadControl
    ): BigDecimal {

        val tabla = when {

            supervision == NivelSupervision.DIRECTIVO_AUTOMATICO &&
                    tipoControl == TipoControl.PREVENTIVO ->
                arrayOf(
                    arrayOf("0.800", "0.740", "0.660"),
                    arrayOf("0.740", "0.679", "0.600"),
                    arrayOf("0.660", "0.600", "0.520")
                )

            supervision == NivelSupervision.DIRECTIVO_AUTOMATICO &&
                    tipoControl == TipoControl.DETECTIVO ->
                arrayOf(
                    arrayOf("0.700", "0.639", "0.560"),
                    arrayOf("0.639", "0.580", "0.500"),
                    arrayOf("0.560", "0.500", "0.420")
                )

            supervision == NivelSupervision.ANALISTA_COORDINADOR &&
                    tipoControl == TipoControl.PREVENTIVO ->
                arrayOf(
                    arrayOf("0.740", "0.680", "0.600"),
                    arrayOf("0.680", "0.620", "0.540"),
                    arrayOf("0.600", "0.540", "0.459")
                )

            supervision == NivelSupervision.ANALISTA_COORDINADOR &&
                    tipoControl == TipoControl.DETECTIVO ->
                arrayOf(
                    arrayOf("0.640", "0.580", "0.500"),
                    arrayOf("0.580", "0.520", "0.439"),
                    arrayOf("0.500", "0.439", "0.360")
                )

            supervision == NivelSupervision.OPERATIVO &&
                    tipoControl == TipoControl.PREVENTIVO ->
                arrayOf(
                    arrayOf("0.660", "0.600", "0.520"),
                    arrayOf("0.600", "0.540", "0.460"),
                    arrayOf("0.520", "0.460", "0.380")
                )

            else ->
                arrayOf(
                    arrayOf("0.560", "0.499", "0.420"),
                    arrayOf("0.499", "0.440", "0.360"),
                    arrayOf("0.420", "0.360", "0.280")
                )
        }

        val fila = when (operatividad) {
            OperatividadControl.AUTOMATICO -> 0
            OperatividadControl.SEMI_AUTOMATICO -> 1
            OperatividadControl.MANUAL -> 2
        }

        val columna = when (periodicidad) {
            PeriodicidadControl.PERMANENTE -> 0
            PeriodicidadControl.PERIODICO -> 1
            PeriodicidadControl.EVENTUAL -> 2
        }

        return BigDecimal(tabla[fila][columna])
    }
}