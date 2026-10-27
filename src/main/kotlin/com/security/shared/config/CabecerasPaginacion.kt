package com.security.shared.config

/**
 * Cabeceras con las que un listado paginado informa cuantos resultados hay en total, sin cambiar el cuerpo de la
 * respuesta (que sigue siendo la lista de la pagina). CORS las expone para que la web pueda leerlas.
 */
object CabecerasPaginacion {
    const val TOTAL = "X-Total-Count"
    const val TOTAL_PAGINAS = "X-Total-Pages"
    const val PAGINA = "X-Page"
    const val TAMANO = "X-Page-Size"

    val TODAS = listOf(TOTAL, TOTAL_PAGINAS, PAGINA, TAMANO)
}
