package com.security.shared.datos.entities

import jakarta.persistence.*
import java.io.Serializable

data class UsuarioRolId(
    var usuario: Int = 0,
    var rol: Int = 0
) : Serializable

@Entity
@Table(name = "usuario_roles")
@IdClass(UsuarioRolId::class)
class UsuarioRol(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    var usuario: Usuario,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol")
    var rol: Roles
)