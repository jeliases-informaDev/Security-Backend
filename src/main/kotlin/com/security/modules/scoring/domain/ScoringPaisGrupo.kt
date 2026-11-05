package com.security.modules.scoring.domain

import com.security.shared.datos.entities.Pais
import jakarta.persistence.*
import java.time.LocalDate

/**
 * Clasificacion de cada pais de la tabla `pais` para el factor "Pais de los fondos" (metodologia version 2):
 * grupo 1 a 4 (que es su puntaje) y si esta en la lista de alto riesgo del GAFI. La lista del GAFI cambia tres
 * veces al ano: se actualiza en src/main/resources/scoring/paises.csv o directamente en esta tabla.
 */
@Entity
@Table(
    name = "scoring_pais_grupo",
    uniqueConstraints = [UniqueConstraint(name = "uk_scoring_pais_grupo_pais", columnNames = ["id_pais"])]
)
class ScoringPaisGrupo(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pais", nullable = false)
    var pais: Pais,

    // 1 Peru, 2 region con controles equivalentes, 3 otros paises, 4 bajo monitoreo del GAFI; null = sin grupo (bloquea)
    @Column(name = "grupo")
    var grupo: Int? = null,

    @Column(name = "alto_riesgo_gafi")
    var altoRiesgoGafi: Boolean? = false,

    // Fecha de la clasificacion del GAFI con la que se cargo el grupo
    @Column(name = "vigencia")
    var vigencia: LocalDate? = null
)
