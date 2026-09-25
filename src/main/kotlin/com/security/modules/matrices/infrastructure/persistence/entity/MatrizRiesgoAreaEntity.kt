package com.security.modules.matrices.infrastructure.persistence.entity

import com.security.shared.datos.entities.Usuario
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "matriz_riesgo_areas",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_mr_area_usuario_nombre",
            columnNames = ["id_usuario", "nombre"]
        )
    ]
)
class MatrizRiesgoAreaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    var usuario: Usuario? = null,

    @Column(name = "nombre", nullable = false, length = 250)
    var nombre: String,

    @Column(name = "activo", nullable = false)
    var activo: Boolean = true,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime = LocalDateTime.now(),

    @Column(name = "fecha_actualizacion", nullable = false)
    var fechaActualizacion: LocalDateTime = LocalDateTime.now()
)