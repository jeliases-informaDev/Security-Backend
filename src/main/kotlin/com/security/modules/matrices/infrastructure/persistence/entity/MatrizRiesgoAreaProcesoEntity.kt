package com.security.modules.matrices.infrastructure.persistence.entity

import com.security.shared.datos.entities.Usuario
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "matriz_riesgo_area_proceso",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_mr_area_proceso",
            columnNames = ["id_area", "id_proceso"]
        )
    ]
)
class MatrizRiesgoAreaProcesoEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_area", nullable = false)
    var area: MatrizRiesgoAreaEntity,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proceso", nullable = false)
    var proceso: MatrizRiesgoProcesoEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    var usuario: Usuario? = null,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime = LocalDateTime.now()
)
