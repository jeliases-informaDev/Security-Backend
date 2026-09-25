package com.security.modules.matrices.domain.enums

enum class NivelProbabilidad(
    val nivel: Int,
    val descripcion: String
) {

    MUY_BAJA(
        1,
        "Se produce 1 vez cada 5 o más años"
    ),

    BAJA(
        2,
        "Se produce 1 vez cada 3 años"
    ),

    MEDIA(
        3,
        "Se produce 1 vez cada año"
    ),

    ALTA(
        4,
        "Se produce 2 a 4 veces al año"
    ),

    MUY_ALTA(
        5,
        "Se produce de 5 a más veces al año"
    )
}