package com.security.modules.listas.dto

data class ResultadoBusquedaResponse(
    val entidadId: Int,
    val tipoEntidad: String,
    val documento: String,
    val tipoDocumento: String?,
    val nombreCompleto: String,
    val pais: String?,
    val manchas: List<ManchaResponse>
)
