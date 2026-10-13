package com.security.modules.operaciones.dto

data class CatalogoOpcionResponse(val id: Long, val nombre: String)
data class MonedaOpcionResponse(val id: Long, val codigo: String, val nombre: String, val simbolo: String?)
data class DepartamentoOpcionResponse(val id: Long, val nombre: String, val idPais: Long)
data class CatalogosOperacionResponse(
    val monedas: List<MonedaOpcionResponse>,
    val ocupaciones: List<CatalogoOpcionResponse>,
    val tiposDocumento: List<CatalogoOpcionResponse>,
    val departamentos: List<DepartamentoOpcionResponse>,
    val paises: List<CatalogoOpcionResponse>
)
