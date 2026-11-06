package com.security.modules.operaciones.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// ---------- Configuracion (RO 16) ----------

data class UmbralNormaResponse(val tipoBien: String, val nombre: String, val umbralUsd: BigDecimal)

data class UmbralInternoDto(val montoUsd: BigDecimal? = null, val motivo: String? = null)

data class ConfiguracionResponse(
    val empresa: String,
    val sector: String?,
    val sectorNombre: String?,
    val porConfigurar: Boolean,
    val unRoPorActividad: Boolean,
    val umbralesNorma: List<UmbralNormaResponse>,
    val notaUmbral: String?,
    val umbralInterno: UmbralInternoDto?,
    val oficinas: List<String>,
    val actualizadoEn: LocalDateTime?,
    val actualizadoPor: String?,
    val puedeEditar: Boolean
)

data class ConfiguracionRequest(
    val sector: String? = null,
    val unRoPorActividad: Boolean? = null,
    val umbralInterno: UmbralInternoDto? = null,
    val oficinas: List<String>? = null
)

// ---------- Tipo de cambio ----------

data class TipoCambioResponse(
    val moneda: String,
    val mes: String,
    val valor: BigDecimal,
    val ambito: String,
    val fuente: String?,
    val cargadoPor: String?,
    val cargadoEn: LocalDateTime?
)

data class TiposCambioResponse(val anio: Int, val monedas: List<String>, val puedeEditar: Boolean, val valores: List<TipoCambioResponse>)

data class TipoCambioRequest(val moneda: String? = null, val mes: String? = null, val valor: BigDecimal? = null, val fuente: String? = null)

// ---------- Umbral ----------

data class VerificarUmbralRequest(
    val tipoOperacion: String? = null,
    val moneda: String? = null,
    val monto: BigDecimal? = null,
    val fechaOperacion: LocalDate? = null
)

data class TipoCambioUsado(val mes: String, val usd: BigDecimal?, val moneda: BigDecimal?)

data class VerificacionUmbralResponse(
    val estado: String,
    val mensaje: String,
    val sector: String?,
    val sectorNombre: String?,
    val tipoBien: String?,
    val tipoBienNombre: String?,
    val umbralUsd: BigDecimal?,
    val umbralNormaUsd: BigDecimal?,
    val umbralInterno: Boolean?,
    val montoUsd: BigDecimal?,
    val tipoCambio: TipoCambioUsado?,
    val tipoCambioNota: String?
)

// ---------- Inusualidad y ROS (RO 12, RO 13) ----------

data class AdjuntoResponse(
    val id: Long,
    val nombre: String,
    val tipo: String?,
    val tamano: Long,
    val subidoEn: LocalDateTime,
    val subidoPor: String?
)

data class DatosOperacionAnalisis(
    val fechaOperacion: LocalDate,
    val tipoOperacion: String?,
    val monto: BigDecimal,
    val moneda: String?,
    val modalidadPago: String?,
    val origenFondos: String?,
    val numeroComprobante: String?,
    val participantes: Int
)

data class InusualidadResponse(
    val id: Long,
    val numeroRegistro: String,
    val estado: String,
    val senal: String?,
    val senalNombre: String?,
    val senalTexto: String?,
    val fuente: String?,
    val fuenteNombre: String?,
    val fechaDeteccion: LocalDate?,
    val descripcionSenal: String?,
    val descripcionOperacion: String?,
    val analisis: String?,
    val decision: String?,
    val calificadaEn: LocalDateTime?,
    val calificadaPor: String?,
    val venceEn: LocalDateTime?,
    val adjuntos: List<AdjuntoResponse>,
    val datosOperacion: DatosOperacionAnalisis,
    val actualizadoEn: LocalDateTime?,
    val actualizadoPor: String?,
    val puedeCalificar: Boolean
)

data class InusualidadRequest(
    val senal: String? = null,
    val senalTexto: String? = null,
    val fuente: String? = null,
    val fechaDeteccion: LocalDate? = null,
    val descripcionSenal: String? = null,
    val descripcionOperacion: String? = null,
    val analisis: String? = null
)

data class DecisionRequest(val decision: String? = null)

data class DatoMinimoResponse(val codigo: String, val nombre: String, val completo: Boolean)

data class RosResponse(
    val id: Long,
    val numeroRegistro: String,
    val calificadaEn: LocalDateTime?,
    val calificadaPor: String?,
    val venceEn: LocalDateTime?,
    val restanteSegundos: Long,
    val estadoPlazo: String,
    val datosMinimos: List<DatoMinimoResponse>,
    val datosListos: Boolean,
    val numeroRos: String?,
    val enviadoEn: LocalDateTime?,
    val registradoEn: LocalDateTime?,
    val registradoPor: String?,
    val enviadoFueraDePlazo: Boolean?,
    val puedeRegistrar: Boolean
)

data class RosRequest(val numeroRos: String? = null, val enviadoEn: LocalDateTime? = null)

/** Resumen en el detalle del registro. */
data class InusualidadResumen(
    val estado: String,
    val senalNombre: String?,
    val fechaDeteccion: LocalDate?,
    val calificadaEn: LocalDateTime?,
    val venceEn: LocalDateTime?,
    val numeroRos: String?
)

data class EnvioResumen(val exportacionId: Long, val fechaEnvio: LocalDate, val numeroAcuse: String?)

// ---------- Exportacion (RO 14, RO 15) ----------

data class ChequeoResponse(val codigo: String, val texto: String, val ok: Boolean)

data class ObservacionResponse(val idRegistro: Long, val numeroRegistro: String, val texto: String)

data class ValidacionExportacionResponse(
    val periodo: String,
    val formato: String,
    val formatoNombre: String?,
    val formatoDisponible: Boolean,
    val registros: Long,
    val pendientes: Long,
    val enviados: Long,
    val chequeos: List<ChequeoResponse>,
    val observaciones: List<ObservacionResponse>,
    val puedeGenerar: Boolean
)

data class ExportacionRequest(val periodo: String? = null, val formato: String? = null)

data class ConstanciaResponse(
    val fechaEnvio: LocalDate,
    val numeroAcuse: String?,
    val notas: String?,
    val archivo: String?,
    val registradoEn: LocalDateTime?,
    val registradoPor: String?
)

data class ExportacionResponse(
    val id: Long,
    val periodo: String,
    val formato: String,
    val formatoNombre: String?,
    val registros: Int,
    val generadoEn: LocalDateTime,
    val generadoPor: String?,
    val archivoXlsx: String?,
    val archivoCsv: String?,
    val estado: String,
    val constancia: ConstanciaResponse?,
    val puedeRegistrarConstancia: Boolean
)

data class PaginaExportacionesResponse(
    val content: List<ExportacionResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val size: Int
)

data class ConstanciaRegistradaResponse(val id: Long, val enviados: Int, val constancia: ConstanciaResponse)
