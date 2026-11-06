package com.security.modules.operaciones.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Un evento del historial de un registro de operaciones (creacion, edicion, responsable, descarga del PDF).
 * Solo se inserta: ninguna columna es actualizable y el repositorio no tiene metodos para cambiar ni borrar.
 * Guarda una copia de quien lo hizo (usuario y nombre en ese momento) y, en una edicion, cada campo cambiado
 * con su etiqueta legible y sus valores antes y despues, listos para pintar.
 */
@Entity
@Table(
    name = "historial_operaciones",
    indexes = [Index(name = "idx_hist_op_registro", columnList = "registro_id, fecha")]
)
class EventoOperacionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    val id: Long? = null,

    @Column(name = "registro_id", nullable = false, updatable = false)
    val registroId: Long,

    @Column(name = "tipo", nullable = false, length = 20, updatable = false)
    val tipo: String,

    @Column(name = "titulo", nullable = false, length = 150, updatable = false)
    val titulo: String,

    @Column(name = "detalle", columnDefinition = "TEXT", updatable = false)
    val detalle: String? = null,

    @Column(name = "motivo", length = 300, updatable = false)
    val motivo: String? = null,

    @Column(name = "id_usuario", updatable = false)
    val idUsuario: Int? = null,

    @Column(name = "usuario", length = 150, updatable = false)
    val usuario: String? = null,

    @Column(name = "nombre_usuario", length = 255, updatable = false)
    val nombreUsuario: String? = null,

    @Column(name = "fecha", nullable = false, updatable = false)
    val fecha: LocalDateTime,

    @Column(name = "codigo_verificacion", length = 20, updatable = false)
    val codigoVerificacion: String? = null,

    @Column(name = "huella_sha256", length = 64, updatable = false)
    val huellaSha256: String? = null,

    cambios: List<CambioCampo> = emptyList()
) {
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "historial_operaciones_cambios", joinColumns = [JoinColumn(name = "id_evento")])
    @OrderColumn(name = "orden")
    val cambios: MutableList<CambioCampo> = cambios.toMutableList()

    companion object {
        const val CREACION = "CREACION"
        const val EDICION = "EDICION"
        const val RESPONSABLE = "RESPONSABLE"
        const val DESCARGA = "DESCARGA"
        const val ANALISIS = "ANALISIS"
        const val UMBRAL = "UMBRAL"
        const val ENVIO = "ENVIO"
        // Solo en la respuesta: registros que ya existian antes del historial (no se guarda)
        const val ANTERIOR = "ANTERIOR"
    }
}

/** Un campo cambiado: `campo` es la ruta tecnica (operacion.monto), `etiqueta` la que se muestra (Monto). */
@Embeddable
class CambioCampo(
    @Column(name = "campo", nullable = false, length = 120)
    var campo: String = "",

    @Column(name = "etiqueta", nullable = false, length = 150)
    var etiqueta: String = "",

    @Column(name = "antes", columnDefinition = "TEXT")
    var antes: String? = null,

    @Column(name = "despues", columnDefinition = "TEXT")
    var despues: String? = null
)

/**
 * Ultimo correlativo usado por empresa y año (RO-AAAA-NNNNNN). Se lee y se incrementa con bloqueo de fila
 * (NumeradorRegistros), dentro de la misma transaccion que guarda el registro: dos altas a la vez no repiten numero.
 * `empresa` va en minusculas y sin espacios sobrantes.
 */
@Entity
@Table(
    name = "contador_registro_operaciones",
    uniqueConstraints = [UniqueConstraint(name = "uk_contador_ro_empresa_anio", columnNames = ["empresa", "anio"])]
)
class ContadorRegistroOperaciones(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "empresa", nullable = false, length = 255)
    val empresa: String,

    @Column(name = "anio", nullable = false)
    val anio: Int,

    @Column(name = "ultimo", nullable = false)
    var ultimo: Int = 0
)

/**
 * Opciones de los desplegables del formulario nuevo (modalidad de pago, origen de los fondos, estado civil, tipo de
 * documento y tipos de operacion por sector), tal como vienen en las fichas «para desarrollo» del diseño. Se siembran
 * desde operaciones/catalogos-registro.csv (solo se agrega lo que falta).
 */
@Entity
@Table(
    name = "catalogo_operaciones",
    uniqueConstraints = [UniqueConstraint(name = "uk_catalogo_op_codigo", columnNames = ["catalogo", "codigo"])]
)
class CatalogoOperacionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "catalogo", nullable = false, length = 40)
    val catalogo: String,

    @Column(name = "codigo", nullable = false, length = 60)
    val codigo: String,

    @Column(name = "nombre", nullable = false, length = 150)
    var nombre: String,

    @Column(name = "orden", nullable = false)
    var orden: Int = 0,

    @Column(name = "pide_texto", nullable = false)
    var pideTexto: Boolean = false,

    // TIPO_OPERACION: grupo de tipos (sector). SECTOR: grupo de tipos de operacion y umbrales que usa (vacio = por configurar).
    @Column(name = "sector", length = 40)
    var sector: String? = null,

    // Solo TIPO_OPERACION: tipo de bien que define el umbral (LOTE_TERRENO, UNIDAD_INMOBILIARIA)
    @Column(name = "tipo_bien", length = 40)
    var tipoBien: String? = null,

    @Column(name = "activo", nullable = false)
    var activo: Boolean = true
)
