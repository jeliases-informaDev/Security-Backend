package com.security.modules.operaciones.domain

import com.security.modules.scoring.domain.ScoringDepartamento
import jakarta.persistence.*

@Entity
@Table(name = "provincia", uniqueConstraints = [
    UniqueConstraint(name = "uk_provincia_departamento_nombre", columnNames = ["id_departamento", "nombre"])
])
class Provincia(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,
    @Column(nullable = false, length = 150)
    var nombre: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_departamento", nullable = false,
        foreignKey = ForeignKey(name = "fk_provincia_departamento"))
    var departamento: ScoringDepartamento
)
