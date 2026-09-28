package com.security.modules.scoring.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class EvaluarScoringRequest(
    @field:NotNull(message = "La entidad es obligatoria")
    val entidadId: Int,

    @field:NotNull(message = "La ocupación es obligatoria")
    val idOcupacion: Int,

    @field:NotNull(message = "El departamento es obligatorio")
    val idDepartamento: Int,

    @field:NotNull(message = "El volumen transaccional es obligatorio")
    @field:PositiveOrZero(message = "El volumen transaccional no puede ser negativo")
    val volumenTransaccional: BigDecimal
)
