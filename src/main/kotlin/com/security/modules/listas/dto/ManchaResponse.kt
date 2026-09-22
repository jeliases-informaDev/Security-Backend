package com.security.modules.listas.dto

import java.time.LocalDate

data class ManchaResponse(
    val id: Int,
    val tipoListaCodigo: String?,
    val tipoListaNombre: String?,
    val descripcion: String?,
    val link: String?,
    val fechaRegistro: LocalDate?,
    val fechaHasta: LocalDate?,
    val institucion: String?,
    val cargo: String?,
    val tipoPep: String?,
    val periodoDesde: String?,
    val periodoHasta: String?
)
