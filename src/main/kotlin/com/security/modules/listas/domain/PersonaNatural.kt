package com.security.modules.listas.domain

import jakarta.persistence.*

@Entity
@Table(name = "personas_naturales")
class PersonaNatural(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidades", nullable = false, unique = true)
    var entidad: Entidad,

    @Column(name = "nombre")
    var nombre: String? = null,

    @Column(name = "segundo_nombre")
    var segundoNombre: String? = null,

    @Column(name = "ape_pat")
    var apePat: String? = null,

    @Column(name = "ape_mat")
    var apeMat: String? = null,

    @Column(name = "pasaporte")
    var pasaporte: String? = null,

    @Column(name = "sexo", length = 1)
    var sexo: String? = null
)
