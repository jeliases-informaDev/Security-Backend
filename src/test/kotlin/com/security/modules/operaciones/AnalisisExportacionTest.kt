package com.security.modules.operaciones

import com.security.modules.denuncias.application.port.ArchivoGuardado
import com.security.modules.denuncias.application.port.ArchivoStorageService
import com.security.modules.denuncias.domain.enums.ProveedorAlmacenamientoArchivo
import com.security.modules.operaciones.FixturesOperaciones.catalogos
import com.security.modules.operaciones.FixturesOperaciones.registro
import com.security.modules.operaciones.FixturesOperaciones.registroViejo
import com.security.modules.operaciones.application.service.*
import com.security.modules.operaciones.domain.AnalisisOperacionEntity
import com.security.modules.operaciones.domain.ExportacionOperacionesEntity
import com.security.modules.operaciones.dto.*
import com.security.modules.operaciones.infraestructure.*
import com.security.shared.exceptions.ResourceNotFoundException
import io.mockk.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

class AnalisisExportacionTest {
    private val operaciones = mockk<OperacionRepository>()
    private val analisisRepo = mockk<AnalisisOperacionRepository>()
    private val adjuntos = mockk<AdjuntoAnalisisRepository> { every { findAllByRegistroIdOrderBySubidoEnAscIdAsc(any()) } returns emptyList() }
    private val catalogosRegistro = mockk<CatalogosRegistro> { every { cargar() } answers { catalogos() } }
    private val historial = mockk<HistorialOperacionService>(relaxed = true)
    private val almacenamiento = mockk<ArchivoStorageService>()
    private val servicio = AnalisisOperacionService(operaciones, analisisRepo, adjuntos, catalogosRegistro, historial, mockk(), almacenamiento)
    private val autor = AutorEvento(1, "ana", "Ana Prueba")
    private val hoy = LocalDate.now(ZONA_LIMA)

    private fun preparar(analisis: AnalisisOperacionEntity? = null) {
        every { operaciones.findById(42) } returns Optional.of(registro())
        every { analisisRepo.findByRegistroId(42) } returns analisis
        every { analisisRepo.save(any()) } answers { firstArg() }
    }

    private val pedido = InusualidadRequest("PAGO_FRACCIONADO", null, "REVISION_INTERNA", hoy, "Texto de prueba", null, "Análisis de prueba")

    @Test
    fun `guardar el analisis pide señal, fuente y fecha, y otra exige descripcion`() {
        preparar()
        fun campos(r: InusualidadRequest) = assertFailsWith<OperacionesException> { servicio.guardar(42, r, autor, false) }.campos!!.keys
        assertEquals(setOf("senal", "fuente", "fechaDeteccion"), campos(InusualidadRequest()))
        assertEquals(setOf("senalTexto"), campos(pedido.copy(senal = "OTRA")))
        assertEquals(setOf("fechaDeteccion"), campos(pedido.copy(fechaDeteccion = hoy.plusDays(1))))
        val r = servicio.guardar(42, pedido, autor, false)
        assertEquals("EN_ANALISIS", r.estado)
        assertEquals("Pago fraccionado sin justificación", r.senalNombre)
        assertEquals(2, r.datosOperacion.participantes)
        verify { historial.anotar(42, "ANALISIS", "Análisis de inusualidad guardado", any(), autor, any()) }
    }

    @Test
    fun `calificar como sospechosa abre el plazo de 24 horas y no se puede volver a calificar`() {
        val a = AnalisisOperacionEntity(registroId = 42, estado = "EN_ANALISIS", senal = "PAGO_FRACCIONADO", fuente = "REVISION_INTERNA")
        preparar(a)
        assertEquals(setOf("analisis"), assertFailsWith<OperacionesException> {
            servicio.decidir(42, DecisionRequest("SOSPECHOSA"), autor)
        }.campos!!.keys)
        a.analisis = "Análisis de prueba"
        assertEquals(setOf("decision"), assertFailsWith<OperacionesException> { servicio.decidir(42, DecisionRequest("TALVEZ"), autor) }.campos!!.keys)
        val r = servicio.decidir(42, DecisionRequest("sospechosa"), autor)
        assertEquals("SOSPECHOSA", r.estado)
        assertEquals(r.calificadaEn!!.plusHours(24), r.venceEn)
        assertEquals(CodigosOperaciones.ANALISIS_CERRADO, assertFailsWith<OperacionesException> {
            servicio.decidir(42, DecisionRequest("NO_SOSPECHOSA"), autor)
        }.code)
        assertEquals(CodigosOperaciones.ANALISIS_CERRADO, assertFailsWith<OperacionesException> { servicio.guardar(42, pedido, autor, true) }.code)
        val ros = servicio.ros(42, true)
        assertEquals("EN_PLAZO", ros.estadoPlazo)
        assertTrue(ros.restanteSegundos in 1..86400)
        assertTrue(ros.datosListos)
    }

    @Test
    fun `el ROS solo existe para una sospechosa, se registra una vez y no antes de la calificacion`() {
        preparar(AnalisisOperacionEntity(registroId = 42, estado = "EN_ANALISIS"))
        assertEquals(CodigosOperaciones.SIN_ROS, assertFailsWith<OperacionesException> { servicio.ros(42, true) }.code)
        val calificada = LocalDateTime.now(ZONA_LIMA).minusHours(30).withNano(0)
        val a = AnalisisOperacionEntity(registroId = 42, estado = "SOSPECHOSA", senal = "OTRA", analisis = "x", decision = "SOSPECHOSA",
            calificadaEn = calificada, venceEn = calificada.plusHours(24))
        preparar(a)
        assertEquals("VENCIDO", servicio.ros(42, true).estadoPlazo)
        assertEquals(setOf("enviadoEn"), assertFailsWith<OperacionesException> {
            servicio.registrarRos(42, RosRequest("ROS-PRUEBA-1", calificada.minusHours(1)), autor)
        }.campos!!.keys)
        val r = servicio.registrarRos(42, RosRequest("ROS-PRUEBA-1", calificada.plusHours(25)), autor)
        assertEquals("CERRADO", r.estadoPlazo)
        assertEquals(true, r.enviadoFueraDePlazo)
        assertEquals(0L, r.restanteSegundos)
        assertEquals(CodigosOperaciones.ROS_YA_REGISTRADO, assertFailsWith<OperacionesException> {
            servicio.registrarRos(42, RosRequest("ROS-PRUEBA-2", calificada.plusHours(2)), autor)
        }.code)
    }

    @Test
    fun `un adjunto de otro registro no se entrega`() {
        every { adjuntos.findByIdAndRegistroId(5, 42) } returns null
        assertEquals(CodigosOperaciones.ADJUNTO_NO_ENCONTRADO,
            assertFailsWith<ResourceNotFoundException> { servicio.descargarAdjunto(42, 5) }.code)
        verify { almacenamiento wasNot Called }
    }

    // ---------- Exportacion ----------

    private val consulta = mockk<ListaOperacionesConsulta>()
    private val exportRepo = mockk<ExportacionOperacionesRepository>()
    private val catalogoRepo = mockk<CatalogoRepository> {
        every { paises() } returns listOf(CatalogoOpcionResponse(1, "Perú"))
    }
    private val configuracion = mockk<ConfiguracionOperacionesService> {
        every { de(any(), any()) } returns ConfiguracionEfectiva(null, null, null, emptyList(), null, null, null, emptyList(), false, null)
    }
    private val exportacion = ExportacionOperacionesService(consulta, operaciones, exportRepo, analisisRepo,
        mockk<com.security.shared.datos.repositories.UsuarioRepository> { every { findAllById(any()) } returns emptyList() },
        catalogosRegistro, catalogoRepo, configuracion, historial, mockk(), almacenamiento)

    @Test
    fun `la validacion separa pendientes y enviados y lista lo que falta`() {
        val viejo = registroViejo(43).apply { personaFisica.nacionalidad = "Narnia" }
        every { consulta.delPeriodo("Empresa Gamma", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)) } returns
            listOf(43L to null, 44L to LocalDate.of(2026, 11, 1))
        every { operaciones.findAllById(listOf(43L)) } returns listOf(viejo)
        val v = exportacion.validacion("Empresa Gamma", "2026-10", null, true)
        assertEquals(2, v.registros)
        assertEquals(1, v.pendientes)
        assertEquals(1, v.enviados)
        assertEquals("DISPOSICION_UIF", v.formato)
        assertTrue(v.formatoDisponible)
        val textos = v.observaciones.map { it.texto }
        assertTrue("Falta el origen de los fondos" in textos)
        assertTrue("Falta el N.º de comprobante" in textos)
        assertTrue("Falta el tipo de cambio de septiembre de 2026" in textos, textos.toString())
        assertTrue(ExportacionOperacionesService.PAIS_INVALIDO in textos)
        assertFalse(v.chequeos.single { it.codigo == "MONEDAS_Y_PAISES" }.ok)
        assertTrue(v.chequeos.single { it.codigo == "PERSONA_Y_BENEFICIARIO" }.ok)

        val pronto = exportacion.validacion("Empresa Gamma", "2026-10", "NOTARIOS", true)
        assertFalse(pronto.formatoDisponible)
        assertFalse(pronto.puedeGenerar)
    }

    @Test
    fun `generar con un formato sin estructura o sin pendientes no genera nada`() {
        assertEquals(CodigosOperaciones.FORMATO_PRONTO, assertFailsWith<OperacionesException> {
            exportacion.generar("Empresa Gamma", ExportacionRequest("2026-10", "SISTEMA_FINANCIERO"), autor)
        }.code)
        every { consulta.delPeriodo(any(), any(), any()) } returns listOf(44L to LocalDate.of(2026, 11, 1))
        every { operaciones.findAllById(emptyList()) } returns emptyList()
        assertEquals(CodigosOperaciones.SIN_REGISTROS, assertFailsWith<OperacionesException> {
            exportacion.generar("Empresa Gamma", ExportacionRequest("2026-10", null), autor)
        }.code)
        assertEquals(CodigosOperaciones.PERIODO_INVALIDO, assertFailsWith<OperacionesException> {
            exportacion.generar("Empresa Gamma", ExportacionRequest("octubre", null), autor)
        }.code)
        verify { almacenamiento wasNot Called }
    }

    @Test
    fun `generar guarda el Excel y el CSV de los pendientes con la advertencia de que no es oficial`() {
        val r = registro()
        every { consulta.delPeriodo(any(), any(), any()) } returns listOf(42L to null)
        every { operaciones.findAllById(listOf(42L)) } returns listOf(r)
        every { analisisRepo.findByRegistroId(42) } returns null
        val contenidos = mutableMapOf<String, ByteArray>()
        every { almacenamiento.guardar(any(), any(), any()) } answers {
            contenidos[firstArg()] = secondArg()
            ArchivoGuardado(firstArg(), ProveedorAlmacenamientoArchivo.values().first())
        }
        every { exportRepo.save(any()) } answers { firstArg<ExportacionOperacionesEntity>().let { spyk(it).also { s -> every { s.id } returns 5L } } }
        val e = exportacion.generar("Empresa Gamma", ExportacionRequest("2026-11", null), autor)
        assertEquals(1, e.registros)
        assertEquals("GENERADO", e.estado)
        assertEquals("RO_2026-11_disposicion_UIF.xlsx", e.archivoXlsx)
        val xlsx = contenidos.entries.single { it.key.endsWith(".xlsx") }.value
        XSSFWorkbook(xlsx.inputStream()).use { libro ->
            val hoja = libro.getSheet("RO")
            assertEquals(ExportadorRo.COLUMNAS.size, hoja.getRow(0).lastCellNum.toInt())
            assertEquals("RO-2026-000001", hoja.getRow(1).getCell(0).stringCellValue)
            assertTrue(libro.getSheet("Léeme").getRow(1).getCell(0).stringCellValue.startsWith("No es una plantilla oficial"))
        }
        val csv = contenidos.entries.single { it.key.endsWith(".csv") }.value.toString(Charsets.UTF_8)
        assertTrue(csv.startsWith("﻿N.º de registro;"))
        assertTrue(csv.contains("RO-2026-000001;"))
    }

    @Test
    fun `una exportacion de otra empresa no se ve ni se le registra constancia`() {
        every { exportRepo.findById(5) } returns Optional.of(ExportacionOperacionesEntity(id = 5, empresa = "empresa delta",
            periodo = "2026-10", formato = "DISPOSICION_UIF", generadoEn = LocalDateTime.now(), generadoPor = null))
        listOf<() -> Unit>(
            { exportacion.ver("Empresa Gamma", 5, true) },
            { exportacion.archivo("Empresa Gamma", 5, "csv") },
            { exportacion.constancia("Empresa Gamma", 5, hoy, "ACUSE-1", null, null, autor) }
        ).forEach { accion ->
            assertEquals(CodigosOperaciones.EXPORTACION_NO_ENCONTRADA, assertFailsWith<ResourceNotFoundException> { accion() }.code)
        }
    }

    @Test
    fun `la constancia pasa a enviado los pendientes de la exportacion, una sola vez`() {
        val e = ExportacionOperacionesEntity(id = 5, empresa = "empresa gamma", periodo = "2026-11", formato = "DISPOSICION_UIF",
            generadoEn = LocalDateTime.now(), generadoPor = null, registros = listOf(42L))
        val r = registro()
        every { exportRepo.findById(5) } returns Optional.of(e)
        every { exportRepo.save(any()) } answers { firstArg() }
        every { operaciones.findAllById(listOf(42L)) } returns listOf(r)
        every { operaciones.flush() } just Runs
        assertEquals(setOf("fechaEnvio", "numeroAcuse"), assertFailsWith<OperacionesException> {
            exportacion.constancia("Empresa Gamma", 5, null, " ", null, null, autor)
        }.campos!!.keys)
        val c = exportacion.constancia("Empresa Gamma", 5, hoy, "ACUSE-PRUEBA-1", null, null, autor)
        assertEquals(1, c.enviados)
        assertEquals(hoy, r.fechaEnvio)
        assertEquals(5L, r.idExportacionEnvio)
        verify { historial.anotar(42, "ENVIO", "Envío a la UIF registrado", any(), autor, any()) }
        assertEquals(CodigosOperaciones.CONSTANCIA_YA_REGISTRADA, assertFailsWith<OperacionesException> {
            exportacion.constancia("Empresa Gamma", 5, hoy, "ACUSE-PRUEBA-2", null, null, autor)
        }.code)
    }
}
