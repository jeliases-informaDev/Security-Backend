package com.security.modules.denuncias.domain.enums

/**
 * Situacion denunciada. Se guarda como texto (VARCHAR 50): EsquemaDenunciasInicializador convierte la columna si una base
 * vieja la tiene como ENUM de MySQL. La etiqueta la manda el servidor (GET /api/denuncias/catalogo).
 */
enum class CausaDenuncia(val etiqueta: String) {
    SOBORNO("Soborno o corrupción"),
    CONFLICTO("Conflicto de intereses"),
    ACOSO("Hostigamiento sexual y acoso"),
    FRAUDE("Fraude"),
    OPERACION_SOSPECHOSA("Operación sospechosa"),
    OTRAS("Otras situaciones")
}
