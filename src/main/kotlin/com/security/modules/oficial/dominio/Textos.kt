package com.security.modules.oficial.dominio

/**
 * Textos del diseño (Figma, «OFICIAL DE CUMPLIMIENTO PLAFT · Propuesta»), copiados tal cual. La declaracion guarda la
 * version del texto que se acepto (VERSION); si un texto cambia, sube la version.
 */
object TextosOc {
    const val VERSION = "2026-11-06"

    const val TITULO_DJ_REQUISITOS = "DECLARACIÓN JURADA DE CUMPLIMIENTO DE REQUISITOS DEL OFICIAL DE CUMPLIMIENTO"
    const val LUGAR = "Lima"

    data class Punto(val letra: String, val texto: String)

    val PUNTOS_REQUISITOS = listOf(
        Punto("a", "Tengo experiencia en las actividades de la empresa, en la lucha contra el LA/FT o como oficial de cumplimiento."),
        Punto("b", "No he sido condenado por delito doloso."),
        Punto("c", "No he sido destituido de un cargo público ni cesado por falta grave."),
        Punto("d", "No tengo deudas vencidas por más de 120 días en el sistema financiero ni protestos de los últimos 5 años sin aclarar."),
        Punto("e", "No he sido declarado en quiebra."),
        Punto("f", "No soy ni he sido auditor interno de la empresa en los 6 meses anteriores a mi designación."),
        Punto("g", "No estoy incurso en impedimentos del art. 365 de la Ley 26702 (salvo el inciso 2)."),
        Punto("h", "Tengo vínculo laboral con la empresa y actuaré con autonomía e independencia.")
    )

    /** Art. 6.1 h: el vinculo puede ser laboral o contractual directo (el diseño solo escribe «laboral»). */
    fun puntoH(vinculo: Vinculo?): String = when (vinculo) {
        Vinculo.CONTRACTUAL_DIRECTO -> "Tengo vínculo contractual directo con la empresa y actuaré con autonomía e independencia."
        else -> PUNTOS_REQUISITOS.last().texto
    }

    fun puntosRequisitos(vinculo: Vinculo?): List<Punto> =
        PUNTOS_REQUISITOS.map { if (it.letra == "h") Punto("h", puntoH(vinculo)) else it }

    val LETRAS = PUNTOS_REQUISITOS.map { it.letra }

    const val DJ_DESIGNANTE =
        "Declaro que la designación fue efectuada por la persona u órgano indicado y que adjunto copia del documento que la acredita."

    /** Renglones de «Requisitos del cargo» (OC 9). */
    fun requisitosCargo(vinculo: Vinculo?): List<String> = listOf(
        "Experiencia en la actividad o en lucha contra el LA/FT",
        "Sin condena por delito doloso ni destitución por falta grave",
        "Sin deudas vencidas ni protestos pendientes, ni quiebra",
        "No es ni fue auditor interno en los últimos 6 meses",
        if (vinculo == Vinculo.CONTRACTUAL_DIRECTO) "Vínculo contractual directo, con autonomía e independencia"
        else "Vínculo laboral directo, con autonomía e independencia"
    )

    data class Obligacion(val nombre: String, val frecuencia: String)

    /** «Obligaciones del cargo» (OC 9), tal cual el diseño. */
    val OBLIGACIONES = listOf(
        Obligacion("Informe anual del oficial (IAOC)", "CADA AÑO"),
        Obligacion("Verificar listas y congelamientos", "PERMANENTE"),
        Obligacion("Comunicar ROS a la UIF", "EN 24 HORAS"),
        Obligacion("Capacitar al personal", "CADA AÑO"),
        Obligacion("Custodia de documentos", "5 AÑOS")
    )

    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    fun fechaLarga(fecha: java.time.LocalDate): String =
        "%02d de %s de %d".format(fecha.dayOfMonth, MESES[fecha.monthValue - 1], fecha.year)

    fun fechaCorta(fecha: java.time.LocalDate?): String? =
        fecha?.let { "%02d/%02d/%04d".format(it.dayOfMonth, it.monthValue, it.year) }
}
