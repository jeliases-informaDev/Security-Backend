package com.security.shared.datos.entities

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    var usuario: Usuario,

    @Column(name = "token", nullable = false, unique = true, length = 100)
    var token: String,

    @Column(name = "fecha_expiracion", nullable = false)
    var fechaExpiracion: LocalDateTime,

    @Column(name = "usado", nullable = false)
    var usado: Boolean = false,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime = LocalDateTime.now()
)
