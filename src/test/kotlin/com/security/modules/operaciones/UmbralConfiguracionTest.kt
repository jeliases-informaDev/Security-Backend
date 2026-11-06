package com.security.modules.operaciones

import com.security.modules.operaciones.FixturesOperaciones.catalogos
import com.security.modules.operaciones.application.service.*
import com.security.modules.operaciones.domain.ConfiguracionOperacionesEntity
import com.security.modules.operaciones.domain.Operacion
import com.security.modules.operaciones.domain.TipoCambioOperacionEntity
import com.security.modules.operaciones.domain.UmbralNormaEntity
import com.security.modules.operaciones.dto.ConfiguracionRequest
import com.security.modules.operaciones.dto.TipoCambioRequest
import com.security.modules.operaciones.dto.UmbralInternoDto
import com.security.modules.operaciones.infraestructure.ConfiguracionOperacionesRepository
import com.security.modules.operaciones.infraestructure.TipoCambioOperacionRepository
import com.security.modules.operaciones.infraestructure.UmbralNormaRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.*

class UmbralConfiguracionTest {
    private val catalogosRegistro = mockk<CatalogosRegistro> { every { cargar() } answers { catalogos() } }
    private val umbralRepo = mockk<UmbralNormaRepository>()
    private val configRepo = mockk<ConfiguracionOperacionesRepository>()
    private val tcRepo = mockk<TipoCambioOperacionRepository>()
    private val configuracion = ConfiguracionOperacionesService(configRepo, umbralRepo, catalogosRegistro)
    private val tiposCambio = TipoCambioService(tcRepo, catalogosRegistro)
    private val umbrales = UmbralService(configuracion, tiposCambio, catalogosRegistro, umbralRepo)
    private val autor = AutorEvento(1, "ana", "Ana Prueba")

    private val normas = listOf(
        UmbralNormaEntity(grupo = "INMOBILIARIO", tipoBien = "LOTE_TERRENO", nombre = "Lotes de terreno", umbralUsd = BigDecimal("30000.00"), orden = 1),
        UmbralNormaEntity(grupo = "INMOBILIARIO", tipoBien = "UNIDAD_INMOBILIARIA", nombre = "Vivienda", umbralUsd = BigDecimal("100000.00"), orden = 2)
    )
    private val octubre = YearMonth.of(2026, 10)
    private val noviembre5 = LocalDate.of(2026, 11, 5)

    private fun empresa(sector: String? = "CONSTRUCCION_INMOBILIARIA", interno: String? = null) {
        every { umbralRepo.findAllByGrupoOrderByOrdenAsc("INMOBILIARIO") } returns normas
        every { configRepo.findByEmpresa("empresa gamma") } returns ConfiguracionOperacionesEntity(
            empresa = "empresa gamma", sector = sector, umbralInternoUsd = interno?.let { BigDecimal(it) }, umbralInternoMotivo = interno?.let { "Motivo" }
        )
        every { tcRepo.findByEmpresaAndMonedaAndMes(any(), any(), any()) } returns null
    }

    private fun tc(moneda: String, valor: String, ambito: String = "empresa gamma") {
        every { tcRepo.findByEmpresaAndMonedaAndMes(ambito, moneda, octubre.toString()) } returns
            TipoCambioOperacionEntity(empresa = ambito, moneda = moneda, mes = octubre.toString(), valor = BigDecimal(valor))
    }

    private fun evaluar(tipo: String?, moneda: String, monto: String?) =
        umbrales.evaluar("Empresa Gamma", tipo, moneda, monto?.let { BigDecimal(it) }, noviembre5, catalogos())

    @Test
    fun `en dolares no hace falta tipo de cambio y compara con el umbral del tipo de bien`() {
        empresa()
        val v = evaluar("VENTA_DEPARTAMENTO", "USD", "128500.00")
        assertEquals("SI_SE_REGISTRA", v.estado)
        assertEquals(BigDecimal("128500.00"), v.montoUsd)
        assertEquals(BigDecimal("100000.00"), v.umbralUsd)
        assertEquals("UNIDAD_INMOBILIARIA", v.tipoBien)
        assertEquals("No aplica (USD)", v.tipoCambioNota)
        assertNull(v.tipoCambio)
        assertEquals("NO_ALCANZA_UMBRAL", evaluar("VENTA_LOTE", "USD", "29999.99").estado)
        assertEquals("SI_SE_REGISTRA", evaluar("VENTA_LOTE", "USD", "30000.00").estado)
    }

    @Test
    fun `en soles y euros usa el promedio del mes anterior, el de la empresa antes que el de Sulotec`() {
        empresa()
        tc("USD", "3.500000", TipoCambioOperacionEntity.SULOTEC)
        val soles = evaluar("VENTA_DEPARTAMENTO", "PEN", "300000.00")
        assertEquals(BigDecimal("85714.29"), soles.montoUsd)
        assertEquals("NO_ALCANZA_UMBRAL", soles.estado)
        assertEquals("2026-10", soles.tipoCambio!!.mes)
        assertEquals("Promedio de venta de octubre de 2026", soles.tipoCambioNota)

        tc("USD", "4.000000")
        assertEquals(BigDecimal("75000.00"), evaluar("VENTA_DEPARTAMENTO", "PEN", "300000.00").montoUsd)

        tc("EUR", "5.000000")
        val euros = evaluar("VENTA_DEPARTAMENTO", "EUR", "100000.00")
        assertEquals(BigDecimal("125000.00"), euros.montoUsd)
        assertEquals("SI_SE_REGISTRA", euros.estado)
    }

    @Test
    fun `sin tipo de cambio cargado lo dice y no inventa un monto`() {
        empresa()
        val v = evaluar("VENTA_DEPARTAMENTO", "PEN", "300000.00")
        assertEquals("SIN_TIPO_DE_CAMBIO", v.estado)
        assertEquals("Falta el tipo de cambio de octubre de 2026.", v.mensaje)
        assertNull(v.montoUsd)
    }

    @Test
    fun `sin sector, sin monto o con un tipo viejo no compara`() {
        empresa(sector = null)
        val sinSector = evaluar("VENTA_DEPARTAMENTO", "USD", "500000.00")
        assertEquals("SIN_CONFIGURAR", sinSector.estado)
        assertEquals(BigDecimal("500000.00"), sinSector.montoUsd) // el monto en US$ igual se guarda
        empresa(sector = "AGENTE_INMOBILIARIO")
        assertEquals("SIN_CONFIGURAR", evaluar("VENTA_DEPARTAMENTO", "USD", "500000.00").estado)
        empresa()
        assertEquals("SIN_MONTO", evaluar("VENTA_DEPARTAMENTO", "USD", null).estado)
        assertEquals("SIN_TIPO_DE_BIEN", evaluar("Deposito", "USD", "500000.00").estado)
    }

    @Test
    fun `el umbral interno manda si es menor que el de la norma`() {
        empresa(interno = "20000.00")
        val v = evaluar("VENTA_LOTE", "USD", "25000.00")
        assertEquals("SI_SE_REGISTRA", v.estado)
        assertEquals(BigDecimal("20000.00"), v.umbralUsd)
        assertEquals(BigDecimal("30000.00"), v.umbralNormaUsd)
        assertEquals(true, v.umbralInterno)
    }

    @Test
    fun `aplicar guarda la verificacion y dice si cambio el resultado`() {
        empresa()
        val o = Operacion(fechaOperacion = noviembre5, lugar = null, modalidadPago = "EFECTIVO", moneda = "USD",
            monto = BigDecimal("128500.00"), tipoOperacion = "VENTA_DEPARTAMENTO", cuenta1 = null, cuenta2 = null, cuenta3 = null)
        val v = evaluar("VENTA_DEPARTAMENTO", "USD", "128500.00")
        assertTrue(umbrales.aplicar(o, v, LocalDateTime.of(2026, 11, 5, 10, 0)))
        assertFalse(umbrales.aplicar(o, v, LocalDateTime.of(2026, 11, 5, 11, 0)))
        assertEquals("SI_SE_REGISTRA", o.umbralEstado)
        assertEquals(BigDecimal("128500.00"), o.montoUsd)
        assertEquals("US$ 128,500.00 es igual o superior a US$ 100,000.00: debe registrarse en el RO.", UmbralService.detalleEvento(v))
    }

    // ---------- Configuracion ----------

    @Test
    fun `la configuracion valida sector, umbral interno y oficinas`() {
        empresa()
        fun campos(r: ConfiguracionRequest) = assertFailsWith<OperacionesException> { configuracion.guardar("Empresa Gamma", r, autor) }.campos!!.keys
        assertEquals(setOf("sector"), campos(ConfiguracionRequest(sector = "INVENTADO")))
        assertEquals(setOf("umbralInterno.montoUsd"), campos(ConfiguracionRequest(sector = "CONSTRUCCION_INMOBILIARIA",
            umbralInterno = UmbralInternoDto(BigDecimal("100000.00"), "Motivo"))))
        assertEquals(setOf("umbralInterno.motivo"), campos(ConfiguracionRequest(sector = "CONSTRUCCION_INMOBILIARIA",
            umbralInterno = UmbralInternoDto(BigDecimal("20000.00"), null))))
        assertEquals(setOf("oficinas[1]"), campos(ConfiguracionRequest(oficinas = listOf("Lima", " lima "))))
        verify(exactly = 0) { configRepo.save(any()) }
    }

    @Test
    fun `guardar la configuracion queda en la clave de la empresa y la devuelve`() {
        empresa()
        val guardada = slot<ConfiguracionOperacionesEntity>()
        every { configRepo.save(capture(guardada)) } answers { firstArg() }
        configuracion.guardar(" Empresa Gamma ", ConfiguracionRequest(sector = "CONSTRUCCION_INMOBILIARIA", unRoPorActividad = true,
            umbralInterno = UmbralInternoDto(BigDecimal("20000"), "Perfil de prueba"), oficinas = listOf("Lima", "Sede Norte", " ")), autor)
        assertEquals("empresa gamma", guardada.captured.empresa)
        assertEquals(listOf("Lima", "Sede Norte"), guardada.captured.oficinas)
        assertEquals(BigDecimal("20000.00"), guardada.captured.umbralInternoUsd)
        assertEquals("Ana Prueba", guardada.captured.actualizadoPor)
    }

    // ---------- Tipo de cambio ----------

    @Test
    fun `el tipo de cambio valida moneda, mes y valor, y el universal guarda el de Sulotec`() {
        every { tcRepo.findByEmpresaAndMonedaAndMes(any(), any(), any()) } returns null
        fun campos(r: TipoCambioRequest) = assertFailsWith<OperacionesException> { tiposCambio.guardar("Empresa Gamma", r, autor) }.campos!!.keys
        assertEquals(setOf("moneda"), campos(TipoCambioRequest("PEN", "2026-10", BigDecimal("3.5"))))
        assertEquals(setOf("mes"), campos(TipoCambioRequest("USD", "2999-01", BigDecimal("3.5"))))
        assertEquals(setOf("valor"), campos(TipoCambioRequest("USD", "2026-10", BigDecimal.ZERO)))
        assertEquals(setOf("valor"), campos(TipoCambioRequest("USD", "2026-10", BigDecimal("3.1234567"))))

        val guardado = slot<TipoCambioOperacionEntity>()
        every { tcRepo.save(capture(guardado)) } answers { firstArg() }
        val r = tiposCambio.guardar(null, TipoCambioRequest("usd", "2026-10", BigDecimal("3.5")), autor)
        assertEquals(TipoCambioOperacionEntity.SULOTEC, guardado.captured.empresa)
        assertEquals("SULOTEC", r.ambito)
        assertEquals(BigDecimal("3.500000"), r.valor)
        tiposCambio.guardar("Empresa Gamma", TipoCambioRequest("USD", "2026-10", BigDecimal("3.6")), autor)
        assertEquals("empresa gamma", guardado.captured.empresa)
    }
}
