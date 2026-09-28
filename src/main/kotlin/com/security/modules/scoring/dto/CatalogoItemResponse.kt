package com.security.modules.scoring.dto

data class CatalogoItemResponse(
    val id: Int,
    val nombre: String
)

data class CatalogosScoringResponse(
    val ocupaciones: List<CatalogoItemResponse>,
    val departamentos: List<CatalogoItemResponse>
)
