package com.security.modules.scoring.domain

import jakarta.persistence.*

/**
 * Opciones con puntaje de la metodologia version 2 que no tenian tabla propia: producto o servicio, canal
 * y medio de pago. Se cargan desde src/main/resources/scoring (productos.csv, canales.csv, medios-pago.csv).
 */
@Entity
@Table(
    name = "scoring_opcion",
    uniqueConstraints = [UniqueConstraint(name = "uk_scoring_opcion_tipo_nombre", columnNames = ["tipo", "nombre"])]
)
class ScoringOpcion(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    // PRODUCTO / CANAL / MEDIO_PAGO
    @Column(name = "tipo", nullable = false, length = 20)
    var tipo: String,

    @Column(name = "nombre", nullable = false, length = 150)
    var nombre: String,

    // 1-4; null = sin puntaje (elegirla bloquea el calculo)
    @Column(name = "puntaje")
    var puntaje: Int? = null,

    // Orden en el desplegable (el del archivo)
    @Column(name = "orden")
    var orden: Int? = null,

    // Solo medios de pago: si pide sustento del origen de los fondos (Efectivo)
    @Column(name = "pide_origen_fondos")
    var pideOrigenFondos: Boolean? = false
) {
    companion object {
        const val PRODUCTO = "PRODUCTO"
        const val CANAL = "CANAL"
        const val MEDIO_PAGO = "MEDIO_PAGO"
    }
}
