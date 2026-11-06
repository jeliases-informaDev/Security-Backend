package com.security.modules.operaciones.dto

import com.fasterxml.jackson.annotation.JsonInclude

data class CatalogoOpcionResponse(val id: Long, val nombre: String)
data class MonedaOpcionResponse(val id: Long, val codigo: String, val nombre: String, val simbolo: String?)
data class DepartamentoOpcionResponse(val id: Long, val nombre: String, val idPais: Long)

/** Opcion de los catalogos del formulario nuevo; `sector` solo en los tipos de operacion. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpcionRegistroResponse(
    val codigo: String,
    val nombre: String,
    val pideTexto: Boolean,
    val sector: String? = null,
    val tipoBien: String? = null,
    val disponible: Boolean? = null
)

data class CatalogosOperacionResponse(
    val monedas: List<MonedaOpcionResponse>,
    val ocupaciones: List<CatalogoOpcionResponse>,
    val tiposDocumento: List<CatalogoOpcionResponse>,
    val departamentos: List<DepartamentoOpcionResponse>,
    val paises: List<CatalogoOpcionResponse>,
    val tiposDocumentoOperacion: List<OpcionRegistroResponse> = emptyList(),
    val estadosCiviles: List<OpcionRegistroResponse> = emptyList(),
    val modalidadesPago: List<OpcionRegistroResponse> = emptyList(),
    val origenesFondos: List<OpcionRegistroResponse> = emptyList(),
    val tiposOperacion: List<OpcionRegistroResponse> = emptyList(),
    val tipoDocumentoInicial: String = "DNI",
    val sectores: List<OpcionRegistroResponse> = emptyList(),
    val senalesAlerta: List<OpcionRegistroResponse> = emptyList(),
    val fuentesSenal: List<OpcionRegistroResponse> = emptyList(),
    val formatosExportacion: List<OpcionRegistroResponse> = emptyList(),
    val oficinas: List<String> = emptyList(),
    val tiposOperacionPorConfigurar: Boolean = false
)
