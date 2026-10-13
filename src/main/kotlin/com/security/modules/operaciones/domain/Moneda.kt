package com.security.modules.operaciones.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "monedas") // Asegúrate de que coincida con el nombre exacto de tu tabla en MySQL
class Moneda(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "codigo", length = 10, nullable = false)
    var codigo: String? = null,

    @Column(name = "nombre", length = 100)
    var nombre: String? = null,

    @Column(name = "simbolo", length = 10)
    var simbolo: String? = null,

    @Column(name = "activo")
    var activo: Boolean = true,

    @Column(name = "fecha_creacion")
    var fechaCreacion: LocalDateTime = LocalDateTime.now()
)