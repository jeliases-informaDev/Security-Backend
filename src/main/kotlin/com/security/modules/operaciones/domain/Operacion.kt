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
    var descripcion: String? = null,

    // ---- Entrega B: verificacion de umbral guardada con el registro (null en los registros anteriores) ----

    @Column(name = "umbral_estado", length = 30)
    var umbralEstado: String? = null,

    @Column(name = "umbral_usd", precision = 15, scale = 2)
    var umbralUsd: BigDecimal? = null,

    @Column(name = "umbral_norma_usd", precision = 15, scale = 2)
    var umbralNormaUsd: BigDecimal? = null,

    @Column(name = "umbral_interno")
    var umbralInterno: Boolean? = null,

    @Column(name = "tipo_bien", length = 40)
    var tipoBien: String? = null,

    @Column(name = "sector", length = 60)
    var sector: String? = null,

    // Monto equivalente en US$ y tipos de cambio usados (soles por unidad, promedio de venta del mes tc_mes)
    @Column(name = "monto_usd", precision = 17, scale = 2)
    var montoUsd: BigDecimal? = null,

    @Column(name = "tc_mes", length = 7)
    var tcMes: String? = null,

    @Column(name = "tc_usd", precision = 10, scale = 6)
    var tcUsd: BigDecimal? = null,

    @Column(name = "tc_moneda", precision = 10, scale = 6)
    var tcMoneda: BigDecimal? = null,

    @Column(name = "umbral_verificado_en")
    var umbralVerificadoEn: java.time.LocalDateTime? = null
)
