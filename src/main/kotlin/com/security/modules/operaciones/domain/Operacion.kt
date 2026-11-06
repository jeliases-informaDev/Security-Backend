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
    var fechaOperacion: LocalDate,

    var lugar: String?,

    @Column(name = "modalidad_pago")
    var modalidadPago: String,

    var moneda: String,
    @Column(precision = 15, scale = 2)
    var monto: BigDecimal,

    @Column(name = "tipo_operacion")
    var tipoOperacion: String,

    @Column(name = "nro_cuenta_1")
    var cuenta1: String?,

    @Column(name = "nro_cuenta_2")
    var cuenta2: String?,

    @Column(name = "nro_cuenta_3")
    var cuenta3: String?,

    // ---- Entrega A del diseño nuevo: opcionales en la base (los registros anteriores no los tienen) ----

    @Column(name = "modalidad_pago_texto", length = 255)
    var modalidadPagoTexto: String? = null,

    @Column(name = "origen_fondos", length = 60)
    var origenFondos: String? = null,

    @Column(name = "origen_fondos_texto", length = 255)
    var origenFondosTexto: String? = null,

    @Column(name = "numero_comprobante", length = 100)
    var numeroComprobante: String? = null,

    @Column(name = "descripcion", length = 500)
    var descripcion: String? = null
)
