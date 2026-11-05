package com.security.modules.scoring.domain.metodologia

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El calculo de la metodologia version 2, sin base ni red. Los nombres de las opciones son los de los catalogos;
 * los puntajes vienen en la entrada (en la aplicacion salen de la base).
 */
class CalculoScoringTest {

    private val m = MetodologiaV2.ACTUAL

    private val sinPep = CondicionCliente(1, "Sin coincidencias en listas PEP", esPep = false, coincidenciasSanciones = 0)
    private val pep = CondicionCliente(4, "PEP", esPep = true, coincidenciasSanciones = 0)

    /** Todo en 1 (puntaje 1,00), con un cliente sin coincidencias. */
    private fun base() = EntradaCalculo(
        ocupacion = OpcionElegida("PROFESOR(MAESTRO Y/O PEDAGOGO", 1),
        condicion = sinPep,
        montoOperacion = BigDecimal("1000"),
        historial = OpcionElegida("Sin alertas", 1),
        producto = OpcionElegida("Alquiler de inmueble", 1),
        canal = OpcionElegida("Presencial", 1),
        medioPago = OpcionElegida("Tarjeta de crédito o débito", 1),
        departamentoResidencia = OpcionElegida("Moquegua", 1),
        departamentoOperacion = OpcionElegida("Moquegua", 1),
        pais = PaisElegido("Perú", 1, false)
    )

    private fun calcular(e: EntradaCalculo) = CalculoScoring.calcular(m, e)

    private fun promedio(r: ResultadoCalculo, grupo: String) = r.grupos.first { it.def.codigo == grupo }.promedio

    private fun factor(r: ResultadoCalculo, codigo: CodigoFactor) = r.grupos.flatMap { it.factores }.first { it.def.codigo == codigo }

    @Test
    fun `ejemplo de control de la especificacion`() {
        val r = calcular(
            EntradaCalculo(
                ocupacion = OpcionElegida("CONSTRUCTOR", 4),
                condicion = pep,
                montoOperacion = BigDecimal("5000000"),
                historial = OpcionElegida("Sin alertas", 1),
                producto = OpcionElegida("Venta de inmueble", 2),
                canal = OpcionElegida("Presencial", 1),
                medioPago = OpcionElegida("Transferencia bancaria", 2),
                departamentoResidencia = OpcionElegida("Lima", 2),
                departamentoOperacion = OpcionElegida("Lima", 2),
                pais = PaisElegido("Perú", 1, false)
            )
        )
        assertEquals(BigDecimal("3.75"), promedio(r, "CLIENTES"))
        assertEquals(BigDecimal("2.00"), promedio(r, "PRODUCTOS"))
        assertEquals(BigDecimal("1.50"), promedio(r, "ZONA"))
        assertEquals(BigDecimal("2.95"), r.puntaje)
        assertEquals(Nivel.ALTO, r.nivelPorPromedio)
        assertEquals(Nivel.ALTO, r.nivel)
        assertTrue(r.fijadoPorRegla)
        assertEquals(listOf(CodigoRegla.PEP, CodigoRegla.DOS_FACTORES_EN_4), r.reglas.map { it.codigo })
        assertEquals("Ocupación, condición y monto", r.reglas[1].detalle)
        assertEquals("60% × 3.75 + 20% × 2.00 + 20% × 1.50", r.formula)
        assertEquals("Nivel fijado por regla: cliente PEP", r.avisoRegla?.titulo)
        assertEquals(
            "El promedio ya da Alto (2.95). Aunque diera menos, dos reglas fijan Alto como nivel mínimo.",
            r.avisoRegla?.detalle
        )
        assertEquals(BigDecimal("1.00"), factor(r, CodigoFactor.OCUPACION).aporte)
        assertEquals("Constructor", factor(r, CodigoFactor.OCUPACION).dato)
        assertEquals("Presencial + Transferencia bancaria", factor(r, CodigoFactor.CANAL_MEDIO_PAGO).dato)
        assertTrue(r.bloqueos.isEmpty())
    }

    @Test
    fun `todo en 1 da 1,00 y Bajo sin reglas`() {
        val r = calcular(base())
        assertEquals(BigDecimal("1.00"), r.puntaje)
        assertEquals(Nivel.BAJO, r.nivel)
        assertTrue(r.reglas.isEmpty())
        assertNull(r.avisoRegla)
    }

    @Test
    fun `bandas en sus bordes`() {
        assertEquals(Nivel.BAJO, m.nivelDe(BigDecimal("1.00")))
        assertEquals(Nivel.BAJO, m.nivelDe(BigDecimal("1.74")))
        assertEquals(Nivel.MEDIO, m.nivelDe(BigDecimal("1.75")))
        assertEquals(Nivel.MEDIO, m.nivelDe(BigDecimal("2.49")))
        assertEquals(Nivel.ALTO, m.nivelDe(BigDecimal("2.50")))
        assertEquals(Nivel.ALTO, m.nivelDe(BigDecimal("4.00")))
    }

    @Test
    fun `1,75 calculado da Medio y 2,50 calculado da Alto por el promedio`() {
        // +25 ocupacion, +20 monto, +10 producto, +10 canal, +10 departamento = 175
        val medio = calcular(
            base().copy(
                ocupacion = OpcionElegida("TECNICO", 2),
                montoOperacion = BigDecimal("50000"),
                producto = OpcionElegida("Venta de inmueble", 2),
                canal = OpcionElegida("Portal web con identidad verificada", 2),
                departamentoResidencia = OpcionElegida("Lima", 2)
            )
        )
        assertEquals(BigDecimal("1.75"), medio.puntaje)
        assertEquals(Nivel.MEDIO, medio.nivel)

        // ocupacion 3 (+50), monto 3 (+40), producto 2 (+10), canal 3 (+20), departamento 3 (+20), pais 2 (+10) = 250
        val alto = calcular(
            base().copy(
                ocupacion = OpcionElegida("NOTARIO", 3),
                montoOperacion = BigDecimal("300000"),
                producto = OpcionElegida("Venta de inmueble", 2),
                canal = OpcionElegida("Telefónico o mensajería (WhatsApp)", 3),
                departamentoResidencia = OpcionElegida("Piura", 3),
                pais = PaisElegido("Chile", 2, false)
            )
        )
        assertEquals(BigDecimal("2.50"), alto.puntaje)
        assertEquals(Nivel.ALTO, alto.nivelPorPromedio)
        assertEquals(Nivel.ALTO, alto.nivel)
        assertTrue(alto.reglas.isEmpty())
        assertNull(alto.avisoRegla)
    }

    @Test
    fun `regla 1 - PEP fija Alto aunque el promedio de Bajo`() {
        val r = calcular(base().copy(condicion = pep))
        assertEquals(BigDecimal("1.30"), r.puntaje)
        assertEquals(Nivel.BAJO, r.nivelPorPromedio)
        assertEquals(Nivel.ALTO, r.nivel)
        assertEquals(listOf(CodigoRegla.PEP), r.reglas.map { it.codigo })
        assertEquals("El promedio da Bajo (1.30), pero una regla fija Alto como nivel mínimo.", r.avisoRegla?.detalle)
    }

    @Test
    fun `regla 2 - coincidencia en sanciones fija Alto`() {
        val r = calcular(base().copy(condicion = sinPep.copy(coincidenciasSanciones = 2)))
        assertEquals(Nivel.ALTO, r.nivel)
        assertEquals(listOf(CodigoRegla.SANCIONES), r.reglas.map { it.codigo })
        assertEquals(1, factor(r, CodigoFactor.CONDICION).puntaje)
    }

    @Test
    fun `regla 3 - pais de alto riesgo del GAFI fija Alto`() {
        val r = calcular(base().copy(pais = PaisElegido("Irán", 4, true)))
        assertEquals(Nivel.ALTO, r.nivel)
        assertEquals(listOf(CodigoRegla.PAIS_GAFI), r.reglas.map { it.codigo })

        val gris = calcular(base().copy(pais = PaisElegido("Bolivia", 4, false)))
        assertTrue(gris.reglas.isEmpty())
        assertEquals(Nivel.BAJO, gris.nivel)
    }

    @Test
    fun `regla 4 - dos factores en 4 fijan Alto y uno solo no`() {
        val uno = calcular(base().copy(producto = OpcionElegida("Cambio de moneda o activos virtuales", 4)))
        assertTrue(uno.reglas.isEmpty())

        val dos = calcular(
            base().copy(
                producto = OpcionElegida("Cambio de moneda o activos virtuales", 4),
                departamentoResidencia = OpcionElegida("Puno", 4)
            )
        )
        assertEquals(Nivel.ALTO, dos.nivel)
        assertEquals(listOf(CodigoRegla.DOS_FACTORES_EN_4), dos.reglas.map { it.codigo })
        assertEquals("Producto y departamento", dos.reglas[0].detalle)
    }

    @Test
    fun `regla 5 - mas de 100 000 con ocupacion de puntaje 1 o 2 fija Alto`() {
        val justo = calcular(base().copy(ocupacion = OpcionElegida("TECNICO", 2), montoOperacion = BigDecimal("100000")))
        assertTrue(justo.reglas.isEmpty())

        val mas = calcular(base().copy(ocupacion = OpcionElegida("TECNICO", 2), montoOperacion = BigDecimal("100000.01")))
        assertEquals(listOf(CodigoRegla.MONTO_ALTO_OCUPACION_BAJA), mas.reglas.map { it.codigo })
        assertEquals(Nivel.ALTO, mas.nivel)

        val ocupacionAlta = calcular(base().copy(ocupacion = OpcionElegida("NOTARIO", 3), montoOperacion = BigDecimal("200000")))
        assertTrue(ocupacionAlta.reglas.isEmpty())
    }

    @Test
    fun `regla 6 - ocupacion sin puntaje bloquea y no hay nivel`() {
        val r = calcular(base().copy(ocupacion = OpcionElegida("PINTOR", null)))
        assertNull(r.nivel)
        assertNull(r.puntaje)
        assertNull(promedio(r, "CLIENTES"))
        assertNotNull(promedio(r, "PRODUCTOS"))
        assertEquals(listOf(CampoScoring.OCUPACION), r.bloqueos.map { it.campo })
    }

    @Test
    fun `regla 6 - departamento o pais sin puntaje bloquean`() {
        val dep = calcular(base().copy(departamentoOperacion = OpcionElegida("Otro", null)))
        assertNull(dep.nivel)
        assertEquals(listOf(CampoScoring.DEPARTAMENTO_OPERACION), dep.bloqueos.map { it.campo })

        val pais = calcular(base().copy(pais = PaisElegido("Atlantida", null, false)))
        assertNull(pais.nivel)
        assertEquals(listOf(CampoScoring.PAIS), pais.bloqueos.map { it.campo })
    }

    @Test
    fun `regla 6 - falta de datos obligatorios bloquea cada campo`() {
        val r = calcular(EntradaCalculo())
        assertNull(r.nivel)
        assertNull(r.puntaje)
        assertEquals(
            setOf(
                CampoScoring.OCUPACION, CampoScoring.CLIENTE, CampoScoring.MONTO, CampoScoring.PRODUCTO, CampoScoring.CANAL,
                CampoScoring.MEDIO_PAGO, CampoScoring.DEPARTAMENTO_RESIDENCIA, CampoScoring.DEPARTAMENTO_OPERACION, CampoScoring.PAIS
            ),
            r.bloqueos.map { it.campo }.toSet()
        )
        assertEquals("60% × — + 20% × — + 20% × —", r.formula)
    }

    @Test
    fun `regla 6 - monto negativo y bloqueos previos sin repetir el campo`() {
        val r = calcular(
            base().copy(
                montoOperacion = BigDecimal("-1"),
                condicion = null,
                bloqueosPrevios = listOf(Bloqueo(CampoScoring.CLIENTE, "Ese cliente no está en Listas Negativas."))
            )
        )
        assertNull(r.nivel)
        assertEquals(1, r.bloqueos.count { it.campo == CampoScoring.CLIENTE })
        assertEquals("Ese cliente no está en Listas Negativas.", r.bloqueos.first { it.campo == CampoScoring.CLIENTE }.mensaje)
        assertTrue(r.bloqueos.any { it.campo == CampoScoring.MONTO })
    }

    @Test
    fun `una regla nunca levanta un bloqueo`() {
        val r = calcular(base().copy(condicion = pep, ocupacion = OpcionElegida("PINTOR", null)))
        assertNull(r.nivel)
        assertTrue(r.reglas.any { it.codigo == CodigoRegla.PEP })
    }

    @Test
    fun `canal y medio de pago toman el mayor de los dos`() {
        val canalAlto = calcular(base().copy(canal = OpcionElegida("Telefónico o mensajería (WhatsApp)", 3)))
        assertEquals(3, factor(canalAlto, CodigoFactor.CANAL_MEDIO_PAGO).puntaje)

        val medioAlto = calcular(base().copy(medioPago = OpcionElegida("Criptoactivos", 4)))
        assertEquals(4, factor(medioAlto, CodigoFactor.CANAL_MEDIO_PAGO).puntaje)
        assertEquals(BigDecimal("2.50"), promedio(medioAlto, "PRODUCTOS"))

        val medioSinPuntaje = calcular(base().copy(medioPago = OpcionElegida("Otro", null)))
        assertEquals(listOf(CampoScoring.MEDIO_PAGO), medioSinPuntaje.bloqueos.map { it.campo })
    }

    @Test
    fun `departamento toma el mayor entre residencia y operacion`() {
        val r = calcular(base().copy(departamentoOperacion = OpcionElegida("Callao", 4)))
        assertEquals(4, factor(r, CodigoFactor.DEPARTAMENTO).puntaje)
        assertEquals("Moquegua (residencia) / Callao (operación)", factor(r, CodigoFactor.DEPARTAMENTO).dato)

        val alReves = calcular(base().copy(departamentoResidencia = OpcionElegida("Tacna", 3)))
        assertEquals(3, factor(alReves, CodigoFactor.DEPARTAMENTO).puntaje)
    }

    @Test
    fun `monto - rangos y el mayor entre la operacion y el promedio del registro`() {
        listOf("0" to 1, "20000" to 1, "20000.01" to 2, "100000" to 2, "100000.01" to 3, "500000" to 3, "500000.01" to 4)
            .forEach { (monto, esperado) ->
                val r = calcular(base().copy(montoOperacion = BigDecimal(monto)))
                assertEquals(esperado, factor(r, CodigoFactor.MONTO).puntaje, "monto $monto")
            }

        val conRegistro = calcular(
            base().copy(montoOperacion = BigDecimal("10000"), promedioMensual = BigDecimal("600000"), notaRegistro = "Promedio del registro.")
        )
        assertEquals(4, factor(conRegistro, CodigoFactor.MONTO).puntaje)
        assertEquals(
            "Puntaje 4: más de S/ 500,000. Promedio del registro. Se toma el mayor: el promedio mensual del Registro de Operaciones.",
            conRegistro.montoNota
        )
        // La regla 5 mira solo el monto de la operacion, no el promedio
        assertTrue(conRegistro.reglas.isEmpty())

        val menor = calcular(base().copy(montoOperacion = BigDecimal("30000"), promedioMensual = BigDecimal("100")))
        assertEquals(2, factor(menor, CodigoFactor.MONTO).puntaje)
    }

    @Test
    fun `los pesos y las bandas de la metodologia son coherentes`() {
        assertEquals(100, m.factores.sumOf { it.peso })
        assertEquals(listOf(60, 20, 20), m.grupos.map { it.peso })
        assertEquals(8, m.factores.size)
    }
}
