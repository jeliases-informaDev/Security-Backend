package com.security.modules.scoring

import com.security.modules.scoring.domain.metodologia.CalculoScoring
import com.security.modules.scoring.infrastructure.CatalogoPaisesIso
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogoPaisesIsoTest {

    @Test
    fun `cada nombre del catalogo ISO resuelve a su propio codigo`() {
        val iso = CatalogoPaisesIso.iso()
        assertTrue(iso.size > 200)
        val fallan = iso.filter { (alfa2, nombre) -> CatalogoPaisesIso.codigo(nombre) != alfa2 }
        assertTrue(fallan.isEmpty(), "no resuelven a su codigo: $fallan")
    }

    @Test
    fun `ningun alias apunta a dos codigos`() {
        val porAlias = mutableMapOf<String, MutableSet<String>>()
        CatalogoPaisesIso.alias().forEach { (alfa2, nombres) ->
            nombres.forEach { porAlias.getOrPut(CalculoScoring.normalizar(it)) { mutableSetOf() }.add(alfa2) }
        }
        // Tampoco contra un nombre del catalogo ISO de otro pais.
        CatalogoPaisesIso.iso().forEach { (alfa2, nombre) -> porAlias[CalculoScoring.normalizar(nombre)]?.add(alfa2) }
        val dobles = porAlias.filterValues { it.size > 1 }
        assertTrue(dobles.isEmpty(), "alias con dos codigos: $dobles")
    }

    @Test
    fun `nombres en otros estilos resuelven al codigo correcto`() {
        assertEquals("US", CatalogoPaisesIso.codigo("Estados Unidos de América"))
        assertEquals("US", CatalogoPaisesIso.codigo("EE. UU."))
        assertEquals("VE", CatalogoPaisesIso.codigo("Venezuela (República Bolivariana de)"))
        assertEquals("RU", CatalogoPaisesIso.codigo("Rusia (Federación de)"))
        assertEquals("PE", CatalogoPaisesIso.codigo("  Perú "))
        assertEquals("IR", CatalogoPaisesIso.codigo("Iran (Republica Islamica del)"))
        assertNull(CatalogoPaisesIso.codigo("Extranjero"))
        assertNull(CatalogoPaisesIso.codigo(null))
    }
}
