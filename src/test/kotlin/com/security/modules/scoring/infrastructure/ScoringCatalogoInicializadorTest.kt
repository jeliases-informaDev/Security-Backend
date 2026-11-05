package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringDepartamento
import com.security.modules.scoring.domain.ScoringOcupacion
import com.security.modules.scoring.domain.ScoringOpcion
import com.security.modules.scoring.domain.ScoringPaisGrupo
import com.security.modules.scoring.domain.metodologia.AparicionLista
import com.security.modules.scoring.domain.metodologia.ClasificadorListas
import com.security.modules.scoring.domain.metodologia.MetodologiaV2
import com.security.shared.datos.entities.Pais
import com.security.shared.datos.repositories.PaisRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Los catalogos se cargan solos, completan lo vacio y no pisan lo cambiado a mano. */
class ScoringCatalogoInicializadorTest {

    private val ocupaciones = mockk<ScoringOcupacionRepository>(relaxed = true)
    private val departamentos = mockk<ScoringDepartamentoRepository>(relaxed = true)
    private val opciones = mockk<ScoringOpcionRepository>(relaxed = true)
    private val paisGrupos = mockk<ScoringPaisGrupoRepository>(relaxed = true)
    private val paises = mockk<PaisRepository>()
    private val inicializador = ScoringCatalogoInicializador(ocupaciones, departamentos, opciones, paisGrupos, paises, mockk(relaxed = true))

    init {
        every { ocupaciones.save(any<ScoringOcupacion>()) } answers { firstArg() }
        every { departamentos.save(any<ScoringDepartamento>()) } answers { firstArg() }
        every { opciones.save(any<ScoringOpcion>()) } answers { firstArg() }
        every { paisGrupos.save(any<ScoringPaisGrupo>()) } answers { firstArg() }
    }

    @Test
    fun `los archivos de la metodologia traen lo de la especificacion`() {
        val ocup = ScoringCatalogoInicializador.leer("scoring/ocupaciones.csv")
        assertEquals(143, ocup.size)
        assertEquals(35, ocup.count { it.getOrNull(2)?.isNotEmpty() == true })
        assertEquals("4", ocup.first { it[0] == "CONSTRUCTOR" }[2])
        assertEquals("", ocup.first { it[0] == "PINTOR" }[2])
        val deps = ScoringCatalogoInicializador.leer("scoring/departamentos.csv")
        assertEquals(25, deps.size)
        assertTrue(deps.all { it[2].isNotEmpty() })
        assertEquals(12, ScoringCatalogoInicializador.leer("scoring/productos.csv").size)
        assertEquals(5, ScoringCatalogoInicializador.leer("scoring/canales.csv").size)
        val medios = ScoringCatalogoInicializador.leer("scoring/medios-pago.csv")
        assertEquals(7, medios.size)
        assertEquals(listOf("Efectivo"), medios.filter { ScoringCatalogoInicializador.esSi(it[2]) }.map { it[0] })
        val paisesCsv = ScoringCatalogoInicializador.leer("scoring/paises.csv")
        assertEquals(3, paisesCsv.count { ScoringCatalogoInicializador.esSi(it[2]) })
    }

    @Test
    fun `ocupaciones - crea las que faltan y completa el puntaje vacio sin pisar el cambiado`() {
        val cambiada = ScoringOcupacion(id = 1, nombre = "CONSTRUCTOR", puntaje = 1, puntajeV2 = 3)
        val vacia = ScoringOcupacion(id = 2, nombre = "Abogado", puntaje = 2, puntajeV2 = null)
        every { ocupaciones.findAll() } returns listOf(cambiada, vacia)

        inicializador.sembrarOcupaciones(listOf(listOf("CONSTRUCTOR", "1", "4"), listOf("ABOGADO", "2", "3"), listOf("NUEVA", "1", "")))

        assertEquals(3, cambiada.puntajeV2)
        assertEquals(3, vacia.puntajeV2)
        verify(exactly = 1) { ocupaciones.save(match { it.nombre == "NUEVA" && it.puntajeV2 == null }) }
    }

    @Test
    fun `departamentos - empareja sin tildes y completa el puntaje`() {
        val peru = Pais(id = 1, nombre = "Peru")
        val apurimac = ScoringDepartamento(id = 3, pais = peru, nombre = "Apurimac", puntaje = 2)
        every { paises.findAll() } returns listOf(peru)
        every { departamentos.findAll() } returns listOf(apurimac)

        inicializador.sembrarDepartamentos(listOf(listOf("Apurímac", "2", "1")))

        assertEquals(1, apurimac.puntajeV2)
        verify(exactly = 0) { departamentos.save(any<ScoringDepartamento>()) }
    }

    @Test
    fun `opciones - crea producto, canal y medio de pago con su orden`() {
        every { opciones.findByTipo(ScoringOpcion.MEDIO_PAGO) } returns emptyList()
        inicializador.sembrarOpciones(ScoringOpcion.MEDIO_PAGO, listOf(listOf("Tarjeta", "1", "no"), listOf("Efectivo", "3", "si")))
        verify { opciones.save(match { it.nombre == "Efectivo" && it.pideOrigenFondos == true && it.orden == 2 && it.puntaje == 3 }) }
    }

    @Test
    fun `paises - grupo por nombre u otro nombre, el resto al grupo 3 y no crea paises`() {
        val peru = Pais(id = 1, nombre = "PERU")
        val corea = Pais(id = 2, nombre = "Corea del Norte (RPDC)")
        val francia = Pais(id = 3, nombre = "Francia")
        val chile = Pais(id = 4, nombre = "Chile")
        every { paises.findAll() } returns listOf(peru, corea, francia, chile)
        val chileGuardado = ScoringPaisGrupo(id = 9, pais = chile, grupo = 3, altoRiesgoGafi = false, vigencia = LocalDate.of(2026, 12, 1))
        every { paisGrupos.todosConPais() } returns listOf(chileGuardado)

        inicializador.sembrarPaises(
            listOf(
                listOf("Perú", "1", "no", "2026-10-30", ""),
                listOf("Corea del Norte", "4", "si", "2026-10-30", "Corea del Norte (RPDC)|RPDC"),
                listOf("Chile", "2", "no", "2026-10-30", ""),
                listOf("Atlantida", "2", "no", "2026-10-30", ""),
                listOf("*", "3", "no", "2026-10-30", "")
            )
        )

        verify { paisGrupos.save(match { it.pais.id == 1 && it.grupo == 1 && it.altoRiesgoGafi == false }) }
        verify { paisGrupos.save(match { it.pais.id == 2 && it.grupo == 4 && it.altoRiesgoGafi == true }) }
        verify { paisGrupos.save(match { it.pais.id == 3 && it.grupo == 3 }) }
        // Chile tenia una clasificacion guardada a mano con vigencia mas nueva: no se pisa
        assertEquals(3, chileGuardado.grupo)
        verify(exactly = 0) { paises.save(any<Pais>()) }
    }

    @Test
    fun `una clasificacion del archivo con vigencia mas nueva reemplaza a la guardada`() {
        val iran = Pais(id = 5, nombre = "Iran")
        every { paises.findAll() } returns listOf(iran)
        val guardado = ScoringPaisGrupo(id = 1, pais = iran, grupo = 3, altoRiesgoGafi = false, vigencia = LocalDate.of(2026, 6, 1))
        every { paisGrupos.todosConPais() } returns listOf(guardado)

        inicializador.sembrarPaises(listOf(listOf("Irán", "4", "si", "2026-10-30", "")))

        assertEquals(4, guardado.grupo)
        assertEquals(true, guardado.altoRiesgoGafi)
        assertEquals(LocalDate.of(2026, 10, 30), guardado.vigencia)
    }

    @Test
    fun `listas - PEP, sanciones o LA-FT por grupo y noticias por nombre`() {
        val m = MetodologiaV2.ACTUAL
        val r = ClasificadorListas.clasificar(
            m,
            listOf(
                AparicionLista(esPep = true, nombreLista = "Perú - PEP", ordenGrupo = 6),
                AparicionLista(esPep = false, nombreLista = "Lista OFAC", ordenGrupo = 1),
                AparicionLista(esPep = false, nombreLista = "Sanciones Administrativas SBS", ordenGrupo = 4),
                AparicionLista(esPep = false, nombreLista = "ASBANC - Noticias (información negativa)", ordenGrupo = 6),
                AparicionLista(esPep = false, nombreLista = "Afectación financiera", ordenGrupo = 5),
                AparicionLista(esPep = false, nombreLista = "Sin grupo", ordenGrupo = null)
            )
        )
        assertEquals(1, r.pep)
        assertEquals(2, r.sanciones)
        assertEquals(1, r.noticias)
        assertEquals(4, ClasificadorListas.condicion(m, r).puntaje)
    }
}
