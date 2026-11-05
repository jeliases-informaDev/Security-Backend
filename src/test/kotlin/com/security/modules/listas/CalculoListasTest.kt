package com.security.modules.listas

import com.security.modules.listas.application.service.SemillaReglas
import com.security.modules.listas.domain.reglas.AparicionConsulta
import com.security.modules.listas.domain.reglas.CalculoListas
import com.security.modules.listas.domain.reglas.Clasificacion
import com.security.modules.listas.domain.reglas.EstadoFila
import com.security.modules.listas.domain.reglas.ListaDisponible
import com.security.modules.listas.domain.reglas.RegistroLista
import com.security.modules.listas.domain.reglas.ReglasListas
import com.security.modules.listas.domain.reglas.ResultadoListas
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El calculo puro con las reglas del archivo de valores iniciales (las mismas que se siembran en la base). */
class CalculoListasTest {

    private val reglas: ReglasListas = SemillaReglas.leer().reglas()

    // Las listas como las crea el cargador masivo (nombre exacto en tipo_lista) y su grupo de colorimetria.
    private val pep = ListaDisponible(1, "Perú - Personas Políticamente Expuestas (Resolución SBS N° 4349-2016)", true, LocalDate.of(2026, 10, 27))
    private val onu = ListaDisponible(2, "Internacional - Consejo de Seguridad ONU", false, null)
    private val ofac = ListaDisponible(3, "Lista OFAC", false, LocalDate.of(2026, 10, 28))
    private val ue = ListaDisponible(4, "Internacional - Sanciones de la Unión Europea", false, null)
    private val investigados = ListaDisponible(5, "ASBANC - Investigados", false, null)
    private val asbancNoticias = ListaDisponible(6, "ASBANC - Noticias (información negativa)", false, null)
    private val noticias = ListaDisponible(7, "Noticias", false, null)
    private val worldCheck = ListaDisponible(8, "ASBANC - World-Check", false, null)
    private val minjus = ListaDisponible(9, "Perú - Deudores de reparaciones civiles (MINJUS)", false, null)
    private val sancionesOtras = ListaDisponible(10, "ASBANC - Sanciones internacionales (otras fuentes)", false, null)
    private val grupo5 = ListaDisponible(11, "Lista de prueba de afectación financiera", false, null)
    private val todas = listOf(pep, onu, ofac, ue, investigados, asbancNoticias, noticias, worldCheck, minjus, sancionesOtras, grupo5)

    private var siguiente = 0

    private fun ap(lista: ListaDisponible, ordenGrupo: Int, veces: Int = 1): List<AparicionConsulta> = (1..veces).map {
        siguiente++
        AparicionConsulta(
            lista.id, lista.nombre, lista.esPep, ordenGrupo, "Grupo $ordenGrupo",
            RegistroLista(id = siguiente, lista = lista.nombre, esPep = lista.esPep, periodoDesde = if (lista.esPep) "NO INDICA" else null,
                sinPeriodo = lista.esPep)
        )
    }

    private fun ResultadoListas.fila(codigo: String) = filas.first { it.codigo == codigo }

    @Test
    fun `ejemplo del diseño - 10 + 10 + 5 + 5 = 30 es coincidencia media`() {
        val r = CalculoListas.calcular(
            reglas, todas,
            ap(pep, 6, 3) + ap(investigados, 2, 2) + ap(asbancNoticias, 6, 1) + ap(noticias, 6, 3)
        )
        assertEquals(30, r.porcentaje)
        assertEquals(Clasificacion.MEDIA, r.clasificacion)
        assertEquals("Coincidencia media", r.clasificacionNombre)
        assertEquals(4, r.listasConCoincidencia)
        // Se consultan de verdad 7 filas: SUNAT, ONU 1718/1737 y GAFI estan "Pronto"
        assertEquals(7, r.listasConsultadas)
        assertEquals(6, r.registrosEnListas)
        assertEquals(3, r.noticiasEnMedios)
        assertTrue(r.esPep)
        assertEquals("3 REGISTROS", r.fila("PEP_PERU").etiquetaConteo)
        assertEquals("2 REGISTROS", r.fila("ASBANC_INVESTIGADOS").etiquetaConteo)
        assertEquals("1 NOTA", r.fila("ASBANC_NOTICIAS").etiquetaConteo)
        assertEquals("3 NOTICIAS", r.fila("NOTICIAS_MEDIOS").etiquetaConteo)
        assertEquals(EstadoFila.SIN_COINCIDENCIAS, r.fila("OFAC").estado)
        assertEquals(listOf(CalculoListas.AVISO_PEP_SIN_PERIODO), r.fila("PEP_PERU").avisos)
        assertEquals(LocalDate.of(2026, 10, 27), r.fila("PEP_PERU").actualizada)
        assertNull(r.fila("ONU_1267").actualizada)
        assertEquals(LocalDate.of(2026, 10, 28), r.fechaBase)

        assertEquals("Coincidencia detectada", r.aviso.titulo)
        assertEquals(listOf("PEP", "Lista de investigados", "Noticias negativas"), r.aviso.fuentes)
        assertEquals("Suma 30% (PEP 10 + investigados 10 + noticias 5 + 5): clasificación Media.", r.aviso.suma)
        assertTrue(r.aviso.texto.contains("marcará al cliente como PEP"))
        assertEquals("Críticas 15% · Altas 10% · Informativas 5%", r.leyenda)
        assertEquals(
            mapOf("PEP" to "COINCIDENCIA", "SUNAT" to "PRONTO", "ONU" to "SIN_COINCIDENCIAS", "OFAC" to "SIN_COINCIDENCIAS",
                "UE" to "SIN_COINCIDENCIAS", "ASBANC" to "COINCIDENCIA", "GAFI" to "PRONTO", "NOTICIAS" to "COINCIDENCIA"),
            r.fuentes.associate { it.codigo to it.estado }
        )
    }

    @Test
    fun `ejemplo del diseño - 15 + 15 + 15 + 15 + 10 + 5 = 75 es critica (solo como prueba del calculo)`() {
        // En produccion ONU 1718/1737 esta "Pronto": aqui se le da una lista de prueba para reproducir el ejemplo.
        val lista1718 = ListaDisponible(20, "Lista de prueba ONU 1718", false, null)
        val conOnu1718 = reglas.copy(filas = reglas.filas.map {
            if (it.codigo == "ONU_1718_1737") it.copy(consultable = true, listas = listOf(lista1718.nombre)) else it
        })
        val r = CalculoListas.calcular(
            conOnu1718, todas + lista1718,
            ap(onu, 1) + ap(lista1718, 1) + ap(ofac, 1) + ap(ue, 2) + ap(investigados, 2) + ap(noticias, 6, 2)
        )
        assertEquals(75, r.porcentaje)
        assertEquals(Clasificacion.CRITICA, r.clasificacion)
        assertEquals(6, r.listasConCoincidencia)
        assertEquals(8, r.listasConsultadas)
        assertEquals(5, r.registrosEnListas)
        assertEquals(2, r.noticiasEnMedios)
        assertFalse(r.esPep)
        assertEquals(EstadoFila.SIN_COINCIDENCIAS, r.fila("ASBANC_NOTICIAS").estado)
        assertEquals(EstadoFila.PRONTO, r.fila("GAFI").estado)
        assertEquals("Coincidencia crítica", r.aviso.titulo)
        assertEquals(listOf("ONU", "OFAC", "Unión Europea", "Lista de investigados", "Noticias negativas"), r.aviso.fuentes)
        assertEquals(
            "Suma 75% (ONU 1267 15 + OFAC 15 + Unión Europea 15 + ONU 1718/1737 15 + investigados 10 + noticias 5): clasificación Crítica.",
            r.aviso.suma
        )
        assertTrue(r.aviso.texto.startsWith("Escala al oficial de cumplimiento"))
    }

    @Test
    fun `el porcentaje tiene tope 100`() {
        val muchas = (1..8).map { ListaDisponible(100 + it, "Lista restrictiva de prueba $it", false, null) }
        val r = CalculoListas.calcular(reglas, todas + muchas, muchas.flatMap { ap(it, 1) } + ap(pep, 6))
        // 8 x 15 + 10 = 130 -> 100
        assertEquals(100, r.porcentaje)
        assertEquals(Clasificacion.CRITICA, r.clasificacion)
        assertEquals(9, r.listasConCoincidencia)
    }

    @Test
    fun `otras listas - una fila por lista con el peso de su grupo de colorimetria`() {
        val r = CalculoListas.calcular(reglas, todas, ap(worldCheck, 3, 2) + ap(minjus, 2) + ap(grupo5, 5))
        val otras = r.filas.filter { it.otraLista }
        assertEquals(listOf(minjus.nombre, worldCheck.nombre, grupo5.nombre), otras.map { it.nombre })
        assertEquals(listOf(10, 10, 5), otras.map { it.peso })
        assertEquals(listOf("ALTA", "ALTA", "INFORMATIVA"), otras.map { it.grupoPeso })
        assertTrue(otras.all { it.fuente == CalculoListas.FUENTE_OTRAS && it.estado == EstadoFila.COINCIDENCIA })
        assertEquals("2 REGISTROS", otras[1].etiquetaConteo)
        assertEquals(25, r.porcentaje)
        assertEquals(Clasificacion.MEDIA, r.clasificacion)
        // Las de "Otras listas" cuentan en M solo porque aparecen
        assertEquals(10, r.listasConsultadas)
        assertEquals(listOf("Otras listas"), r.aviso.fuentes)

        // Grupo 1 (vinotinto) pesa como critica y hace la consulta critica
        val critica = CalculoListas.calcular(reglas, todas, ap(sancionesOtras, 1))
        assertEquals(15, critica.porcentaje)
        assertEquals(Clasificacion.CRITICA, critica.clasificacion)

        // Una lista sin grupo no se pierde: va a "Otras listas" con el grupo de menor peso (0 %)
        val sinGrupo = CalculoListas.calcular(reglas, todas, listOf(ap(grupo5, 5).first().copy(ordenGrupo = null)))
        assertEquals(1, sinGrupo.listasConCoincidencia)
        assertEquals(0, sinGrupo.porcentaje)
        assertEquals(Clasificacion.MEDIA, sinGrupo.clasificacion)
    }

    @Test
    fun `filas Pronto no cuentan y nunca dicen sin coincidencias`() {
        val r = CalculoListas.calcular(reglas, todas, emptyList())
        listOf("SUNAT", "ONU_1718_1737", "GAFI").forEach {
            assertEquals(EstadoFila.PRONTO, r.fila(it).estado)
            assertEquals("PRONTO", r.fila(it).etiquetaConteo)
            assertTrue(r.fila(it).motivo!!.isNotBlank())
        }
        assertEquals(7, r.listasConsultadas)

        // Una fila "consultable" cuya lista no existe en la base tambien queda "Pronto" (y se avisa en el arranque)
        val sinOfac = todas - ofac
        val r2 = CalculoListas.calcular(reglas, sinOfac, emptyList())
        assertEquals(EstadoFila.PRONTO, r2.fila("OFAC").estado)
        assertEquals(CalculoListas.MOTIVO_SIN_FUENTE, r2.fila("OFAC").motivo)
        assertEquals(6, r2.listasConsultadas)
        assertEquals(listOf("OFAC" to "Lista OFAC"), CalculoListas.listasFaltantes(reglas, sinOfac))
        assertFalse(CalculoListas.catalogo(reglas, sinOfac).first { it.codigo == "OFAC" }.disponible)
        assertTrue(CalculoListas.listasFaltantes(reglas, todas).isEmpty())

        // Una aparicion en una lista que solo alimenta una fila "Pronto" no suma en esa fila: pasa a "Otras listas"
        val listaGafi = ListaDisponible(30, "Lista GAFI de prueba", false, null)
        val conGafi = reglas.copy(filas = reglas.filas.map { if (it.codigo == "GAFI") it.copy(listas = listOf(listaGafi.nombre)) else it })
        val r3 = CalculoListas.calcular(conGafi, todas + listaGafi, ap(listaGafi, 4))
        assertEquals(EstadoFila.PRONTO, r3.fila("GAFI").estado)
        assertEquals(0, r3.fila("GAFI").registros)
        assertEquals(listOf(listaGafi.nombre), r3.filas.filter { it.otraLista }.map { it.nombre })
        assertEquals(5, r3.porcentaje)
    }

    @Test
    fun `cada clasificacion`() {
        val sin = CalculoListas.calcular(reglas, todas, emptyList())
        assertEquals(0, sin.porcentaje)
        assertEquals(Clasificacion.SIN_COINCIDENCIAS, sin.clasificacion)
        assertEquals("Sin coincidencias en las 7 listas consultadas", sin.aviso.titulo)
        assertNull(sin.aviso.suma)
        assertEquals(EstadoFila.SIN_COINCIDENCIAS, sin.fila("PEP_PERU").estado)

        val soloOfac = CalculoListas.calcular(reglas, todas, ap(ofac, 1))
        assertEquals(15, soloOfac.porcentaje)
        assertEquals(Clasificacion.CRITICA, soloOfac.clasificacion)

        val soloNoticias = CalculoListas.calcular(reglas, todas, ap(noticias, 6))
        assertEquals(5, soloNoticias.porcentaje)
        assertEquals(Clasificacion.MEDIA, soloNoticias.clasificacion)
        assertEquals(0, soloNoticias.registrosEnListas)
        assertEquals(1, soloNoticias.noticiasEnMedios)
        assertTrue(soloNoticias.aviso.texto.contains("homónimo"))
    }

    @Test
    fun `los nombres de lista se comparan sin mayusculas ni tildes`() {
        val asiEnLaBase = ListaDisponible(6, "  asbanc - NOTICIAS (informacion   negativa) ", false, null)
        val r = CalculoListas.calcular(reglas, todas - asbancNoticias + asiEnLaBase, ap(asiEnLaBase, 6))
        assertEquals(EstadoFila.COINCIDENCIA, r.fila("ASBANC_NOTICIAS").estado)
        assertTrue(r.filas.none { it.otraLista })
        assertEquals("ASBANC - NOTICIAS (INFORMACION NEGATIVA)", CalculoListas.normalizar(asiEnLaBase.nombre))
    }

    @Test
    fun `el resultado guardado en JSON se vuelve a leer igual`() {
        val r = CalculoListas.calcular(reglas, todas, ap(pep, 6, 2) + ap(worldCheck, 3) + ap(noticias, 6))
        val json = jacksonObjectMapper()
        val leido = json.readValue<ResultadoListas>(json.writeValueAsString(r))
        assertEquals(r, leido)
    }

    @Test
    fun `dominio del enlace y periodo vacio`() {
        assertEquals("diario.example.pe", CalculoListas.dominio("https://www.diario.example.pe/nota?id=1"))
        assertEquals("diario.example.pe", CalculoListas.dominio("diario.example.pe/nota"))
        assertNull(CalculoListas.dominio("  "))
        assertTrue(CalculoListas.periodoVacio("No indica"))
        assertTrue(CalculoListas.periodoVacio(null))
        assertFalse(CalculoListas.periodoVacio("2019"))
    }
}
