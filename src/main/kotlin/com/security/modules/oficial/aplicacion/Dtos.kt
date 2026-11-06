package com.security.modules.oficial.aplicacion

import com.security.modules.oficial.dominio.Plazo
import java.time.LocalDate
import java.time.LocalDateTime

// ============================================================
// PEDIDOS
// ============================================================

data class PersonaRequest(
    val nombres: String? = null,
    val apellidos: String? = null,
    val nacionalidad: String? = null,
    val tipoDocumento: String? = null,
    val numeroDocumento: String? = null,
    val fechaNacimiento: LocalDate? = null,
    val domicilio: String? = null,
    val telefono: String? = null,
    val correo: String? = null
)

data class CargoRequest(
    val cargo: String? = null,
    val fechaIngreso: LocalDate? = null,
    val vinculo: String? = null,
    val direccionOficina: String? = null
)

data class DatosDesignacionRequest(
    val designa: String? = null,
    val nombreOrgano: String? = null,
    val documentoTipo: String? = null,
    val documentoFecha: LocalDate? = null,
    val fechaDesignacion: LocalDate? = null,
    val adjuntoId: String? = null,
    val hojaVidaAdjuntoId: String? = null,
    val documentoVinculoAdjuntoId: String? = null,
    val esOficialOtraEmpresa: Boolean? = null,
    val reemplazaOficialAnterior: Boolean? = null
)

data class DjRequisitosRequest(
    val puntos: List<String>? = null,
    val firmaNombre: String? = null,
    val fechaFirma: LocalDate? = null,
    val adjuntoId: String? = null
)

data class DjDesignanteRequest(
    val aceptada: Boolean? = null,
    val firmaNombre: String? = null,
    val fechaFirma: LocalDate? = null,
    val adjuntoId: String? = null
)

data class DeclaracionesRequest(
    val requisitos: DjRequisitosRequest? = null,
    val designante: DjDesignanteRequest? = null
)

data class DesignacionRequest(
    val tipoEmpresa: String? = null,
    val tipoCargo: String? = null,
    val dedicacion: String? = null,
    val persona: PersonaRequest? = null,
    val cargo: CargoRequest? = null,
    val designacion: DatosDesignacionRequest? = null,
    val declaraciones: DeclaracionesRequest? = null,
    /** Solo en la correccion (PUT). */
    val motivo: String? = null
)

data class VistaPreviaRequest(
    val nombres: String? = null,
    val apellidos: String? = null,
    val tipoDocumento: String? = null,
    val numeroDocumento: String? = null,
    val tipoCargo: String? = null,
    val vinculo: String? = null,
    val fecha: LocalDate? = null
)

data class ComunicacionRequest(
    val fechaComunicacion: LocalDate? = null,
    val numeroSolicitud: String? = null,
    val resultado: String? = null,
    val fechaNotificacion: LocalDate? = null,
    val motivoNoProcedente: String? = null,
    val codigoEmpresa: String? = null,
    val codigoOficial: String? = null,
    val constanciaAdjuntoId: String? = null
)

data class CambioDatosRequest(
    val telefono: String? = null,
    val correo: String? = null,
    val domicilio: String? = null,
    val cargo: String? = null,
    val direccionOficina: String? = null,
    val fechaCambio: LocalDate? = null,
    val motivo: String? = null
)

data class RemocionRequest(
    val motivo: String? = null,
    val razones: String? = null,
    val aprobadaPor: String? = null,
    val fechaDecision: LocalDate? = null,
    val actaAdjuntoId: String? = null
)

data class ComunicacionPendienteRequest(
    val fecha: LocalDate? = null,
    val numeroSolicitud: String? = null,
    val nota: String? = null,
    val constanciaAdjuntoId: String? = null
)

// ============================================================
// RESPUESTAS
// ============================================================

data class AdjuntoResponse(
    val id: String,
    val ambito: String,
    val nombre: String,
    val tipoContenido: String?,
    val tamanoBytes: Long?,
    val subidoEn: LocalDateTime
)

data class EtiquetaOc(val codigo: String, val texto: String, val tono: String)

data class UsuarioOc(val usuario: String?, val nombreCompleto: String?)

data class EmpresaResponse(val uuid: String?, val nombre: String)

data class PermisosOc(
    val puedeDesignar: Boolean,
    val puedeCorregir: Boolean,
    val puedeComunicar: Boolean,
    val puedeCambiarDatos: Boolean,
    val puedeRemover: Boolean,
    val puedeRegistrarPendientes: Boolean
)

data class CambioCampo(val campo: String, val etiqueta: String, val antes: String?, val despues: String?)

data class ResumenDesignacion(
    val id: String,
    val estado: String,
    val estadoEtiqueta: String,
    val nombreCompleto: String,
    val iniciales: String,
    val tipoCargo: String,
    val tipoCargoNombre: String,
    val dedicacion: String,
    val dedicacionNombre: String,
    val documento: String,
    val fechaDesignacion: LocalDate,
    val guardadaEn: LocalDateTime,
    val designaNombre: String,
    val nombreOrgano: String,
    val resultadoUif: String?,
    val fechaNotificacion: LocalDate?
)

data class PersonaResponse(
    val nombres: String,
    val apellidos: String,
    val nacionalidad: String,
    val nacionalidadNombre: String?,
    val tipoDocumento: String,
    val tipoDocumentoNombre: String,
    val numeroDocumento: String,
    val documento: String,
    val fechaNacimiento: LocalDate,
    val domicilio: String,
    val telefono: String?,
    val correo: String
)

data class CargoResponse(
    val cargo: String,
    val fechaIngreso: LocalDate,
    val vinculo: String,
    val vinculoNombre: String,
    val direccionOficina: String
)

data class DesignacionDatosResponse(
    val designa: String,
    val designaNombre: String,
    val nombreOrgano: String,
    val documentoTipo: String,
    val documentoTipoNombre: String,
    val documentoFecha: LocalDate,
    val documentoTexto: String,
    val fechaDesignacion: LocalDate,
    val esOficialOtraEmpresa: Boolean,
    val reemplazaAId: String?,
    val adjunto: AdjuntoResponse?,
    val hojaVida: AdjuntoResponse?,
    val documentoVinculo: AdjuntoResponse?
)

data class DjRequisitosResponse(
    val completa: Boolean,
    val puntosAceptados: Int,
    val puntosTotal: Int,
    val firmaNombre: String?,
    val fechaFirma: LocalDate?,
    val adjunto: AdjuntoResponse?,
    val vigente: Boolean
)

data class DjDesignanteResponse(
    val completa: Boolean,
    val firmaNombre: String?,
    val fechaFirma: LocalDate?,
    val adjunto: AdjuntoResponse?,
    val vigente: Boolean
)

data class DeclaracionesResponse(val requisitos: DjRequisitosResponse, val designante: DjDesignanteResponse)

data class CodigosResponse(val reservados: Boolean, val registradosEn: LocalDateTime?, val cifradoDisponible: Boolean)

data class ComunicacionResponse(
    val fechaComunicacion: LocalDate?,
    val numeroSolicitud: String?,
    val resultado: String?,
    val resultadoNombre: String?,
    val fechaNotificacion: LocalDate?,
    val motivoNoProcedente: String?,
    val constancia: AdjuntoResponse?,
    val registradaEn: LocalDateTime?,
    val codigos: CodigosResponse
)

data class RemocionResponse(
    val id: String,
    val motivo: String,
    val motivoNombre: String,
    val razones: String,
    val aprobadaPor: String?,
    val fechaDecision: LocalDate,
    val acta: AdjuntoResponse?,
    val registradaEn: LocalDateTime,
    val registradaPor: UsuarioOc
)

data class ComunicacionPendienteResponse(
    val fecha: LocalDate?,
    val numeroSolicitud: String?,
    val nota: String?,
    val constancia: AdjuntoResponse?,
    val registradaEn: LocalDateTime?,
    val registradaPor: UsuarioOc
)

data class PendienteResponse(
    val id: String,
    val tipo: String,
    val designacionId: String?,
    val titulo: String,
    val detalle: String?,
    val plazo: Plazo?,
    val comunicado: Boolean,
    val comunicacion: ComunicacionPendienteResponse?,
    val creadoEn: LocalDateTime,
    val creadoPor: UsuarioOc
)

data class FichaResponse(
    val id: String,
    val estado: String,
    val estadoEtiqueta: String,
    val empresa: EmpresaResponse,
    val tipoEmpresa: String,
    val tipoEmpresaNombre: String,
    val tipoCargo: String,
    val tipoCargoNombre: String,
    val dedicacion: String,
    val dedicacionNombre: String,
    val nombreCompleto: String,
    val iniciales: String,
    val etiquetas: List<EtiquetaOc>,
    val persona: PersonaResponse,
    val cargo: CargoResponse,
    val designacion: DesignacionDatosResponse,
    val declaraciones: DeclaracionesResponse,
    val comunicacion: ComunicacionResponse?,
    val plazoComunicacion: Plazo?,
    val observacionUif: ObservacionUif?,
    val enElCargoDesde: LocalDate?,
    val revisionSugerida: LocalDate,
    val requisitosCargo: List<String>,
    val remocion: RemocionResponse?,
    val pendientes: List<PendienteResponse>,
    val alterno: Any?,
    val guardadaEn: LocalDateTime,
    val guardadaPor: UsuarioOc,
    val permisos: PermisosOc
)

data class ObservacionUif(val motivo: String?, val fechaNotificacion: LocalDate?)

data class VacanciaResponse(
    val designacionRemovidaId: String,
    val nombreCompleto: String,
    val motivo: String?,
    val motivoNombre: String?,
    val fechaDecision: LocalDate?,
    val registradaEn: LocalDateTime?,
    val aprobadaPor: String?,
    val plazoDesignar: Plazo?,
    val pendienteRemocionId: String?,
    val plazoComunicarRemocion: Plazo?,
    val remocionComunicada: Boolean,
    val hayAlternoComunicado: Boolean = false
)

data class EstadoResponse(
    val empresa: EmpresaResponse,
    val estado: String,
    val estadoEtiqueta: String,
    val tipoEmpresa: String?,
    val titular: ResumenDesignacion?,
    val plazoComunicacion: Plazo?,
    val observacionUif: ObservacionUif?,
    val vacancia: VacanciaResponse?,
    val pendientes: List<PendienteResponse>,
    val permisos: PermisosOc
)

data class EventoResponse(
    val id: String,
    val tipo: String,
    val titulo: String,
    val etiqueta: EtiquetaOc?,
    val detalle: String?,
    val motivo: String?,
    val cambios: List<CambioCampo>,
    val usuario: UsuarioOc,
    val fechaHora: LocalDateTime
)

data class VigenciaResponse(
    val estado: String,
    val estadoEtiqueta: String,
    val enElCargoDesde: LocalDate?,
    val revisionSugerida: LocalDate
)

data class HistorialResponse(
    val designacionId: String,
    val nombreCompleto: String,
    val eventos: List<EventoResponse>,
    val pendientesUif: List<PendienteResponse>,
    val vigencia: VigenciaResponse
)

data class PuntoHoja(val letra: String, val texto: String)

data class HojaDeclaracionResponse(
    val titulo: String,
    val encabezado: String,
    val puntos: List<PuntoHoja>,
    val lugarFecha: String,
    val firmaNombre: String,
    val firmaDocumento: String,
    val firmada: Boolean? = null,
    val fechaFirma: LocalDate? = null,
    val adjunto: AdjuntoResponse? = null,
    val vigente: Boolean? = null
)

data class DjDesignanteVerResponse(
    val titulo: String,
    val organo: String,
    val designaNombre: String,
    val documento: String,
    val texto: String,
    val aceptada: Boolean,
    val firmaNombre: String?,
    val fechaFirma: LocalDate?,
    val adjunto: AdjuntoResponse?,
    val vigente: Boolean
)

data class CambioDatosResponse(val cambios: List<CambioCampo>, val pendiente: PendienteResponse, val ficha: FichaResponse)

data class PlazosRemocionCreada(val comunicarRemocion: Plazo, val vacancia: Plazo)

data class RemocionCreadaResponse(
    val designacionId: String,
    val estadoEmpresa: String,
    val remocion: RemocionResponse,
    val pendiente: PendienteResponse,
    val plazos: PlazosRemocionCreada
)
