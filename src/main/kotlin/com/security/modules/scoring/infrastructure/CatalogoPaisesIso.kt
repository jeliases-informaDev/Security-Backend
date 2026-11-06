package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.metodologia.CalculoScoring

/**
 * Nombre de pais (en cualquiera de sus estilos) → codigo ISO 3166-1 alfa-2. La tabla `pais` puede traer «Estados Unidos»,
 * «Estados Unidos de América» o «Venezuela (República Bolivariana de)»: todos se comparan normalizados (sin tildes, sin
 * espacios, en mayusculas) contra, en este orden de prioridad:
 *  1. scoring/paises-iso.csv (nombre_es → alfa2);
 *  2. scoring/paises-alias.csv (`alfa2;alias1|alias2|…`);
 *  3. los otros_nombres de scoring/paises.csv (toman el alfa2 de su nombre principal).
 * Si un nombre aparece en dos fuentes, gana la primera.
 */
object CatalogoPaisesIso {

    const val ISO = "scoring/paises-iso.csv"
    const val ALIAS = "scoring/paises-alias.csv"
    const val CLASIFICACION = "scoring/paises.csv"

    private class Datos(val porNombre: Map<String, String>, val nombreIso: Map<String, String>)

    private val datos: Datos by lazy { construir() }

    /** El alfa-2 del pais, o null si el nombre no se reconoce. */
    fun codigo(nombre: String?): String? {
        val n = nombre?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return datos.porNombre[CalculoScoring.normalizar(n)]
    }

    /** El nombre en español del catalogo ISO para un alfa-2. */
    fun nombre(alfa2: String): String? = datos.nombreIso[alfa2.uppercase()]

    /** Las filas del archivo de alias: alfa2 → alias (para las pruebas). */
    fun alias(): List<Pair<String, List<String>>> =
        ScoringCatalogoInicializador.leer(ALIAS).filter { it.size >= 2 }
            .map { it[0].uppercase() to it[1].split("|").map { a -> a.trim() }.filter { a -> a.isNotEmpty() } }

    /** Las filas del catalogo ISO: alfa2 → nombre_es (para las pruebas). */
    fun iso(): List<Pair<String, String>> =
        ScoringCatalogoInicializador.leer(ISO, ",").filter { it.size >= 4 }.map { it[0].uppercase() to it[3] }

    private fun construir(): Datos {
        val mapa = linkedMapOf<String, String>()
        fun poner(nombre: String, alfa2: String) {
            if (nombre.isNotBlank()) mapa.putIfAbsent(CalculoScoring.normalizar(nombre), alfa2)
        }
        val iso = iso()
        iso.forEach { (alfa2, nombre) -> poner(nombre, alfa2) }
        alias().forEach { (alfa2, nombres) -> nombres.forEach { poner(it, alfa2) } }
        ScoringCatalogoInicializador.leer(CLASIFICACION).forEach { fila ->
            val principal = fila.getOrNull(0)?.takeIf { it.isNotBlank() && it != ScoringCatalogoInicializador.COMODIN } ?: return@forEach
            val alfa2 = mapa[CalculoScoring.normalizar(principal)] ?: return@forEach
            fila.getOrNull(4)?.split("|")?.forEach { poner(it.trim(), alfa2) }
        }
        return Datos(mapa, iso.associate { it.first to it.second })
    }
}
