package com.security.modules.ml.client

import com.security.modules.ml.config.MlProperties
import com.security.modules.ml.dto.MlChatRequest
import com.security.modules.ml.dto.MlChatResponse
import com.security.modules.ml.dto.MlFeedbackRequest
import com.security.modules.ml.dto.MlPersonaConCasos
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID

/**
 * Cliente HTTP del microservicio de ML. Aísla el contrato (snake_case) y traduce
 * los errores del ML a respuestas HTTP coherentes para el frontend.
 */
@Component
class MlClient(private val properties: MlProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val rapido: RestClient = construir(properties.timeoutSeconds)
    private val lento: RestClient = construir(properties.chatTimeoutSeconds)

    private fun construir(timeoutSeconds: Long): RestClient {
        val httpClient = HttpClient.newBuilder()
            // Por defecto el cliente de Java intenta subir a HTTP/2 (Upgrade: h2c) y Uvicorn, el servidor
            // del ML, pierde el cuerpo de los POST cuando ve ese encabezado (responde 422 "body missing").
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(timeoutSeconds))
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(factory)
            .defaultHeader(HEADER_CLAVE_INTERNA, properties.apiKey)
            .build()
    }

    internal fun chat(request: MlChatRequest): MlChatResponse =
        llamar("chat de Indira") {
            lento.post()
                .uri("/api/v1/indira/chat")
                .body(request)
                .retrieve()
                .body(MlChatResponse::class.java)
        }

    internal fun feedback(mensajeId: UUID, feedback: String) {
        llamar<Map<*, *>>("feedback de Indira") {
            rapido.post()
                .uri("/api/v1/indira/mensajes/{id}/feedback", mensajeId)
                .body(MlFeedbackRequest(feedback))
                .retrieve()
                .body(Map::class.java)
        }
    }

    internal fun buscarPersona(texto: String): MlPersonaConCasos =
        llamar("búsqueda de personas") {
            rapido.get()
                .uri { it.path("/api/v1/personas/buscar").queryParam("q", texto).build() }
                .retrieve()
                .body(MlPersonaConCasos::class.java)
        }

    private fun <T> llamar(operacion: String, bloque: () -> T?): T {
        try {
            return bloque() ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "El servicio de análisis devolvió una respuesta vacía")
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: RestClientResponseException) {
            log.warn("El ML respondió {} en {}: {}", e.statusCode, operacion, e.responseBodyAsString)
            throw ResponseStatusException(traducir(e.statusCode), mensajePara(e.statusCode))
        } catch (e: ResourceAccessException) {
            log.error("No se pudo conectar con el ML en {}: {}", operacion, e.message)
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de análisis no está disponible por el momento")
        }
    }

    private fun traducir(status: HttpStatusCode): HttpStatusCode = when (status.value()) {
        404 -> HttpStatus.NOT_FOUND
        422 -> HttpStatus.BAD_REQUEST
        503 -> HttpStatus.SERVICE_UNAVAILABLE
        // Un 401 del ML significa que la clave interna está mal configurada: no es culpa del usuario.
        else -> HttpStatus.BAD_GATEWAY
    }

    private fun mensajePara(status: HttpStatusCode): String = when (status.value()) {
        404 -> "No se encontró el recurso solicitado"
        422 -> "La solicitud no es válida"
        503 -> "El servicio de análisis no está disponible por el momento"
        else -> "El servicio de análisis no pudo procesar la solicitud"
    }

    companion object {
        const val HEADER_CLAVE_INTERNA = "X-Internal-Key"
    }
}
