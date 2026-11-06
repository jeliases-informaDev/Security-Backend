package com.security.shared.exceptions


import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

// Los campos opcionales solo aparecen cuando traen valor (los usan los errores de licencia: code, enlace, estado, cupo).
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiErrorResponse(
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val code: String? = null,
    val enlace: String? = null,
    val estado: String? = null,
    val activos: Long? = null,
    @JsonProperty("usuarios_max") val usuariosMax: Long? = null,
    // Errores de datos de un formulario: campo -> mensaje (Registro de Operaciones)
    val campos: Map<String, String>? = null
)
