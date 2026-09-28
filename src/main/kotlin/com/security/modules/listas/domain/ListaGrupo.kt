package com.security.modules.listas.domain

import jakarta.persistence.*

@Entity
@Table(name = "lista_grupo")
class ListaGrupo(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "orden")
    var orden: Int? = null,

    @Column(name = "nombre")
    var nombre: String? = null,

    @Column(name = "color", length = 30)
    var color: String? = null
)
