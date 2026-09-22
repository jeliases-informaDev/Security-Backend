package com.security.shared.datos.entities

import jakarta.persistence.*

@Entity
@Table(name = "pais")
class Pais(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "nombre")
    var nombre: String? = null,

    @Column(name = "continente")
    var continente: String? = null
)
