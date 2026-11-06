package com.security.modules.operaciones.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Umbral de la norma por grupo de sector y tipo de bien (Res. SBS 789-2018, art. 41 num. 10.2, segun el diseño de RO 16).
 * Se siembra desde operaciones/umbrales-norma.csv. Un grupo sin filas = sector «Por configurar».
 */
@Entity
@Table(
    name = "umbral_operaciones",
    uniqueConstraints = [UniqueConstraint(name = "uk_umbral_op_grupo_bien", columnNames = ["grupo", "tipo_bien"])]
)
class UmbralNormaEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "grupo", nullable = false, length = 40)
    val grupo: String,
    @Column(name = "tipo_bien", nullable = false, length = 40)
    val tipoBien: String,
    @Column(name = "nombre", nullable = false, length = 200)
    var nombre: String,
    @Column(name = "umbral_usd", nullable = false, precision = 15, scale = 2)
    var umbralUsd: BigDecimal,
    @Column(name = "orden", nullable = false)
    var orden: Int = 0
)

/** Configuracion del Registro de Operaciones de una empresa (RO 16). `empresa` en minusculas y sin espacios sobrantes. */
@Entity
@Table(
    name = "configuracion_operaciones",
    uniqueConstraints = [UniqueConstraint(name = "uk_config_op_empresa", columnNames = ["empresa"])]
)
class ConfiguracionOperacionesEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "empresa", nullable = false, length = 255)
    val empresa: String,
    @Column(name = "sector", length = 60)
    var sector: String? = null,
    @Column(name = "un_ro_por_actividad", nullable = false)
    var unRoPorActividad: Boolean = false,
    @Column(name = "umbral_interno_usd", precision = 15, scale = 2)
    var umbralInternoUsd: BigDecimal? = null,
    @Column(name = "umbral_interno_motivo", length = 300)
    var umbralInternoMotivo: String? = null,
    @Column(name = "actualizado_en")
    var actualizadoEn: LocalDateTime? = null,
    @Column(name = "actualizado_por", length = 255)
    var actualizadoPor: String? = null
) {
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "configuracion_operaciones_oficinas", joinColumns = [JoinColumn(name = "id_configuracion")])
    @OrderColumn(name = "orden")
    @Column(name = "oficina", nullable = false, length = 100)
    var oficinas: MutableList<String> = mutableListOf()
}

/**
 * Tipo de cambio mensual (soles por 1 unidad de la moneda, promedio de venta del mes `mes`), cargado a mano.
 * `empresa` = "" es el que carga Sulotec para todas; si una empresa carga el suyo, manda el de ella.
 */
@Entity
@Table(
    name = "tipo_cambio_operaciones",
    uniqueConstraints = [UniqueConstraint(name = "uk_tc_op_empresa_moneda_mes", columnNames = ["empresa", "moneda", "mes"])]
)
class TipoCambioOperacionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "empresa", nullable = false, length = 255)
    val empresa: String,
    @Column(name = "moneda", nullable = false, length = 10)
    val moneda: String,
    @Column(name = "mes", nullable = false, length = 7)
    val mes: String,
    @Column(name = "valor", nullable = false, precision = 10, scale = 6)
    var valor: BigDecimal,
    @Column(name = "fuente", length = 200)
    var fuente: String? = null,
    @Column(name = "cargado_por", length = 255)
    var cargadoPor: String? = null,
    @Column(name = "cargado_en")
    var cargadoEn: LocalDateTime? = null
) {
    companion object {
        const val SULOTEC = ""
    }
}

/** Analisis de inusualidad de un registro (RO 12) y constancia del ROS (RO 13). Uno por registro. */
@Entity
@Table(
    name = "analisis_operaciones",
    uniqueConstraints = [UniqueConstraint(name = "uk_analisis_op_registro", columnNames = ["registro_id"])]
)
class AnalisisOperacionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "registro_id", nullable = false)
    val registroId: Long,
    // EN_ANALISIS, DESCARTADA o SOSPECHOSA
    @Column(name = "estado", nullable = false, length = 20)
    var estado: String,
    @Column(name = "senal", length = 60) var senal: String? = null,
    @Column(name = "senal_texto", length = 300) var senalTexto: String? = null,
    @Column(name = "fuente", length = 60) var fuente: String? = null,
    @Column(name = "fecha_deteccion") var fechaDeteccion: LocalDate? = null,
    @Column(name = "descripcion_senal", columnDefinition = "TEXT") var descripcionSenal: String? = null,
    @Column(name = "descripcion_operacion", columnDefinition = "TEXT") var descripcionOperacion: String? = null,
    @Column(name = "analisis", columnDefinition = "TEXT") var analisis: String? = null,
    @Column(name = "decision", length = 20) var decision: String? = null,
    @Column(name = "calificada_en") var calificadaEn: LocalDateTime? = null,
    @Column(name = "calificada_por", length = 255) var calificadaPor: String? = null,
    @Column(name = "vence_en") var venceEn: LocalDateTime? = null,
    @Column(name = "numero_ros", length = 50) var numeroRos: String? = null,
    @Column(name = "ros_enviado_en") var rosEnviadoEn: LocalDateTime? = null,
    @Column(name = "ros_registrado_en") var rosRegistradoEn: LocalDateTime? = null,
    @Column(name = "ros_registrado_por", length = 255) var rosRegistradoPor: String? = null,
    @Column(name = "actualizado_en") var actualizadoEn: LocalDateTime? = null,
    @Column(name = "actualizado_por", length = 255) var actualizadoPor: String? = null
) {
    companion object {
        const val EN_ANALISIS = "EN_ANALISIS"
        const val DESCARTADA = "DESCARTADA"
        const val SOSPECHOSA = "SOSPECHOSA"
        const val SIN_ANALISIS = "SIN_ANALISIS"
    }
}

/** Archivo adjunto del analisis (no se borra: queda como constancia). */
@Entity
@Table(name = "analisis_operaciones_adjuntos", indexes = [Index(name = "idx_adj_op_registro", columnList = "registro_id")])
class AdjuntoAnalisisEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "registro_id", nullable = false, updatable = false) val registroId: Long,
    @Column(name = "nombre", nullable = false, length = 255, updatable = false) val nombre: String,
    @Column(name = "tipo_contenido", length = 150, updatable = false) val tipoContenido: String?,
    @Column(name = "tamano_bytes", nullable = false, updatable = false) val tamanoBytes: Long,
    @Column(name = "ruta", nullable = false, length = 500, updatable = false) val ruta: String,
    @Column(name = "sha256", length = 64, updatable = false) val sha256: String?,
    @Column(name = "subido_en", nullable = false, updatable = false) val subidoEn: LocalDateTime,
    @Column(name = "subido_por", length = 255, updatable = false) val subidoPor: String?
)

/**
 * Archivo «RO a disposicion de la UIF» de un periodo (RO 14) y su constancia de envio (RO 15). Los archivos se guardan al
 * generarlos (foto de ese momento) en el mismo almacenamiento que las denuncias.
 */
@Entity
@Table(name = "exportacion_operaciones", indexes = [Index(name = "idx_export_op_empresa", columnList = "empresa, generado_en")])
class ExportacionOperacionesEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "empresa", nullable = false, length = 255) val empresa: String,
    @Column(name = "periodo", nullable = false, length = 7) val periodo: String,
    @Column(name = "formato", nullable = false, length = 40) val formato: String,
    @Column(name = "generado_en", nullable = false) val generadoEn: LocalDateTime,
    @Column(name = "generado_por", length = 255) val generadoPor: String?,
    @Column(name = "ruta_xlsx", length = 500) var rutaXlsx: String? = null,
    @Column(name = "ruta_csv", length = 500) var rutaCsv: String? = null,
    @Column(name = "nombre_base", length = 120) var nombreBase: String? = null,
    @Column(name = "fecha_envio") var fechaEnvio: LocalDate? = null,
    @Column(name = "numero_acuse", length = 100) var numeroAcuse: String? = null,
    @Column(name = "notas", columnDefinition = "TEXT") var notas: String? = null,
    @Column(name = "constancia_nombre", length = 255) var constanciaNombre: String? = null,
    @Column(name = "constancia_ruta", length = 500) var constanciaRuta: String? = null,
    @Column(name = "constancia_tipo", length = 150) var constanciaTipo: String? = null,
    @Column(name = "constancia_registrada_en") var constanciaRegistradaEn: LocalDateTime? = null,
    @Column(name = "constancia_registrada_por", length = 255) var constanciaRegistradaPor: String? = null,
    registros: List<Long> = emptyList()
) {
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exportacion_operaciones_registros", joinColumns = [JoinColumn(name = "id_exportacion")])
    @Column(name = "registro_id", nullable = false)
    val registros: MutableList<Long> = registros.toMutableList()
}
