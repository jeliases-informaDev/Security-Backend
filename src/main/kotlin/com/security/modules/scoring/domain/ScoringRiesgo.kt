package com.security.modules.scoring.domain

import com.security.modules.listas.domain.Entidad
import com.security.shared.datos.entities.Usuario
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "scoring_riesgo")
class ScoringRiesgo(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    var usuario: Usuario,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad")
    var entidad: Entidad,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ocupacion")
    var ocupacion: ScoringOcupacion,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_departamento")
    var departamento: ScoringDepartamento,

    // NACIONAL / EXTRANJERO / PEP -- se deriva automaticamente, ver ScoringService
    @Column(name = "cliente_sensible_tipo", length = 20)
    var clienteSensibleTipo: String,

    @Column(name = "volumen_transaccional")
    var volumenTransaccional: BigDecimal,

    @Column(name = "puntaje")
    var puntaje: BigDecimal,

    @Column(name = "sustento", columnDefinition = "TEXT")
    var sustento: String? = null,

    @Column(name = "categoria", length = 50)
    var categoria: String,

    @Column(name = "fecha_creacion")
    var fechaCreacion: LocalDateTime = LocalDateTime.now()
)
