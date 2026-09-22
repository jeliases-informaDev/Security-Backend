package com.security.shared.datos.entities

import jakarta.persistence.*

@Entity
@Table(name = "tipo_documento")
class TipoDocumento(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "nombre")
    var nombre: String? = null,

    @Column(name = "descripcion", columnDefinition = "TEXT")
    var descripcion: String? = null
)
