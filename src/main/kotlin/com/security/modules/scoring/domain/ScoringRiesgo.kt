package com.security.modules.scoring.domain

import com.security.modules.listas.domain.Entidad
import com.security.shared.datos.entities.Pais
import com.security.shared.datos.entities.Usuario
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Una evaluacion de scoring. Las filas anteriores a la metodologia nueva quedan como version 1 (columna
 * `version_metodologia` con valor por defecto 1) y conservan su puntaje y su categoria de entonces.
 * Las de la version 2 guardan ademas los datos del cliente, cada opcion elegida, el resultado completo
 * (factores con dato, puntaje, peso y aporte, y reglas activadas) y la decision del analista.
 * Todas las columnas nuevas son opcionales: la tabla existente se actualiza sin tocar sus datos.
 */
@Entity
@Table(name = "scoring_riesgo")
class ScoringRiesgo(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    var usuario: Usuario,

    // null en la version 2 cuando el cliente no esta en la base de listas (sus datos van en cliente_*)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad")
    var entidad: Entidad? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ocupacion")
    var ocupacion: ScoringOcupacion,

    // Version 2: departamento de residencia
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_departamento")
    var departamento: ScoringDepartamento,

    // Version 1: NACIONAL / EXTRANJERO / PEP. Version 2: PEP / SIN_PEP
    @Column(name = "cliente_sensible_tipo", length = 20)
    var clienteSensibleTipo: String,

    // Version 1: volumen declarado. Version 2: el monto que se puntuo (el mayor entre la operacion y el promedio)
    @Column(name = "volumen_transaccional")
    var volumenTransaccional: BigDecimal,

    @Column(name = "puntaje")
    var puntaje: BigDecimal,

    @Column(name = "sustento", columnDefinition = "TEXT")
    var sustento: String? = null,

    // Version 1: "Riesgo Medio", etc. Version 2: "Bajo" / "Medio" / "Alto"
    @Column(name = "categoria", length = 50)
    var categoria: String,

    @Column(name = "fecha_creacion")
    var fechaCreacion: LocalDateTime = LocalDateTime.now(),

    // ---------------- Version 2 ----------------

    @Column(name = "version_metodologia", columnDefinition = "int default 1")
    var version: Int? = 1,

    // BAJO / MEDIO / ALTO
    @Column(name = "nivel", length = 10)
    var nivel: String? = null,

    @Column(name = "nivel_por_promedio", length = 10)
    var nivelPorPromedio: String? = null,

    @Column(name = "fijado_por_regla")
    var fijadoPorRegla: Boolean? = null,

    @Column(name = "cliente_nombre", length = 300)
    var clienteNombre: String? = null,

    @Column(name = "cliente_tipo_documento", length = 20)
    var clienteTipoDocumento: String? = null,

    @Column(name = "cliente_documento", length = 50)
    var clienteDocumento: String? = null,

    @Column(name = "cliente_tipo_persona", length = 20)
    var clienteTipoPersona: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_departamento_operacion")
    var departamentoOperacion: ScoringDepartamento? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_producto")
    var producto: ScoringOpcion? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_canal")
    var canal: ScoringOpcion? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medio_pago")
    var medioPago: ScoringOpcion? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pais_fondos")
    var paisFondos: Pais? = null,

    @Column(name = "monto_operacion", precision = 18, scale = 2)
    var montoOperacion: BigDecimal? = null,

    @Column(name = "promedio_mensual", precision = 18, scale = 2)
    var promedioMensual: BigDecimal? = null,

    @Column(name = "id_sustento", length = 36)
    var sustentoId: String? = null,

    // El resultado tal como se mostro (JSON): permite volver a abrirlo y sacar el PDF aunque cambien las tablas
    @Column(name = "resultado", columnDefinition = "LONGTEXT")
    var resultado: String? = null,

    // ENVIAR_A_APROBACION / RECHAZAR
    @Column(name = "decision_accion", length = 30)
    var decisionAccion: String? = null,

    @Column(name = "decision_comentario", columnDefinition = "TEXT")
    var decisionComentario: String? = null,

    @Column(name = "decision_por", length = 100)
    var decisionPor: String? = null,

    @Column(name = "decision_fecha")
    var decisionFecha: LocalDateTime? = null
) {
    /** Las filas anteriores a la columna (o con null) son de la version 1. */
    fun versionMetodologia(): Int = version ?: 1
}
