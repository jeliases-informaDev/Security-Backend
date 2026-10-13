package com.security.modules.operaciones.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "registro_operaciones_v2")
class RegistroOperacionesEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "numero_registro", nullable = false, unique = true, length = 50)
    val numeroRegistro: String?,

    @Column(name = "id_usuario")
    val idUsuario: Int,

    val empresa: String,
    val oficina: String,

    @Column(name = "fecha_registro")
    val fechaRegistro: LocalDate,

    @Column(name = "fecha_creacion")
    val fechaCreacion: LocalDateTime = LocalDateTime.now(),

    // Copia de los datos personales al registrar la operacion.
    @OneToOne(cascade = [CascadeType.PERSIST], fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona_fisica", nullable = false, unique = true)
    val personaFisica: Persona,

    @OneToOne(cascade = [CascadeType.PERSIST], fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona_representada", unique = true)
    val personaRepresentada: Persona?,

    // Operación asociada
    @OneToOne(cascade = [CascadeType.ALL])
    @JoinColumn(name = "id_operacion", nullable = false, unique = true)
    val operacion: Operacion,

    // Lista de beneficiarios
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "registro_id")
    val beneficiarios: List<BeneficiarioEntity> = emptyList()
)
