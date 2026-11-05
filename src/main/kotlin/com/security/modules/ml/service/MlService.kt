package com.security.modules.ml.service

import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaReglas
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.dominio.TIPO_MENSAJE_INDIRA
import com.security.modules.ml.client.MlClient
import com.security.modules.ml.dto.CasoMlResponse
import com.security.modules.ml.dto.IndiraChatRequest
import com.security.modules.ml.dto.IndiraChatResponse
import com.security.modules.ml.dto.MlChatRequest
import com.security.modules.ml.dto.PersonaConCasosResponse
import com.security.modules.ml.dto.PersonaMlResponse
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class MlService(
    private val mlClient: MlClient,
    private val usuarioRepository: UsuarioRepository,
    private val licenciaContexto: LicenciaContexto,
    private val licenciaService: LicenciaService
) {

    fun chatear(username: String, request: IndiraChatRequest): IndiraChatResponse {
        val canal = request.canal.lowercase()
        if (canal !in CANALES_VALIDOS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Canal no válido. Use 'web' o 'app'")
        }

        val usuarioId = uuidDeUsuario(username)

        // Cada mensaje a Indira consume 1 token, pedido antes de llamar al ML; si el ML falla, se devuelve.
        val claveConsumo = consumirMensaje()
        val respuesta = try {
            mlClient.chat(
                MlChatRequest(
                    usuarioId = usuarioId,
                    canal = canal,
                    mensaje = request.mensaje.trim(),
                    conversacionId = request.conversacionId
                )
            )
        } catch (e: Exception) {
            claveConsumo?.let { licenciaService.anular(it) }
            throw e
        }

        return IndiraChatResponse(respuesta.conversacionId, respuesta.mensajeId, respuesta.respuesta)
    }

    fun enviarFeedback(mensajeId: UUID, feedback: String) {
        val valor = feedback.lowercase()
        if (valor !in FEEDBACKS_VALIDOS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback no válido. Use 'positivo' o 'negativo'")
        }
        mlClient.feedback(mensajeId, valor)
    }

    fun buscarPersona(texto: String): PersonaConCasosResponse {
        val consulta = texto.trim()
        if (consulta.length !in 3..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingrese entre 3 y 100 caracteres")
        }

        val resultado = mlClient.buscarPersona(consulta)

        return PersonaConCasosResponse(
            persona = resultado.persona?.let {
                PersonaMlResponse(
                    id = it.id,
                    numeroDocumento = it.numeroDocumento,
                    nombres = it.nombres,
                    apellidos = it.apellidos,
                    esPep = it.esPep,
                    nivelRiesgoScore = it.nivelRiesgoScore,
                    estadoVerificacion = it.estadoVerificacion
                )
            },
            casos = resultado.casos.map {
                CasoMlResponse(
                    id = it.id,
                    tipo = it.tipo,
                    categoriaDelito = it.categoriaDelito,
                    resumen = it.resumen,
                    urlFuente = it.urlFuente,
                    scoreConfianza = it.scoreConfianza,
                    estadoRevision = it.estadoRevision
                )
            }
        )
    }

    /**
     * Pide 1 token a Sulotec para el mensaje y devuelve la clave del consumo (null si no se consumió: licencias
     * apagadas, sin sesión o una cuenta sin empresa con UUID).
     */
    private fun consumirMensaje(): String? {
        if (!licenciaService.activo) return null
        val contexto = licenciaContexto.actual() ?: return null
        val uuid = contexto.empresaUuid ?: return null
        val clave = LicenciaReglas.claveAleatoria(uuid, TIPO_MENSAJE_INDIRA)
        licenciaService.consumirComo(contexto, TIPO_MENSAJE_INDIRA, clave, "mensaje a Indira")
        return clave
    }

    /**
     * El ML identifica al usuario con un UUID y este backend usa ids enteros.
     * Se deriva un UUID estable a partir del id, para que el historial de Indira
     * del mismo usuario siempre quede asociado.
     */
    private fun uuidDeUsuario(username: String): UUID {
        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException("Usuario no encontrado")
        return UUID.nameUUIDFromBytes("security-usuario-${usuario.id}".toByteArray())
    }

    companion object {
        private val CANALES_VALIDOS = setOf("web", "app")
        private val FEEDBACKS_VALIDOS = setOf("positivo", "negativo")
    }
}
