package com.security.modules.oficial

import com.security.modules.oficial.dominio.CalculadoraPlazos
import com.security.modules.oficial.dominio.UnidadPlazo
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalculadoraPlazosTest {
    private val sinFeriados = CalculadoraPlazos(emptySet(), 3)
    private fun d(t: String) = LocalDate.parse(t)

    @Test
    fun `los dias habiles se cuentan desde el dia siguiente, como en las fechas del diseño`() {
        assertEquals(d("2026-11-24"), sinFeriados.vencimiento(UnidadPlazo.DIAS_HABILES, d("2026-11-03"), 15))
        assertEquals(d("2026-11-23"), sinFeriados.vencimiento(UnidadPlazo.DIAS_HABILES, d("2026-11-16"), 5))
        assertEquals(d("2026-11-27"), sinFeriados.vencimiento(UnidadPlazo.DIAS_HABILES, d("2026-11-20"), 5))
        assertEquals(d("2026-12-20"), sinFeriados.vencimiento(UnidadPlazo.DIAS, d("2026-11-20"), 30))
        assertEquals(d("2027-11-03"), sinFeriados.vencimiento(UnidadPlazo.MESES, d("2026-11-03"), 12))
    }

    @Test
    fun `un feriado de la tabla corre el vencimiento`() {
        val conFeriado = CalculadoraPlazos(setOf(d("2026-11-18")), 3)
        assertEquals(d("2026-11-24"), conFeriado.vencimiento(UnidadPlazo.DIAS_HABILES, d("2026-11-16"), 5))
        assertTrue(conFeriado.cuentaFeriados)
        assertFalse(sinFeriados.cuentaFeriados)
    }

    @Test
    fun `estado del plazo en plazo, por vencer y vencido`() {
        val desde = d("2026-11-03")
        val enPlazo = sinFeriados.plazo("R", UnidadPlazo.DIAS_HABILES, 15, desde, d("2026-11-04"))
        assertEquals("EN_PLAZO", enPlazo.estado)
        assertEquals(14, enPlazo.diasRestantes)
        assertEquals(6, enPlazo.avance)
        assertEquals("En plazo", enPlazo.estadoEtiqueta)

        val porVencer = sinFeriados.plazo("R", UnidadPlazo.DIAS_HABILES, 15, desde, d("2026-11-19"))
        assertEquals(3, porVencer.diasRestantes)
        assertEquals("POR_VENCER", porVencer.estado)

        val hoyVence = sinFeriados.plazo("R", UnidadPlazo.DIAS_HABILES, 15, desde, d("2026-11-24"))
        assertEquals(0, hoyVence.diasRestantes)
        assertEquals("POR_VENCER", hoyVence.estado)

        val vencido = sinFeriados.plazo("R", UnidadPlazo.DIAS_HABILES, 15, desde, d("2026-11-25"))
        assertEquals("VENCIDO", vencido.estado)
        assertEquals(0, vencido.diasRestantes)
        assertEquals(100, vencido.avance)
    }

    @Test
    fun `la vacancia cuenta dias corridos`() {
        val p = sinFeriados.plazo("VACANCIA_MAXIMA", UnidadPlazo.DIAS, 30, d("2026-11-20"), d("2026-11-20"))
        assertEquals(30, p.diasRestantes)
        assertEquals(0, p.avance)
        assertEquals("DIAS", p.unidad)
        assertEquals(d("2026-12-20"), p.vence)
    }

    @Test
    fun `habiles entre salta fines de semana`() {
        assertEquals(5, sinFeriados.habilesEntre(d("2026-11-20"), d("2026-11-27")))
        assertEquals(0, sinFeriados.habilesEntre(d("2026-11-27"), d("2026-11-20")))
    }
}
