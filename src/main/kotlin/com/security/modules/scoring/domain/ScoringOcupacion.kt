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

    // Puntaje de riesgo 1-5, segun el modelo de scoring de referencia
    @Column(name = "puntaje", nullable = false)
    var puntaje: Int
)
