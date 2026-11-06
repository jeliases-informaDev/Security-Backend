package com.security.modules.oficial.dominio

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Plazo ya resuelto por el servidor: el front solo lo pinta. */
data class Plazo(
    val regla: String,
    val unidad: String,
    val total: Int,
    val desde: LocalDate,
    val vence: LocalDate,
    val diasRestantes: Int,
    val avance: Int,
    val estado: String,
    val estadoEtiqueta: String,
    val cuentaFeriados: Boolean
)

enum class EstadoPlazo(val etiqueta: String) {
    EN_PLAZO("En plazo"),
    POR_VENCER("Por vencer"),
    VENCIDO("Vencido")
}

/**
 * Cuenta plazos. Dias habiles = lunes a viernes menos los feriados de la tabla, contados desde el dia siguiente a «desde»
 * (asi salen las fechas del diseño: 03/11/2026 + 15 = 24/11/2026). Los dias corridos y los meses se suman tal cual.
 * Es puro (sin base ni reloj) para poder probarlo.
 */
class CalculadoraPlazos(private val feriados: Set<LocalDate>, private val umbralPorVencer: Int) {

    val cuentaFeriados: Boolean get() = feriados.isNotEmpty()

    fun esHabil(dia: LocalDate): Boolean =
        dia.dayOfWeek != DayOfWeek.SATURDAY && dia.dayOfWeek != DayOfWeek.SUNDAY && dia !in feriados

    fun sumarHabiles(desde: LocalDate, cantidad: Int): LocalDate {
        var dia = desde
        var contados = 0
        while (contados < cantidad) {
            dia = dia.plusDays(1)
            if (esHabil(dia)) contados++
        }
        return dia
    }

    /** Dias habiles d con hoy < d <= hasta. */
    fun habilesEntre(hoy: LocalDate, hasta: LocalDate): Int {
        if (!hasta.isAfter(hoy)) return 0
        var dia = hoy
        var contados = 0
        while (dia.isBefore(hasta)) {
            dia = dia.plusDays(1)
            if (esHabil(dia)) contados++
        }
        return contados
    }

    fun vencimiento(unidad: UnidadPlazo, desde: LocalDate, valor: Int): LocalDate = when (unidad) {
        UnidadPlazo.DIAS_HABILES -> sumarHabiles(desde, valor)
        UnidadPlazo.DIAS -> desde.plusDays(valor.toLong())
        UnidadPlazo.MESES -> desde.plusMonths(valor.toLong())
    }

    fun plazo(regla: String, unidad: UnidadPlazo, valor: Int, desde: LocalDate, hoy: LocalDate): Plazo {
        val vence = vencimiento(unidad, desde, valor)
        val total = when (unidad) {
            UnidadPlazo.MESES -> ChronoUnit.DAYS.between(desde, vence).toInt()
            else -> valor
        }
        val restantes = when (unidad) {
            UnidadPlazo.DIAS_HABILES -> habilesEntre(hoy, vence)
            else -> if (vence.isAfter(hoy)) ChronoUnit.DAYS.between(hoy, vence).toInt() else 0
        }.coerceIn(0, total.coerceAtLeast(0))
        val estado = when {
            hoy.isAfter(vence) -> EstadoPlazo.VENCIDO
            habilesEntre(hoy, vence) <= umbralPorVencer -> EstadoPlazo.POR_VENCER
            else -> EstadoPlazo.EN_PLAZO
        }
        val avance = if (total <= 0) 100 else (((total - restantes) * 100) / total).coerceIn(0, 100)
        return Plazo(
            regla = regla,
            unidad = if (unidad == UnidadPlazo.MESES) UnidadPlazo.DIAS.name else unidad.name,
            total = total,
            desde = desde,
            vence = vence,
            diasRestantes = restantes,
            avance = avance,
            estado = estado.name,
            estadoEtiqueta = estado.etiqueta,
            cuentaFeriados = cuentaFeriados
        )
    }
}
