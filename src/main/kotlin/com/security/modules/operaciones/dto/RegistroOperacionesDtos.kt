package com.security.modules.operaciones.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Quien hizo un evento (copia guardada en ese momento). */
data class AutorEventoResponse(val usuario: String, val nombreCompleto: String)

data class CambioResponse(val campo: String, val etiqueta: String, val antes: String?, val despues: String?)

data class EventoOperacionResponse(
    val id: Long?,
    val tipo: String,
    val titulo: String,
    val detalle: String?,
    val motivo: String?,
    val usuario: AutorEventoResponse?,
    val sistema: Boolean,
    val fecha: LocalDateTime,
    val cambios: List<CambioResponse>,
    val codigoVerificacion: String?
)

data class HistorialOperacionResponse(
    val id: Long,
    val numeroRegistro: String,
    val fechaOperacion: LocalDate,
    val conservarHasta: LocalDate,
    val eventos: List<EventoOperacionResponse>
)

data class IndicadoresOperacionesResponse(
    val periodo: String,
    val registrosDelPeriodo: Long,
    val pendientesDeEnvio: Long,
    val sinResponsable: Long,
    val total: Long,
    val enviados: Long = 0,
    val inusualesEnAnalisis: Long = 0,
    val sospechosas: Long = 0
)

data class RegistroCreadoResponse(
    val id: Long,
    val numeroRegistro: String,
    val fechaRegistro: LocalDate,
    val creadoEn: LocalDateTime,
    val estado: String,
    val responsable: UsuarioAsignableResponse?,
    val mensaje: String = "Operacion registrada correctamente",
    val umbral: VerificacionUmbralResponse? = null
)

data class EdicionOperacionResponse(
    val id: Long,
    val numeroRegistro: String,
    val mensaje: String,
    val cambios: List<CambioResponse>
)

data class ResponsablePosibleResponse(val id: Int, val nombreCompleto: String, val actual: Boolean)

/** Filtros de la lista (ya validados). */
data class FiltrosOperaciones(
    val q: String? = null,
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
    val tipoOperacion: List<String> = emptyList(),
    val modalidadPago: List<String> = emptyList(),
    val moneda: String? = null,
    val monedaId: Long? = null,
    val responsableId: Int? = null,
    val sinResponsable: Boolean = false,
    val estados: Set<String> = emptySet(),
    val oficina: String? = null,
    val montoMinUsd: BigDecimal? = null,
    val montoMaxUsd: BigDecimal? = null
)

/** Fila de la lista tal como sale de la base (ListaOperacionesConsulta). */
data class FilaOperacion(
    val id: Long,
    val numeroRegistro: String?,
    val personaFisica: String?,
    val personaNombre: String?,
    val oficina: String?,
    val fecha: LocalDate?,
    val creadoEn: LocalDateTime?,
    val fechaOperacion: LocalDate?,
    val tipoOperacion: String?,
    val modalidadPago: String?,
    val monto: BigDecimal?,
    val moneda: String?,
    val responsableId: Int?,
    val responsableNombre: String?,
    val estadoAnalisis: String? = null,
    val fechaEnvio: LocalDate? = null,
    val montoUsd: BigDecimal? = null,
    val umbralEstado: String? = null
)
