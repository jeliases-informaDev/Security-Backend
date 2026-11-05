package com.security.modules.scoring.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// Contrato con el front (rutas bajo /api/scoring). Ver docs/SCORING.md.

// ---------------- GET /catalogos ----------------

data class MetodologiaInfo(val version: Int, val fecha: LocalDate)

data class OpcionCatalogo(val id: Int, val nombre: String, val puntaje: Int?)

data class MedioPagoCatalogo(val id: Int, val nombre: String, val puntaje: Int?, val pideOrigenFondos: Boolean)

data class PaisCatalogo(val id: Int, val nombre: String, val grupo: Int?, val altoRiesgoGafi: Boolean)

/** Incluye `desde` y excluye `hasta`, salvo la ultima banda, que incluye su `hasta`. */
data class BandaCatalogo(val nivel: String, val desde: BigDecimal, val hasta: BigDecimal)

data class GrupoCatalogo(val codigo: String, val nombre: String, val peso: Int)

data class CatalogosScoringResponse(
    val metodologia: MetodologiaInfo,
    val ocupaciones: List<OpcionCatalogo>,
    val productos: List<OpcionCatalogo>,
    val canales: List<OpcionCatalogo>,
    val mediosPago: List<MedioPagoCatalogo>,
    val departamentos: List<OpcionCatalogo>,
    val paises: List<PaisCatalogo>,
    val sustentoDisponible: Boolean,
    val bandas: List<BandaCatalogo>,
    val grupos: List<GrupoCatalogo>,
    /** Fecha de la clasificacion de paises (lista del GAFI) con la que se cargo el catalogo; null si no hay. */
    val vigenciaPaises: LocalDate?
)

// ---------------- GET /cliente ----------------

/** `revisado: false` = el sistema no tiene como comprobarlo: el front lo muestra como «Sin dato». */
data class CoincidenciaListas(val revisado: Boolean, val coincidencias: Int?)

data class ConsultaListasResponse(
    val fecha: LocalDateTime,
    val pep: CoincidenciaListas,
    val vinculados: CoincidenciaListas,
    val sanciones: CoincidenciaListas,
    val noticias: CoincidenciaListas
)

data class CondicionResponse(val puntaje: Int, val etiqueta: String)

data class ReglaResponse(val codigo: String, val titulo: String, val detalle: String)

data class ClienteScoringResponse(
    val encontrado: Boolean,
    val entidadId: Int?,
    val tipoPersona: String?,
    val tipoDocumento: String?,
    val documento: String,
    val nombre: String?,
    val consulta: ConsultaListasResponse,
    val condicion: CondicionResponse,
    val reglas: List<ReglaResponse>
)

// ---------------- Datos de la evaluacion (vista previa y evaluar) ----------------

data class ClienteNuevoRequest(
    /** NATURAL / JURIDICA */
    val tipoPersona: String? = null,
    /** DNI / CE / RUC / PASAPORTE (los codigos de Listas Negativas) */
    val tipoDocumento: String? = null,
    val documento: String? = null,
    val nombre: String? = null
)

data class DatosScoringRequest(
    val entidadId: Int? = null,
    val cliente: ClienteNuevoRequest? = null,
    val ocupacionId: Int? = null,
    val montoOperacion: BigDecimal? = null,
    val productoId: Int? = null,
    val canalId: Int? = null,
    val medioPagoId: Int? = null,
    val departamentoResidenciaId: Int? = null,
    val departamentoOperacionId: Int? = null,
    val paisFondosId: Int? = null,
    val sustentoId: String? = null
)

data class BloqueoResponse(val campo: String, val mensaje: String)

data class GruposVistaPrevia(val clientes: BigDecimal?, val productos: BigDecimal?, val zona: BigDecimal?)

data class VistaPreviaResponse(
    val grupos: GruposVistaPrevia,
    val puntaje: BigDecimal?,
    val formula: String,
    val montoNota: String?,
    val bloqueos: List<BloqueoResponse>
)

// ---------------- Resultado (evaluar, GET /{id}, decision) ----------------

data class ClienteResultado(
    val nombre: String?,
    val tipoDocumento: String?,
    val documento: String?,
    val tipoPersona: String?,
    val ocupacion: String?,
    val departamento: String?
)

data class AvisoReglaResponse(val titulo: String, val detalle: String)

data class FactorResultado(
    val codigo: String,
    val nombre: String,
    val dato: String?,
    val puntaje: Int?,
    val peso: Int,
    val aporte: BigDecimal?,
    val automatico: Boolean
)

data class GrupoResultado(
    val codigo: String,
    val nombre: String,
    val peso: Int,
    val promedio: BigDecimal?,
    val factores: List<FactorResultado>
)

data class QueHacerResponse(
    val debidaDiligencia: String,
    val decide: String,
    val revisionMeses: Int,
    val proximaRevision: LocalDate?,
    val reunir: List<String>
)

data class DecisionResponse(
    val accion: String,
    val comentario: String?,
    val por: String?,
    val fecha: LocalDateTime?
)

data class ResultadoScoringResponse(
    val id: Int,
    val version: Int,
    val fecha: LocalDateTime,
    val cliente: ClienteResultado,
    val puntaje: BigDecimal?,
    /** BAJO / MEDIO / ALTO; null en las evaluaciones de la version 1 */
    val nivel: String?,
    val nivelPorPromedio: String?,
    val fijadoPorRegla: Boolean,
    val avisoRegla: AvisoReglaResponse?,
    val grupos: List<GrupoResultado>,
    val reglas: List<ReglaResponse>,
    val queHacer: QueHacerResponse?,
    val decision: DecisionResponse?,
    // Extras (no estaban en el contrato; el front puede ignorarlos)
    /** Solo version 1: la categoria de entonces ("Riesgo Medio", etc.) */
    val categoria: String? = null,
    val entidadId: Int? = null,
    val sustentoId: String? = null,
    val montoNota: String? = null,
    val consulta: ConsultaListasResponse? = null
)

// ---------------- POST /{id}/decision ----------------

data class DecisionRequest(
    /** APROBAR (Bajo o Medio) / ENVIAR_A_APROBACION (Alto) / RECHAZAR (cualquiera) */
    val accion: String? = null,
    val comentario: String? = null
)

// ---------------- GET /historial ----------------

data class FactorHistorial(
    val nombre: String,
    val valor: String?,
    val puntaje: Int?,
    val peso: Int,
    val puntajePonderado: BigDecimal?
)

data class HistorialScoringResponse(
    val id: Int,
    val version: Int,
    val entidadId: Int?,
    val nombreCompleto: String,
    val documento: String?,
    val factores: List<FactorHistorial>,
    val puntajeTotal: BigDecimal?,
    /** Version 1: la categoria de entonces; version 2: Bajo / Medio / Alto */
    val categoria: String?,
    /** BAJO / MEDIO / ALTO; null en la version 1 */
    val nivel: String?,
    val decision: DecisionResponse?,
    val fechaCreacion: LocalDateTime
)

// ---------------- POST /sustentos ----------------

data class SustentoResponse(val id: String)
