package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.domain.CambioCampo
import com.security.modules.operaciones.domain.EventoOperacionEntity
import com.security.modules.operaciones.domain.EventoOperacionEntity.Companion.ANTERIOR
import com.security.modules.operaciones.domain.EventoOperacionEntity.Companion.CREACION
import com.security.modules.operaciones.domain.EventoOperacionEntity.Companion.DESCARGA
import com.security.modules.operaciones.domain.EventoOperacionEntity.Companion.EDICION
import com.security.modules.operaciones.domain.EventoOperacionEntity.Companion.RESPONSABLE
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.dto.AutorEventoResponse
import com.security.modules.operaciones.dto.CambioResponse
import com.security.modules.operaciones.dto.EventoOperacionResponse
import com.security.modules.operaciones.dto.HistorialOperacionResponse
import com.security.modules.operaciones.infraestructure.HistorialOperacionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** Quien hace algo sobre un registro (se guarda una copia con el evento). */
data class AutorEvento(val id: Int?, val usuario: String, val nombreCompleto: String)

/**
 * Historial de cada registro: solo se agregan eventos (no hay metodos para cambiarlos ni borrarlos).
 * Un registro sin evento de creacion es anterior al historial: se muestra con un evento «Registro anterior al historial».
 */
@Service
class HistorialOperacionService(private val repository: HistorialOperacionRepository) {

    fun anotar(
        registroId: Long,
        tipo: String,
        titulo: String,
        detalle: String?,
        autor: AutorEvento?,
        fecha: LocalDateTime,
        motivo: String? = null,
        cambios: List<CambioCampo> = emptyList(),
        codigoVerificacion: String? = null,
        huella: String? = null
    ): EventoOperacionEntity = repository.save(
        EventoOperacionEntity(
            registroId = registroId, tipo = tipo, titulo = titulo, detalle = detalle, motivo = motivo,
            idUsuario = autor?.id, usuario = autor?.usuario, nombreUsuario = autor?.nombreCompleto,
            fecha = fecha, codigoVerificacion = codigoVerificacion, huellaSha256 = huella, cambios = cambios
        )
    )

    fun creacion(registroId: Long, autor: AutorEvento?, fecha: LocalDateTime) =
        anotar(registroId, CREACION, "Creación del registro", "Con sello de fecha y hora.", autor, fecha)

    fun edicion(registroId: Long, autor: AutorEvento?, fecha: LocalDateTime, motivo: String, cambios: List<CambioCampo>) =
        anotar(registroId, EDICION, "Edición del registro", detalleCambios(cambios), autor, fecha, motivo, cambios)

    fun responsable(registroId: Long, autor: AutorEvento?, fecha: LocalDateTime, antes: String?, despues: String) {
        val cambio = CambioCampo("responsable", "Responsable", antes, despues)
        anotar(registroId, RESPONSABLE, "Cambio de responsable", "${antes ?: "Sin asignar"} → $despues", autor, fecha, cambios = listOf(cambio))
    }

    fun descarga(registroId: Long, autor: AutorEvento?, fecha: LocalDateTime, codigo: String, huella: String) =
        anotar(
            registroId, DESCARGA, "Descarga del PDF",
            "Se descargó la versión del ${FotoOperacion.fecha(fecha.toLocalDate())} ${"%02d:%02d".format(fecha.hour, fecha.minute)}. " +
                "Código de verificación $codigo.",
            autor, fecha, codigoVerificacion = codigo, huella = huella
        )

    /** Todos los eventos, el mas reciente primero (con el evento sintetico de los registros anteriores al final). */
    fun eventos(registro: RegistroOperacionesEntity): List<EventoOperacionResponse> {
        val id = requireNotNull(registro.id)
        val guardados = repository.findAllByRegistroIdOrderByFechaDescIdDesc(id).map { it.respuesta() }
        if (guardados.any { it.tipo == CREACION }) return guardados
        return guardados + EventoOperacionResponse(
            id = null, tipo = ANTERIOR, titulo = "Registro anterior al historial",
            detalle = "Se registró antes de que existiera el historial de cambios.",
            motivo = null, usuario = null, sistema = true, fecha = registro.fechaCreacion,
            cambios = emptyList(), codigoVerificacion = null
        )
    }

    fun historial(registro: RegistroOperacionesEntity, tipo: String?): HistorialOperacionResponse {
        val filtro = tipo?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: TODO_
        if (filtro !in FILTROS) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.FILTRO_INVALIDO,
                "El filtro del historial debe ser TODO, EDICION, RESPONSABLE, DESCARGA o ANALISIS.")
        }
        val todos = eventos(registro)
        val eventos = when (filtro) {
            TODO_ -> todos
            else -> todos.filter { it.tipo == filtro }
        }
        val fechaOperacion = registro.operacion.fechaOperacion
        return HistorialOperacionResponse(
            id = requireNotNull(registro.id),
            numeroRegistro = registro.numeroRegistro.orEmpty(),
            fechaOperacion = fechaOperacion,
            conservarHasta = conservarHasta(registro),
            eventos = eventos
        )
    }

    private fun EventoOperacionEntity.respuesta() = EventoOperacionResponse(
        id = id, tipo = tipo, titulo = titulo, detalle = detalle, motivo = motivo,
        usuario = usuario?.let { AutorEventoResponse(it, nombreUsuario ?: it) },
        sistema = usuario == null, fecha = fecha,
        cambios = cambios.map { CambioResponse(it.campo, it.etiqueta, it.antes, it.despues) },
        codigoVerificacion = codigoVerificacion
    )

    companion object {
        private const val TODO_ = "TODO"
        private const val ANALISIS = "ANALISIS"
        private val FILTROS = setOf(TODO_, EDICION, RESPONSABLE, DESCARGA, ANALISIS)

        /** Se conserva 5 años desde la fecha de la operacion (Res. SBS 789-2018, art. 22 y 31, según el diseño). */
        fun conservarHasta(registro: RegistroOperacionesEntity) = registro.operacion.fechaOperacion.plusYears(5)

        fun detalleCambios(cambios: List<CambioCampo>): String =
            cambios.joinToString(" · ") { "${it.etiqueta}: ${it.antes ?: "—"} → ${it.despues ?: "—"}" }
    }
}
