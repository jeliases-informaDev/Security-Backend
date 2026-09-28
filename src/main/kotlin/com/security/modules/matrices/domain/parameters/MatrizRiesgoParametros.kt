package com.security.modules.matrices.domain.parameters

import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.OperatividadControl
import com.security.modules.matrices.domain.enums.PeriodicidadControl
import com.security.modules.matrices.domain.enums.RespuestaControl
import com.security.modules.matrices.domain.enums.TipoControl
import java.math.BigDecimal

object MatrizRiesgoParametros {

    // =========================================================
    // IMPACTO
    // Fuente: Matriz_Riesgo.xlsx / hoja Parámetros
    // =========================================================

    data class RangoImpacto(
        val limiteSuperior: BigDecimal?,
        val nivel: NivelImpacto
    )

    val RANGOS_IMPACTO = listOf(
        RangoImpacto(
            limiteSuperior = BigDecimal("30000"),
            nivel = NivelImpacto.INSIGNIFICANTE
        ),
        RangoImpacto(
            limiteSuperior = BigDecimal("200000"),
            nivel = NivelImpacto.MENOR
        ),
        RangoImpacto(
            limiteSuperior = BigDecimal("850000"),
            nivel = NivelImpacto.MODERADO
        ),
        RangoImpacto(
            limiteSuperior = BigDecimal("1700000"),
            nivel = NivelImpacto.MAYOR
        ),
        RangoImpacto(
            limiteSuperior = null,
            nivel = NivelImpacto.CATASTROFICO
        )
    )

    // =========================================================
    // PROBABILIDAD
    //
    // Valores representativos utilizados por el Excel para
    // recalcular probabilidad residual.
    // =========================================================

    val VALOR_PROBABILIDAD = mapOf(
        NivelProbabilidad.MUY_ALTA to BigDecimal("1.000"),
        NivelProbabilidad.ALTA to BigDecimal("0.699"),
        NivelProbabilidad.MEDIA to BigDecimal("0.299"),
        NivelProbabilidad.BAJA to BigDecimal("0.099"),
        NivelProbabilidad.MUY_BAJA to BigDecimal("0.049")
    )

    data class RangoProbabilidad(
        val limiteSuperior: BigDecimal,
        val nivel: NivelProbabilidad
    )

    val RANGOS_PROBABILIDAD_RESIDUAL = listOf(
        RangoProbabilidad(
            BigDecimal("0.049"),
            NivelProbabilidad.MUY_BAJA
        ),
        RangoProbabilidad(
            BigDecimal("0.099"),
            NivelProbabilidad.BAJA
        ),
        RangoProbabilidad(
            BigDecimal("0.299"),
            NivelProbabilidad.MEDIA
        ),
        RangoProbabilidad(
            BigDecimal("0.999"),
            NivelProbabilidad.ALTA
        ),
        RangoProbabilidad(
            BigDecimal("1.000"),
            NivelProbabilidad.MUY_ALTA
        )
    )

    // =========================================================
    // DISEÑO DEL CONTROL
    // =========================================================

    val VALOR_PERIODICIDAD = mapOf(
        PeriodicidadControl.PERMANENTE to BigDecimal("1.0"),
        PeriodicidadControl.PERIODICO to BigDecimal("0.7"),
        PeriodicidadControl.EVENTUAL to BigDecimal("0.3")
    )

    val VALOR_OPERATIVIDAD = mapOf(
        OperatividadControl.AUTOMATICO to BigDecimal("1.0"),
        OperatividadControl.SEMI_AUTOMATICO to BigDecimal("0.7"),
        OperatividadControl.MANUAL to BigDecimal("0.3")
    )

    val VALOR_TIPO_CONTROL = mapOf(
        TipoControl.PREVENTIVO to BigDecimal("1.0"),
        TipoControl.DETECTIVO to BigDecimal("0.5")
    )

    // La fórmula real del Excel asigna 20 % a estos tres factores.
    val PESO_PERIODICIDAD = BigDecimal("0.20")
    val PESO_OPERATIVIDAD = BigDecimal("0.20")
    val PESO_TIPO_CONTROL = BigDecimal("0.20")

    // =========================================================
    // EJECUCIÓN DEL CONTROL
    // =========================================================

    val VALOR_RESPUESTA = mapOf(
        RespuestaControl.SI to BigDecimal.ONE,
        RespuestaControl.NO to BigDecimal.ZERO
    )

    val PESO_FRECUENCIA_OPORTUNA = BigDecimal("0.50")
    val PESO_SEGUIMIENTO_ADECUADO = BigDecimal("0.50")
}