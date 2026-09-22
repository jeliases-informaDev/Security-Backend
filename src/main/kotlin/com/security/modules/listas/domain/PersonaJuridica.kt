package com.security.modules.listas.domain

import jakarta.persistence.*

@Entity
@Table(name = "personas_juridicas")
class PersonaJuridica(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidades", nullable = false, unique = true)
    var entidad: Entidad,

    @Column(name = "razon_social")
    var razonSocial: String? = null
)
