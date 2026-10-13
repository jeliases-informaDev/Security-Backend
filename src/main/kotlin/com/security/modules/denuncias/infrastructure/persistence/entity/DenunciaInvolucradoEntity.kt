package com.security.modules.denuncias.infrastructure.persistence.entity

import com.security.modules.denuncias.domain.enums.RolIncidente
import com.security.modules.denuncias.domain.enums.TipoPersonaInvolucrada
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "denuncia_involucrados")
open class DenunciaInvolucradoEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    // -------------------------
    // DENUNCIA
    // -------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "denuncia_id",
        nullable = false
    )
    var denuncia: DenunciaEntity,

    // -------------------------
    // PERSONA / EMPRESA
    // -------------------------

    @Enumerated(EnumType.STRING)
    @Column(
        name = "tipo_persona",
        nullable = false,
        length = 50
    )
    var tipoPersona: TipoPersonaInvolucrada,

    @Enumerated(EnumType.STRING)
    @Column(
        name = "rol_incidente",
        nullable = false,
        length = 50
    )
    var rolIncidente: RolIncidente,

    @Column(
        name = "documento",
        length = 50
    )
    var documento: String? = null,

    @Column(
        name = "nombre_razon_social",
        nullable = false,
        length = 255
    )
    var nombreRazonSocial: String,

    @Column(
        name = "cargo",
        nullable = false,
        length = 255
    )
    var cargo: String,

    // -------------------------
    // FECHA
    // -------------------------

    @Column(
        name = "fecha_creacion",
        nullable = false
    )
    var fechaCreacion: LocalDateTime =
        LocalDateTime.now()
)