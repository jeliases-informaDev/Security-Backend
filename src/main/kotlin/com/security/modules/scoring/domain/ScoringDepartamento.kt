package com.security.modules.scoring.domain

import com.security.shared.datos.entities.Pais
import jakarta.persistence.*

@Entity
@Table(name = "scoring_departamento")
class ScoringDepartamento(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    // Generico a proposito: hoy solo se carga Peru, pero el mismo catalogo
    // escala a otros paises sin cambiar el esquema (ver nota en la migracion de datos)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pais", nullable = false)
    var pais: Pais,

    @Column(name = "nombre", nullable = false)
    var nombre: String,

    // Puntaje de riesgo 1-5 de la metodologia version 1 (libro de 2020)
    @Column(name = "puntaje", nullable = false)
    var puntaje: Int,

    // Puntaje 1-4 de la metodologia version 2; null = sin puntaje (bloquea el calculo)
    @Column(name = "puntaje_v2")
    var puntajeV2: Int? = null
)
