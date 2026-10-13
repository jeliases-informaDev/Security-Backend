package com.security.modules.operaciones.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.math.BigDecimal

@Entity
@Table(name = "operaciones_v2")
class Operacion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "fecha_operacion")
    val fechaOperacion: LocalDate,

    val lugar: String?,

    @Column(name = "modalidad_pago")
    val modalidadPago: String,

    val moneda: String,
    @Column(precision = 15, scale = 2)
    val monto: BigDecimal,

    @Column(name = "tipo_operacion")
    val tipoOperacion: String,

    @Column(name = "nro_cuenta_1")
    val cuenta1: String?,

    @Column(name = "nro_cuenta_2")
    val cuenta2: String?,

    @Column(name = "nro_cuenta_3")
    val cuenta3: String?
)
