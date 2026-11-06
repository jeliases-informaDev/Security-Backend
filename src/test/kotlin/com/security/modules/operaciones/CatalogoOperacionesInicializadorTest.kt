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
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.transaction.PlatformTransactionManager
import kotlin.test.*

class CatalogoOperacionesInicializadorTest {
    private val monedas = mockk<MonedaRepository>()
    private val provincias = mockk<ProvinciaRepository>()
    private val departamentos = mockk<ScoringDepartamentoRepository>()
    private val transacciones = mockk<PlatformTransactionManager>(relaxed = true)
    private val inicializador = CatalogoOperacionesInicializador(monedas, provincias, departamentos, transacciones)

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

    @Test
    fun `al arrancar con provincias ya guardadas siembra dentro de una transaccion`() {
        // En produccion las provincias guardadas traen su departamento perezoso: leer su nombre exige una sesion abierta.
        val junin = departamento("Junin")
        every { monedas.findAll() } returns CatalogoOperacionesInicializador.MONEDAS.map { Moneda(codigo = it.codigo, nombre = it.nombre) }
        every { departamentos.findAll() } returns listOf(junin)
        every { provincias.findAll() } returns listOf(Provincia(nombre = "Huancayo", departamento = junin))
        val guardadas = slot<List<Provincia>>()
        every { provincias.saveAll(capture(guardadas)) } answers { firstArg() }

        inicializador.run(DefaultApplicationArguments())

        verify(exactly = 2) { transacciones.getTransaction(any()) }
        verify(exactly = 2) { transacciones.commit(any()) }
        // Las de Junin que faltaban (el archivo trae 9) y ninguna repetida
        assertEquals(8, guardadas.captured.size)
        assertTrue(guardadas.captured.all { it.departamento === junin })
        assertTrue(guardadas.captured.none { it.nombre == "Huancayo" })
    }

    @Test
    fun `el catalogo del formulario trae las opciones exactas de las fichas y solo agrega lo que falta`() {
        val filas = inicializador.leerCatalogosRegistro()
        val porCatalogo = filas.groupBy { it.catalogo }
        assertEquals(5, porCatalogo.getValue("TIPO_DOCUMENTO").size)
        assertEquals(5, porCatalogo.getValue("ESTADO_CIVIL").size)
        assertEquals(9, porCatalogo.getValue("MODALIDAD_PAGO").size)
        assertEquals(7, porCatalogo.getValue("ORIGEN_FONDOS").size)
        assertEquals(12, porCatalogo.getValue("TIPO_OPERACION").size)
        assertTrue(porCatalogo.getValue("TIPO_OPERACION").all { it.sector == "INMOBILIARIO" })
        assertEquals("Tarjeta de crédito o débito", porCatalogo.getValue("MODALIDAD_PAGO").single { it.codigo == "TARJETA" }.nombre)
        assertEquals(listOf("SIN_DOCUMENTO"), porCatalogo.getValue("TIPO_DOCUMENTO").filter { it.pideTexto }.map { it.codigo })
        assertEquals(filas.size, filas.map { it.catalogo to it.codigo }.toSet().size)

        val repositorio = mockk<com.security.modules.operaciones.infraestructure.CatalogoOperacionRepository>()
        val conRepositorio = CatalogoOperacionesInicializador(monedas, provincias, departamentos, transacciones, repositorio)
        every { repositorio.findAll() } returns filas.take(3)
        val guardadas = slot<List<com.security.modules.operaciones.domain.CatalogoOperacionEntity>>()
        every { repositorio.saveAll(capture(guardadas)) } answers { firstArg() }
        assertEquals(filas.size - 3, conRepositorio.sembrarCatalogosRegistro(filas))
        assertEquals(filas.size - 3, guardadas.captured.size)
    }
}
