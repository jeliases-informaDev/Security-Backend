package com.security.shared.datos.entities

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "roles")
class Roles(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "codigo", nullable = false, unique = true)
    var codigo: String,

    @Column(name = "nombre", nullable = false)
    var nombre: String,

    @Column(name = "descripcion")
    var descripcion: String? = null,

    @Column(name = "activo", nullable = false)
    var activo: Boolean = true,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime? = null,

    @Column(name = "fecha_actualizacion", nullable = false)
    var fechaActualizacion: LocalDateTime? = null
)