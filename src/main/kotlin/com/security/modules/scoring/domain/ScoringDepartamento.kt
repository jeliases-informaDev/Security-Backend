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

    // Puntaje de riesgo 1-5
    @Column(name = "puntaje", nullable = false)
    var puntaje: Int
)
