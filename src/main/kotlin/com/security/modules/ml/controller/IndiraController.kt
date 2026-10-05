package com.security.modules.ml.controller

import com.security.modules.ml.dto.IndiraChatRequest
import com.security.modules.ml.dto.IndiraChatResponse
import com.security.modules.ml.dto.IndiraFeedbackRequest
import com.security.modules.ml.dto.PersonaConCasosResponse
import com.security.modules.ml.service.MlService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Puerta de entrada al microservicio de ML. Requiere JWT como el resto de la API;
 * la clave interna hacia el ML la agrega MlClient.
 */
@RestController
@RequestMapping("/api/ml")
class IndiraController(
    private val mlService: MlService
) {

    @PostMapping("/indira/chat")
    fun chat(
        authentication: Authentication,
        @Valid @RequestBody request: IndiraChatRequest
    ): ResponseEntity<IndiraChatResponse> =
        ResponseEntity.ok(mlService.chatear(authentication.name, request))

    @PostMapping("/indira/mensajes/{mensajeId}/feedback")
    fun feedback(
        @PathVariable mensajeId: UUID,
        @Valid @RequestBody request: IndiraFeedbackRequest
    ): ResponseEntity<Void> {
        mlService.enviarFeedback(mensajeId, request.feedback)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/personas/buscar")
    fun buscarPersona(
        @RequestParam q: String
    ): ResponseEntity<PersonaConCasosResponse> =
        ResponseEntity.ok(mlService.buscarPersona(q))
}
