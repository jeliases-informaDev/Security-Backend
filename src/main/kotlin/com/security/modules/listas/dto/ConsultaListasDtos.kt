package com.security.modules.listas.dto

import com.security.modules.listas.application.service.TipoConsultaDef
import com.security.modules.listas.application.service.TipoDocumentoConsulta
import com.security.modules.listas.domain.reglas.FilaCatalogo
import com.security.modules.listas.domain.reglas.FuenteResultado
import com.security.modules.listas.domain.reglas.ResultadoListas
import java.time.LocalDate
import java.time.LocalDateTime

// Contrato completo con ejemplos: ver el archivo CONTRATO-LISTAS.md entregado con el cambio y docs/LISTAS-NEGATIVAS.md.

// ---------------- GET /consultas/catalogo ----------------

data class GrupoPesoResponse(val codigo: String, val nombre: String, val peso: Int, val critica: Boolean)

data class OtrasListasReglaResponse(val ordenGrupo: Int, val grupo: String?, val grupoPeso: String, val peso: Int)

data class CatalogoConsultaResponse(
    val tiposConsulta: List<TipoConsultaDef>,
    val tipoConsultaInicial: String?,
    val tiposDocumento: List<TipoDocumentoConsulta>,
    val tipoDocumentoInicial: String?,
    val gruposPeso: List<GrupoPesoResponse>,
    val leyenda: String,
    val filas: List<FilaCatalogo>,
    val fuentes: List<FuenteResultado>,
    val listasConsultables: Int,
    val otrasListas: List<OtrasListasReglaResponse>,
    val fechaBase: LocalDate?
)

// ---------------- POST /consultas ----------------

data class ConsultaListasRequest(
    /** CLIENTE (si falta), PROVEEDOR o TRABAJADOR_DIRECTOR */
    val tipoConsulta: String? = null,
    /** DNI, CE, PASAPORTE o RUC */
    val tipoDocumento: String? = null,
    val documento: String? = null,
    val nombres: String? = null,
    val apellidoPaterno: String? = null,
    val apellidoMaterno: String? = null
)

data class ElegirCandidatoRequest(val entidadId: Int? = null)

data class ComentarioRequest(val comentario: String? = null)

/** Lo que devuelve POST /consultas: una consulta lista o los candidatos para elegir. */
data class ConsultaCreadaResponse(
    /** CONSULTA o CANDIDATOS */
    val resultado: String,
    val consulta: ConsultaResponse?,
    val busqueda: BusquedaCandidatosResponse?
)

data class CandidatoResponse(
    val entidadId: Int,
    val nombre: String,
    val tipoDocumento: String?,
    val documento: String,
    val tipoPersona: String,
    val pais: String?,
    val alias: String?,
    val esPep: Boolean,
    val apariciones: Int,
    val listas: List<String>,
    /** La consulta ya creada al elegir este candidato en esta busqueda (abrirla no cobra), o null. */
    val consultaId: Long?
)

data class BusquedaCandidatosResponse(
    val id: Long,
    val fecha: LocalDateTime,
    val tipoConsulta: String,
    /** POR_DOCUMENTO (el documento esta repetido) o POR_NOMBRE */
    val modo: String,
    val criterio: CriterioResponse,
    val candidatos: List<CandidatoResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int
)

// ---------------- Consulta (POST /consultas, GET /consultas/{id}, elegir, homonimo) ----------------

data class CriterioResponse(
    val tipoDocumento: String?,
    val documento: String?,
    val nombres: String?,
    val apellidoPaterno: String?,
    val apellidoMaterno: String?,
    val texto: String?
)

data class UsuarioCortoResponse(val usuario: String, val nombre: String)

data class ConsultadoResponse(
    val entidadId: Int?,
    val nombre: String?,
    val tipoDocumento: String?,
    val documento: String?,
    /** NATURAL, JURIDICA o null si no esta en la base */
    val tipoPersona: String?,
    val pais: String?,
    val esPep: Boolean
)

data class PorQueCoincideResponse(
    /** EXACTA_DOCUMENTO, POR_NOMBRE o SIN_RESULTADOS */
    val tipo: String,
    val titulo: String,
    val detalle: String?,
    /** Texto de advertencia de una coincidencia por nombre (verificar que no sea homonimo); null en las demas. */
    val advertencia: String?
)

data class HomonimoResponse(
    val comentario: String?,
    val usuario: UsuarioCortoResponse?,
    val fecha: LocalDateTime?
)

data class DecisionRegistroResponse(
    /** HOMONIMO o DESHACER_HOMONIMO */
    val accion: String,
    val comentario: String?,
    val usuario: UsuarioCortoResponse,
    val fecha: LocalDateTime
)

data class AccionesResponse(
    val verRegistros: Boolean,
    val marcarHomonimo: Boolean,
    val deshacerHomonimo: Boolean,
    val reportePdf: Boolean,
    val continuarScoring: Boolean,
    val programarBusqueda: String,
    val consultaMasiva: String
)

/** Lo que necesita /scoring para abrir con esta persona ("Continuar al scoring"). */
data class ScoringDesdeConsultaResponse(
    val consultaId: Long,
    val entidadId: Int?,
    val clienteNuevo: Boolean,
    val tipoDocumento: String?,
    val documento: String?,
    val nombre: String?,
    val tipoPersona: String?
)

data class ConsultaResponse(
    val id: Long,
    val fecha: LocalDateTime,
    val tipoConsulta: String,
    val tipoConsultaNombre: String,
    val usuario: UsuarioCortoResponse,
    val criterio: CriterioResponse,
    val busquedaId: Long?,
    val consultado: ConsultadoResponse,
    val porQueCoincide: PorQueCoincideResponse,
    val resultado: ResultadoListas,
    /** VIGENTE o DESCARTADA_HOMONIMO */
    val estado: String,
    val estadoNombre: String,
    val homonimo: HomonimoResponse?,
    val decisiones: List<DecisionRegistroResponse>,
    val acciones: AccionesResponse,
    val scoring: ScoringDesdeConsultaResponse,
    val pieNoticias: String
)

// ---------------- GET /consultas (historial), /resumen, /inicio ----------------

data class ConsultaHistorialResponse(
    val id: Long,
    val fecha: LocalDateTime,
    val consultado: String?,
    val tipoDocumento: String?,
    val documento: String?,
    val tipoConsulta: String,
    val tipoConsultaNombre: String,
    val clasificacion: String?,
    val clasificacionNombre: String,
    val porcentaje: Int?,
    val listasConCoincidencia: List<String>,
    val usuario: String,
    val estado: String,
    val homonimo: Boolean
)

data class PaginaResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int
)

data class ResumenConsultasResponse(
    val periodo: String,
    val consultas: Long,
    val conCoincidencias: Long,
    val porcentajeConCoincidencias: Int?,
    val criticas: Long,
    val criticasSinDecidir: Long,
    val sinCoincidencias: Long,
    val porcentajeSinCoincidencias: Int?
)

data class RuedaResponse(val porcentaje: Int?, val consultaId: Long?, val clasificacion: String?)

data class InicioListasResponse(val ultimas: List<ConsultaHistorialResponse>, val rueda: RuedaResponse)
