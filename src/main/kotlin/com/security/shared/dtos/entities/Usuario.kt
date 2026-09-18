package com.security.shared.datos.entities

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "usuarios")
class Usuario(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "usuario", nullable = false, unique = true)
    var usuario: String,

    @Column(name = "clave", nullable = false)
    var clave: String,

    @Column(name = "correo", nullable = false, unique = true)
    var correo: String,

    @Column(name = "nombres")
    var nombres: String? = null,

    @Column(name = "ape_pat")
    var apePat: String? = null,

    @Column(name = "ape_mat")
    var apeMat: String? = null,

    @Column(name = "cargo")
    var cargo: String? = null,

    @Column(name = "empresa")
    var empresa: String? = null,

    @Column(name = "documento")
    var documento: String? = null,

    @Column(name = "departamento")
    var departamento: String? = null,

    @Column(name = "provincia")
    var provincia: String? = null,

    @Column(name = "distrito")
    var distrito: String? = null,

    @Column(name = "direccion")
    var direccion: String? = null,

    @Column(name = "telefono")
    var telefono: String? = null,

    @Column(name = "foto", columnDefinition = "TEXT")
    var foto: String? = null,

    @Column(name = "activo", nullable = false)
    var activo: Boolean = true,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime? = null,

    @Column(name = "fecha_actualizacion", nullable = false)
    var fechaActualizacion: LocalDateTime? = null,

    @OneToMany(mappedBy = "usuario")
    var roles: MutableList<UsuarioRol> = mutableListOf()
)