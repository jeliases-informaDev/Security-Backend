package com.security.modules.oficial.persistencia

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.Immutable
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Tablas del modulo Oficial de Cumplimiento (prefijo oc_). Se crean con ddl-auto: update, como el resto del proyecto.
 * Todas llevan empresa_id (empresas.id) y nunca se leen sin filtrar por empresa en el servidor. Las relaciones se guardan
 * como ids sueltos (sin @ManyToOne) para no depender de proxies perezosos. Los enums se guardan como texto (su codigo).
 */

/** Una designacion de oficial (hoy solo titular). Los codigos secretos se guardan cifrados (AES-256-GCM). */
@Entity
@Table(
    name = "oc_designacion",
    indexes = [Index(name = "ix_oc_designacion_empresa", columnList = "empresa_id")]
)
class OcDesignacion(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    var uuid: String = "",

    @Column(name = "empresa_id", nullable = false)
    var empresaId: Int = 0,

    /** = empresa_id mientras sea el titular activo (pendiente, en evaluacion o vigente); null si no. Unico: un solo titular activo. */
    @Column(name = "titular_activo_empresa", unique = true)
    var titularActivoEmpresa: Int? = null,

    @Column(name = "estado", nullable = false, length = 30)
    var estado: String = "",

    @Column(name = "tipo_cargo", nullable = false, length = 30)
    var tipoCargo: String = "",

    @Column(name = "dedicacion", nullable = false, length = 30)
    var dedicacion: String = "",

    @Column(name = "tipo_empresa", nullable = false, length = 30)
    var tipoEmpresa: String = "",

    // Persona
    @Column(name = "nombres", nullable = false, length = 150) var nombres: String = "",
    @Column(name = "apellidos", nullable = false, length = 150) var apellidos: String = "",
    @Column(name = "nacionalidad", nullable = false, length = 2) var nacionalidad: String = "",
    @Column(name = "tipo_documento", nullable = false, length = 20) var tipoDocumento: String = "",
    @Column(name = "numero_documento", nullable = false, length = 20) var numeroDocumento: String = "",
    @Column(name = "fecha_nacimiento", nullable = false) var fechaNacimiento: LocalDate = LocalDate.MIN,
    @Column(name = "domicilio", nullable = false, length = 255) var domicilio: String = "",
    @Column(name = "telefono", length = 30) var telefono: String? = null,
    @Column(name = "correo", nullable = false, length = 150) var correo: String = "",

    // Cargo en la empresa
    @Column(name = "cargo", nullable = false, length = 150) var cargo: String = "",
    @Column(name = "fecha_ingreso", nullable = false) var fechaIngreso: LocalDate = LocalDate.MIN,
    @Column(name = "vinculo", nullable = false, length = 30) var vinculo: String = "",
    @Column(name = "direccion_oficina", nullable = false, length = 255) var direccionOficina: String = "",

    // Designacion
    @Column(name = "designa", nullable = false, length = 30) var designa: String = "",
    @Column(name = "nombre_organo", nullable = false, length = 255) var nombreOrgano: String = "",
    @Column(name = "documento_tipo", nullable = false, length = 30) var documentoTipo: String = "",
    @Column(name = "documento_fecha", nullable = false) var documentoFecha: LocalDate = LocalDate.MIN,
    @Column(name = "fecha_designacion", nullable = false) var fechaDesignacion: LocalDate = LocalDate.MIN,
    @Column(name = "es_oficial_otra_empresa", nullable = false) var esOficialOtraEmpresa: Boolean = false,
    @Column(name = "reemplaza_oficial_anterior", nullable = false) var reemplazaOficialAnterior: Boolean = false,
    @Column(name = "reemplaza_a_id") var reemplazaAId: Long? = null,
    @Column(name = "acta_adjunto_id") var actaAdjuntoId: Long? = null,
    @Column(name = "hoja_vida_adjunto_id") var hojaVidaAdjuntoId: Long? = null,
    @Column(name = "documento_vinculo_adjunto_id") var documentoVinculoAdjuntoId: Long? = null,

    // Declaracion jurada de requisitos (art. 6.1)
    @Column(name = "dj_req_puntos", nullable = false, length = 40) var djReqPuntos: String = "",
    @Column(name = "dj_req_firma_nombre", length = 255) var djReqFirmaNombre: String? = null,
    @Column(name = "dj_req_fecha_firma") var djReqFechaFirma: LocalDate? = null,
    @Column(name = "dj_req_adjunto_id") var djReqAdjuntoId: Long? = null,
    @Column(name = "dj_texto_version", nullable = false, length = 20) var djTextoVersion: String = "",

    // Declaracion jurada de quien designo (art. 7.3)
    @Column(name = "dj_des_aceptada", nullable = false) var djDesAceptada: Boolean = false,
    @Column(name = "dj_des_firma_nombre", length = 255) var djDesFirmaNombre: String? = null,
    @Column(name = "dj_des_fecha_firma") var djDesFechaFirma: LocalDate? = null,
    @Column(name = "dj_des_adjunto_id") var djDesAdjuntoId: Long? = null,

    // Comunicacion a la UIF (un solo registro que se actualiza; el historial guarda cada paso)
    @Column(name = "com_fecha") var comFecha: LocalDate? = null,
    @Column(name = "com_numero_solicitud", length = 50) var comNumeroSolicitud: String? = null,
    @Column(name = "com_resultado", length = 30) var comResultado: String? = null,
    @Column(name = "com_fecha_notificacion") var comFechaNotificacion: LocalDate? = null,
    @Column(name = "com_motivo_no_procedente", length = 1000) var comMotivoNoProcedente: String? = null,
    @Column(name = "com_constancia_adjunto_id") var comConstanciaAdjuntoId: Long? = null,
    @Column(name = "com_registrada_en") var comRegistradaEn: LocalDateTime? = null,

    // Codigos secretos: SOLO cifrados. Nunca se devuelven.
    @Column(name = "codigo_empresa_cifrado", length = 512) var codigoEmpresaCifrado: String? = null,
    @Column(name = "codigo_oficial_cifrado", length = 512) var codigoOficialCifrado: String? = null,
    @Column(name = "codigos_registrados_en") var codigosRegistradosEn: LocalDateTime? = null,

    // Sello y autoria
    @Column(name = "guardada_en", nullable = false) var guardadaEn: LocalDateTime = LocalDateTime.MIN,
    @Column(name = "guardada_por_id") var guardadaPorId: Int? = null,
    @Column(name = "guardada_por_usuario", length = 150) var guardadaPorUsuario: String? = null,
    @Column(name = "guardada_por_nombre", length = 255) var guardadaPorNombre: String? = null,
    @Column(name = "removida_en") var removidaEn: LocalDateTime? = null,
    @Column(name = "actualizada_en", nullable = false) var actualizadaEn: LocalDateTime = LocalDateTime.MIN,

    @Version
    @Column(name = "version")
    var version: Long? = null
)

/** Archivo subido (acta, declaraciones firmadas, constancias). El archivo vive en el almacenamiento de las denuncias. */
@Entity
@Table(
    name = "oc_adjunto",
    indexes = [Index(name = "ix_oc_adjunto_empresa", columnList = "empresa_id")]
)
class OcAdjunto(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "uuid", nullable = false, unique = true, length = 36) var uuid: String = "",
    @Column(name = "empresa_id", nullable = false) var empresaId: Int = 0,
    @Column(name = "ambito", nullable = false, length = 40) var ambito: String = "",
    /** false = suelto (se puede borrar); true = ya lo usa una designacion, remocion o comunicacion. */
    @Column(name = "vinculado", nullable = false) var vinculado: Boolean = false,
    @Column(name = "designacion_id") var designacionId: Long? = null,
    @Column(name = "nombre_original", nullable = false, length = 255) var nombreOriginal: String = "",
    @Column(name = "nombre_archivo", nullable = false, length = 255) var nombreArchivo: String = "",
    @Column(name = "tipo_contenido", length = 150) var tipoContenido: String? = null,
    @Column(name = "ruta_archivo", nullable = false, length = 1024) var rutaArchivo: String = "",
    @Column(name = "tamano_bytes") var tamanoBytes: Long? = null,
    @Column(name = "proveedor_almacenamiento", length = 20) var proveedorAlmacenamiento: String? = null,
    @Column(name = "bucket", length = 255) var bucket: String? = null,
    @Column(name = "sha256", length = 64) var sha256: String? = null,
    @Column(name = "subido_por_id") var subidoPorId: Int? = null,
    @Column(name = "subido_por_usuario", length = 150) var subidoPorUsuario: String? = null,
    @Column(name = "subido_en", nullable = false) var subidoEn: LocalDateTime = LocalDateTime.MIN
)

/** Remocion de un oficial (OC 12). */
@Entity
@Table(
    name = "oc_remocion",
    indexes = [Index(name = "ix_oc_remocion_empresa", columnList = "empresa_id")]
)
class OcRemocion(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "uuid", nullable = false, unique = true, length = 36) var uuid: String = "",
    @Column(name = "empresa_id", nullable = false) var empresaId: Int = 0,
    @Column(name = "designacion_id", nullable = false) var designacionId: Long = 0,
    @Column(name = "motivo", nullable = false, length = 30) var motivo: String = "",
    @Column(name = "razones", nullable = false, length = 2000) var razones: String = "",
    @Column(name = "aprobada_por", length = 255) var aprobadaPor: String? = null,
    @Column(name = "fecha_decision", nullable = false) var fechaDecision: LocalDate = LocalDate.MIN,
    @Column(name = "acta_adjunto_id") var actaAdjuntoId: Long? = null,
    @Column(name = "registrada_en", nullable = false) var registradaEn: LocalDateTime = LocalDateTime.MIN,
    @Column(name = "registrada_por_usuario", length = 150) var registradaPorUsuario: String? = null,
    @Column(name = "registrada_por_nombre", length = 255) var registradaPorNombre: String? = null
)

/** Algo que hay que comunicar a la UIF (cambio de datos o remocion) y si ya se comunico. */
@Entity
@Table(
    name = "oc_pendiente",
    indexes = [Index(name = "ix_oc_pendiente_empresa", columnList = "empresa_id")]
)
class OcPendiente(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "uuid", nullable = false, unique = true, length = 36) var uuid: String = "",
    @Column(name = "empresa_id", nullable = false) var empresaId: Int = 0,
    @Column(name = "designacion_id", nullable = false) var designacionId: Long = 0,
    @Column(name = "tipo", nullable = false, length = 30) var tipo: String = "",
    @Column(name = "titulo", nullable = false, length = 255) var titulo: String = "",
    @Column(name = "detalle", length = 2000) var detalle: String? = null,
    @Column(name = "regla", nullable = false, length = 60) var regla: String = "",
    @Column(name = "desde", nullable = false) var desde: LocalDate = LocalDate.MIN,
    @Column(name = "remocion_id") var remocionId: Long? = null,
    @Column(name = "comunicado", nullable = false) var comunicado: Boolean = false,
    @Column(name = "com_fecha") var comFecha: LocalDate? = null,
    @Column(name = "com_numero_solicitud", length = 50) var comNumeroSolicitud: String? = null,
    @Column(name = "com_nota", length = 500) var comNota: String? = null,
    @Column(name = "com_constancia_adjunto_id") var comConstanciaAdjuntoId: Long? = null,
    @Column(name = "com_registrada_en") var comRegistradaEn: LocalDateTime? = null,
    @Column(name = "com_registrada_por_usuario", length = 150) var comRegistradaPorUsuario: String? = null,
    @Column(name = "com_registrada_por_nombre", length = 255) var comRegistradaPorNombre: String? = null,
    @Column(name = "creado_en", nullable = false) var creadoEn: LocalDateTime = LocalDateTime.MIN,
    @Column(name = "creado_por_usuario", length = 150) var creadoPorUsuario: String? = null,
    @Column(name = "creado_por_nombre", length = 255) var creadoPorNombre: String? = null
)

/**
 * Historial del oficial: SOLO inserciones. @Immutable hace que Hibernate ignore cualquier cambio y el repositorio no
 * expone ni actualizar ni borrar. Nunca guarda codigos secretos.
 */
@Entity
@Immutable
@Table(
    name = "oc_evento",
    indexes = [Index(name = "ix_oc_evento_designacion", columnList = "designacion_id")]
)
class OcEvento(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "uuid", nullable = false, unique = true, length = 36, updatable = false) var uuid: String = "",
    @Column(name = "empresa_id", nullable = false, updatable = false) var empresaId: Int = 0,
    @Column(name = "designacion_id", nullable = false, updatable = false) var designacionId: Long = 0,
    @Column(name = "pendiente_id", updatable = false) var pendienteId: Long? = null,
    @Column(name = "tipo", nullable = false, length = 40, updatable = false) var tipo: String = "",
    @Column(name = "titulo", nullable = false, length = 255, updatable = false) var titulo: String = "",
    @Column(name = "detalle", length = 2000, updatable = false) var detalle: String? = null,
    @Column(name = "motivo", length = 2000, updatable = false) var motivo: String? = null,
    @Column(name = "etiqueta", length = 30, updatable = false) var etiqueta: String? = null,
    @Column(name = "usuario_id", updatable = false) var usuarioId: Int? = null,
    @Column(name = "usuario", length = 150, updatable = false) var usuario: String? = null,
    @Column(name = "usuario_nombre", length = 255, updatable = false) var usuarioNombre: String? = null,
    @Column(name = "fecha_hora", nullable = false, updatable = false) var fechaHora: LocalDateTime = LocalDateTime.MIN
)

/** Un «antes → después» de un evento (tambien solo inserciones). */
@Entity
@Immutable
@Table(
    name = "oc_evento_cambio",
    indexes = [Index(name = "ix_oc_evento_cambio_evento", columnList = "evento_id")]
)
class OcEventoCambio(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "evento_id", nullable = false, updatable = false) var eventoId: Long = 0,
    @Column(name = "orden", nullable = false, updatable = false) var orden: Int = 0,
    @Column(name = "campo", nullable = false, length = 80, updatable = false) var campo: String = "",
    @Column(name = "etiqueta", nullable = false, length = 150, updatable = false) var etiqueta: String = "",
    @Column(name = "antes", length = 500, updatable = false) var antes: String? = null,
    @Column(name = "despues", length = 500, updatable = false) var despues: String? = null
)

/** Regla de plazos como dato (el administrador de Sulotec puede cambiar el valor). */
@Entity
@Table(name = "oc_regla")
class OcRegla(
    @Id
    @Column(name = "codigo", length = 60)
    var codigo: String = "",
    @Column(name = "valor", nullable = false) var valor: Int = 0,
    @Column(name = "unidad", nullable = false, length = 20) var unidad: String = "",
    @Column(name = "articulo", length = 100) var articulo: String? = null,
    @Column(name = "descripcion", length = 255) var descripcion: String? = null,
    @Column(name = "actualizada_en") var actualizadaEn: LocalDateTime? = null,
    @Column(name = "actualizada_por", length = 150) var actualizadaPor: String? = null
)

/** Feriado nacional (lo mantiene el administrador de Sulotec; solo los confirmados en una fuente oficial). */
@Entity
@Table(name = "oc_feriado")
class OcFeriado(
    @Id
    @Column(name = "fecha")
    var fecha: LocalDate = LocalDate.MIN,
    @Column(name = "nombre", nullable = false, length = 150) var nombre: String = "",
    @Column(name = "fuente", nullable = false, length = 255) var fuente: String = "",
    @Column(name = "creado_en") var creadoEn: LocalDateTime? = null,
    @Column(name = "creado_por", length = 150) var creadoPor: String? = null
)
