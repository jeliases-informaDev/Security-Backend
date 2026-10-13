package com.security.modules.denuncias.infrastructure.persistence.entity

import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.shared.datos.entities.Usuario
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "denuncia_historial")
open class DenunciaHistorialEntity(

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
    // CAMBIO DE ESTADO
    // -------------------------

    @Enumerated(EnumType.STRING)
    @Column(
        name = "estado_anterior",
        length = 30
    )
    var estadoAnterior: EstadoDenuncia? = null,

    @Enumerated(EnumType.STRING)
    @Column(
        name = "estado_nuevo",
        nullable = false,
        length = 30
    )
    var estadoNuevo: EstadoDenuncia,

    @Column(
        name = "comentario",
        columnDefinition = "TEXT"
    )
    var comentario: String? = null,

    // -------------------------
    // USUARIO QUE REALIZA EL CAMBIO
    // -------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    var usuario: Usuario? = null,

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