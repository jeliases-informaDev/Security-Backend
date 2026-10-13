package com.security.modules.operaciones.domain

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "beneficiarios_operacion_v2")
class BeneficiarioEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "registro_id", insertable = false, updatable = false)
    var registroId: Long? = null,

    val nombre: String,

    @Column(name = "tipo_documento")
    val tipoDocumento: String,

    @Column(name = "numero_documento")
    val numeroDocumento: String,

    @Column(name = "fecha_nacimiento")
    val fechaNacimiento: LocalDate
)
