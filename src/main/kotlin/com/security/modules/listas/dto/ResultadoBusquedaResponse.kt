package com.security.modules.listas.dto

import java.time.LocalDate

data class ResultadoBusquedaResponse(
    val entidadId: Int,
    val tipoEntidad: String,
    val documento: String,
    val tipoDocumento: String?,
    val nombreCompleto: String,
    val nombres: String?,
    val apellidoPaterno: String?,
    val apellidoMaterno: String?,
    val pasaporte: String?,
    val alias: String?,
    val fechaNacimientoRegistro: LocalDate?,
    val pais: String?,
    val manchas: List<ManchaResponse>
)
