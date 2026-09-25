package com.security.modules.matrices.infrastructure.persistence.entity

import com.security.modules.matrices.domain.enums.FactorRiesgo
import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import com.security.modules.matrices.domain.enums.NivelSupervision
import com.security.modules.matrices.domain.enums.OperatividadControl
import com.security.modules.matrices.domain.enums.PeriodicidadControl
import com.security.modules.matrices.domain.enums.TipoControl
import com.security.modules.matrices.domain.enums.TipoEmpresa
import com.security.shared.datos.entities.Usuario
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "matriz_riesgo_analisis")
class MatrizRiesgoAnalisisEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    var usuario: Usuario? = null,

    // -------------------------
    // RIESGO INHERENTE
    // -------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_empresa", length = 100)
    var tipoEmpresa: TipoEmpresa? = null,

    @Column(name = "titulo", length = 250)
    var titulo: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    var area: MatrizRiesgoAreaEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id")
    var proceso: MatrizRiesgoProcesoEntity? = null,

    @Column(name = "detalle_riesgo", columnDefinition = "TEXT")
    var detalleRiesgo: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "factor", length = 100)
    var factor: FactorRiesgo? = null,

    @Column(name = "probabilidad_opcion", columnDefinition = "TEXT")
    var probabilidadOpcion: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "probabilidad_nivel", length = 50)
    var probabilidadNivel: NivelProbabilidad? = null,

    @Column(name = "impacto_estimado", precision = 15, scale = 2)
    var impactoEstimado: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "impacto_nivel", length = 50)
    var impactoNivel: NivelImpacto? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "riesgo_inherente_valor", length = 100)
    var riesgoInherente: NivelRiesgo? = null,

    @Column(name = "riesgo_inherente_color", length = 20)
    var riesgoInherenteColor: String? = null,

    // -------------------------
    // IDENTIFICACIÓN DE CONTROL
    // -------------------------

    @Column(name = "control_descripcion", columnDefinition = "TEXT")
    var controlDescripcion: String? = null,

    @Column(name = "control_documento", length = 250)
    var controlDocumento: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "control_area_id")
    var controlArea: MatrizRiesgoAreaEntity? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "control_periodicidad", length = 50)
    var controlPeriodicidad: PeriodicidadControl? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "control_operatividad", length = 50)
    var controlOperatividad: OperatividadControl? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "control_tipo", length = 50)
    var controlTipo: TipoControl? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "control_supervision", length = 100)
    var controlSupervision: NivelSupervision? = null,

    @Column(name = "control_frecuencia_oportuna")
    var controlFrecuenciaOportuna: Boolean? = null,

    @Column(name = "control_seguimiento_adecuado")
    var controlSeguimientoAdecuado: Boolean? = null,

    // -------------------------
    // RIESGO RESIDUAL
    // -------------------------

    @Column(name = "mitigacion", precision = 5, scale = 3)
    var mitigacion: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "probabilidad_residual_nivel", length = 50)
    var probabilidadResidual: NivelProbabilidad? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "impacto_residual_nivel", length = 50)
    var impactoResidual: NivelImpacto? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "riesgo_residual_nivel", length = 50)
    var riesgoResidual: NivelRiesgo? = null,

    @Column(name = "riesgo_residual_valor", precision = 5, scale = 2)
    var riesgoResidualValor: BigDecimal? = null,

    @Column(name = "riesgo_residual_color", length = 20)
    var riesgoResidualColor: String? = null,

    // -------------------------
    // TRATAMIENTO
    // -------------------------

    @Column(name = "plan_accion", columnDefinition = "TEXT")
    var planAccion: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_responsable_id")
    var areaResponsable: MatrizRiesgoAreaEntity? = null,

    @Column(name = "fecha_inicio")
    var fechaInicio: LocalDate? = null,

    @Column(name = "fecha_cierre")
    var fechaCierre: LocalDate? = null,

    @Column(name = "estado", length = 20)
    var estado: String = "EDITANDO",

    @Column(name = "fecha_creacion")
    var fechaCreacion: LocalDateTime = LocalDateTime.now(),

    @Column(name = "fecha_actualizacion", nullable = false)
    var fechaActualizacion: LocalDateTime = LocalDateTime.now()
)