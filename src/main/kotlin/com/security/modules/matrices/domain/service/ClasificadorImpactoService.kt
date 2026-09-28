package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.parameters.MatrizRiesgoParametros
import com.security.modules.matrices.domain.enums.NivelImpacto
import java.math.BigDecimal

class ClasificadorImpactoService {

    fun clasificar(
        monto: BigDecimal
    ): NivelImpacto {

        require(monto >= BigDecimal.ZERO) {
            "El impacto estimado no puede ser negativo"
        }

        return MatrizRiesgoParametros.RANGOS_IMPACTO
            .first { rango ->
                rango.limiteSuperior == null ||
                        monto <= rango.limiteSuperior
            }
            .nivel
    }
}