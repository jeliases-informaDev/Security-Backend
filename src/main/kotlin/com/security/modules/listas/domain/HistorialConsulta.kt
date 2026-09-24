package com.security.modules.listas.domain

import com.security.shared.datos.entities.Usuario
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "historial_consultas")
class HistorialConsulta(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuarios")
    var usuario: Usuario,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad")
    var entidad: Entidad,

    @Column(name = "fecha_consulta")
    var fechaConsulta: LocalDateTime = LocalDateTime.now(),

    @Column(name = "tipo", length = 50)
    var tipo: String = "BUSQUEDA"
)
