package com.security.modules.scoring.domain

import jakarta.persistence.*

@Entity
@Table(name = "scoring_ocupacion")
class ScoringOcupacion(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "nombre", nullable = false, unique = true)
    var nombre: String,

    // Puntaje de riesgo 1-5 de la metodologia version 1 (libro de 2020): lo conservan las evaluaciones antiguas
    @Column(name = "puntaje", nullable = false)
    var puntaje: Int,

    // Puntaje 1-4 de la metodologia version 2; null = la ocupacion no tiene puntaje y elegirla bloquea el calculo
    @Column(name = "puntaje_v2")
    var puntajeV2: Int? = null
)
