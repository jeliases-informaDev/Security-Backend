package com.security.modules.ml.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

// --- Lo que recibe este backend desde el frontend ---

data class IndiraChatRequest(
    @field:NotBlank(message = "El mensaje no puede estar vacío")
    @field:Size(max = 2000, message = "El mensaje no puede superar los 2000 caracteres")
    val mensaje: String,

    val conversacionId: UUID? = null,

    // "web" o "app"
    val canal: String = "web"
)

data class IndiraFeedbackRequest(
    // "positivo" o "negativo"
    @field:NotBlank(message = "El feedback es obligatorio")
    val feedback: String
)

// --- Lo que responde este backend ---

data class IndiraChatResponse(
    val conversacionId: UUID,
    val mensajeId: UUID,
    val respuesta: String
)

data class CasoMlResponse(
    val id: UUID,
    val tipo: String,
    val categoriaDelito: String,
    val resumen: String,
    val urlFuente: String,
    val scoreConfianza: Double,
    val estadoRevision: String
)

data class PersonaMlResponse(
    val id: UUID,
    val numeroDocumento: String?,
    val nombres: String,
    val apellidos: String,
    val esPep: Boolean,
    val nivelRiesgoScore: Double,
    val estadoVerificacion: String
)

data class PersonaConCasosResponse(
    val persona: PersonaMlResponse?,
    val casos: List<CasoMlResponse>
)

// --- Contrato del microservicio ML (snake_case) ---

internal data class MlChatRequest(
    @JsonProperty("usuario_id") val usuarioId: UUID,
    val canal: String,
    val mensaje: String,
    @JsonProperty("conversacion_id") val conversacionId: UUID?
)

internal data class MlChatResponse(
    @JsonProperty("conversacion_id") val conversacionId: UUID,
    @JsonProperty("mensaje_id") val mensajeId: UUID,
    val respuesta: String
)

internal data class MlFeedbackRequest(val feedback: String)

internal data class MlCaso(
    val id: UUID,
    val tipo: String,
    @JsonProperty("categoria_delito") val categoriaDelito: String,
    val resumen: String,
    @JsonProperty("url_fuente") val urlFuente: String,
    @JsonProperty("score_confianza") val scoreConfianza: Double,
    @JsonProperty("estado_revision") val estadoRevision: String
)

internal data class MlPersona(
    val id: UUID,
    @JsonProperty("numero_documento") val numeroDocumento: String?,
    val nombres: String,
    val apellidos: String,
    @JsonProperty("es_pep") val esPep: Boolean,
    @JsonProperty("nivel_riesgo_score") val nivelRiesgoScore: Double,
    @JsonProperty("estado_verificacion") val estadoVerificacion: String
)

internal data class MlPersonaConCasos(
    val persona: MlPersona?,
    val casos: List<MlCaso>
)
