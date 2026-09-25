package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo

class RiesgoInherenteService {

    fun calcular(
        probabilidad: NivelProbabilidad,
        impacto: NivelImpacto
    ): NivelRiesgo {

        return when (probabilidad) {

            NivelProbabilidad.MUY_ALTA -> when (impacto) {
                NivelImpacto.INSIGNIFICANTE,
                NivelImpacto.MENOR ->
                    NivelRiesgo.MODERADO

                NivelImpacto.MODERADO ->
                    NivelRiesgo.ALTO

                NivelImpacto.MAYOR,
                NivelImpacto.CATASTROFICO ->
                    NivelRiesgo.MUY_ALTO
            }

            NivelProbabilidad.ALTA -> when (impacto) {
                NivelImpacto.INSIGNIFICANTE ->
                    NivelRiesgo.LEVE

                NivelImpacto.MENOR,
                NivelImpacto.MODERADO ->
                    NivelRiesgo.MODERADO

                NivelImpacto.MAYOR ->
                    NivelRiesgo.ALTO

                NivelImpacto.CATASTROFICO ->
                    NivelRiesgo.MUY_ALTO
            }

            NivelProbabilidad.MEDIA -> when (impacto) {
                NivelImpacto.INSIGNIFICANTE,
                NivelImpacto.MENOR ->
                    NivelRiesgo.LEVE

                NivelImpacto.MODERADO,
                NivelImpacto.MAYOR ->
                    NivelRiesgo.MODERADO

                NivelImpacto.CATASTROFICO ->
                    NivelRiesgo.ALTO
            }

            NivelProbabilidad.BAJA -> when (impacto) {
                NivelImpacto.INSIGNIFICANTE ->
                    NivelRiesgo.MINIMO

                NivelImpacto.MENOR,
                NivelImpacto.MODERADO ->
                    NivelRiesgo.LEVE

                NivelImpacto.MAYOR,
                NivelImpacto.CATASTROFICO ->
                    NivelRiesgo.MODERADO
            }

            NivelProbabilidad.MUY_BAJA -> when (impacto) {
                NivelImpacto.INSIGNIFICANTE,
                NivelImpacto.MENOR ->
                    NivelRiesgo.MINIMO

                NivelImpacto.MODERADO,
                NivelImpacto.MAYOR ->
                    NivelRiesgo.LEVE

                NivelImpacto.CATASTROFICO ->
                    NivelRiesgo.MODERADO
            }
        }
    }
}