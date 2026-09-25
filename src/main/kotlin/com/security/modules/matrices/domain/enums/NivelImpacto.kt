package com.security.modules.matrices.domain.enums

enum class NivelImpacto(
    val nivel: Int
) {

    INSIGNIFICANTE(1),
    MENOR(2),
    MODERADO(3),
    MAYOR(4),
    CATASTROFICO(5)
}
