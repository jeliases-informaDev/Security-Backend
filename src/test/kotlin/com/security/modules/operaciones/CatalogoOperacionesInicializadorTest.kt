package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.CatalogoOperacionesInicializador
import com.security.modules.operaciones.domain.Moneda
import com.security.modules.operaciones.domain.Provincia
import com.security.modules.operaciones.infraestructure.MonedaRepository
import com.security.modules.operaciones.infraestructure.ProvinciaRepository
import com.security.modules.scoring.domain.ScoringDepartamento
import com.security.modules.scoring.infrastructure.ScoringDepartamentoRepository
import com.security.shared.datos.entities.Pais
import io.mockk.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class CatalogoOperacionesInicializadorTest {
    private val monedas = mockk<MonedaRepository>()
    private val provincias = mockk<ProvinciaRepository>()
    private val departamentos = mockk<ScoringDepartamentoRepository>()
    private val inicializador = CatalogoOperacionesInicializador(monedas, provincias, departamentos)

    private val peru = mockk<Pais>(relaxed = true)
    private fun departamento(nombre: String) = ScoringDepartamento(pais = peru, nombre = nombre, puntaje = 1)

    @Test
    fun `el archivo trae las 196 provincias del Peru de los 25 departamentos sin repetir`() {
        val filas = inicializador.leerProvincias()

        assertEquals(196, filas.size)
        assertEquals(196, filas.toSet().size)
        assertEquals(25, filas.map { it.first }.toSet().size)
        assertEquals(10, filas.count { it.first == "Lima" })
        assertTrue("Junin" to "Huancayo" in filas)
    }

    @Test
    fun `agrega solo las monedas que faltan`() {
        every { monedas.findAll() } returns listOf(Moneda(codigo = "pen", nombre = "Nuevo sol"))
        val guardadas = slot<List<Moneda>>()
        every { monedas.saveAll(capture(guardadas)) } answers { firstArg() }

        assertEquals(2, inicializador.sembrarMonedas())

        assertEquals(listOf("USD", "EUR"), guardadas.captured.map { it.codigo })
    }

    @Test
    fun `no escribe nada si las monedas ya estan`() {
        every { monedas.findAll() } returns CatalogoOperacionesInicializador.MONEDAS.map { Moneda(codigo = it.codigo, nombre = it.nombre) }

        assertEquals(0, inicializador.sembrarMonedas())

        verify(exactly = 0) { monedas.saveAll(any<List<Moneda>>()) }
    }

    @Test
    fun `agrega las provincias que faltan y omite departamentos que no existen`() {
        val junin = departamento("Junin")
        every { departamentos.findAll() } returns listOf(junin)
        every { provincias.findAll() } returns listOf(Provincia(nombre = "Huancayo", departamento = junin))
        val guardadas = slot<List<Provincia>>()
        every { provincias.saveAll(capture(guardadas)) } answers { firstArg() }

        val filas = listOf("Junin" to "Huancayo", "Junin" to "Tarma", "Junin" to "tarma", "Narnia" to "Cair Paravel")
        assertEquals(1, inicializador.sembrarProvincias(filas))

        assertEquals(listOf("Tarma"), guardadas.captured.map { it.nombre })
        assertSame(junin, guardadas.captured.single().departamento)
    }

    @Test
    fun `una segunda corrida con todo cargado no escribe nada`() {
        val departamentosBase = inicializador.leerProvincias().map { it.first }.distinct().map { departamento(it) }
        every { departamentos.findAll() } returns departamentosBase
        val porNombre = departamentosBase.associateBy { it.nombre }
        every { provincias.findAll() } returns inicializador.leerProvincias().map { (d, p) -> Provincia(nombre = p, departamento = porNombre.getValue(d)) }

        assertEquals(0, inicializador.sembrarProvincias(inicializador.leerProvincias()))

        verify(exactly = 0) { provincias.saveAll(any<List<Provincia>>()) }
    }
}
