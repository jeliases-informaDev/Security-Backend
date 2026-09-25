package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.enums.NivelImpacto
import java.math.BigDecimal

class ClasificadorImpactoService {

    fun clasificar(monto: BigDecimal): NivelImpacto {

        require(monto >= BigDecimal.ZERO) {
            "El impacto estimado no puede ser negativo"
        }

        return when {
            monto < BigDecimal("4600") ->
                NivelImpacto.INSIGNIFICANTE

            monto < BigDecimal("230000") ->
                NivelImpacto.MENOR

            monto < BigDecimal("460000") ->
                NivelImpacto.MODERADO

            monto < BigDecimal("920000") ->
                NivelImpacto.MAYOR

            else ->
                NivelImpacto.CATASTROFICO
        }
    }
}
