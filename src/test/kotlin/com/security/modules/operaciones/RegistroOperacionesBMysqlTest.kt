package com.security.modules.operaciones

import com.security.modules.operaciones.domain.*
import com.security.modules.operaciones.dto.FiltrosOperaciones
import com.security.modules.operaciones.infraestructure.*
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.*

/** Entrega B contra la MySQL del CI: estados, oficina y monto en US$ en la lista, cifras y periodo, siempre por empresa. */
@SpringBootTest
class RegistroOperacionesBMysqlTest {

    @Autowired lateinit var usuarios: UsuarioRepository
    @Autowired lateinit var operaciones: OperacionRepository
    @Autowired lateinit var consulta: ListaOperacionesConsulta
    @Autowired lateinit var analisis: AnalisisOperacionRepository
    @Autowired lateinit var tiposCambio: TipoCambioOperacionRepository
    @Autowired lateinit var configuraciones: ConfiguracionOperacionesRepository
    @Autowired lateinit var umbrales: UmbralNormaRepository
    @Autowired lateinit var transacciones: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transacciones) }
    private val sufijo = UUID.randomUUID().toString().take(8)

    private fun cuenta(nombre: String, empresa: String): Usuario = usuarios.save(
        Usuario(usuario = "$nombre.$sufijo", clave = "x", correo = "$nombre.$sufijo@example.test", nombres = nombre,
            empresa = empresa, activo = true, fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now())
    )

    private fun guardar(duenio: Usuario, numero: String, oficina: String, montoUsd: String?, fecha: LocalDate,
                        enviado: LocalDate? = null): RegistroOperacionesEntity = tx.execute {
        operaciones.saveAndFlush(
            RegistroOperacionesEntity(
                numeroRegistro = numero, idUsuario = requireNotNull(duenio.id), empresa = duenio.empresa!!, oficina = oficina,
                fechaRegistro = fecha, fechaCreacion = fecha.atStartOfDay(),
                personaFisica = Persona().apply { tipoDocumento = "DNI"; numeroDocumento = "00000001"; nombres = "Ana"; apellidos = "Ficticia" },
                personaRepresentada = null,
                operacion = Operacion(fechaOperacion = fecha, lugar = null, modalidadPago = "EFECTIVO", moneda = "USD",
                    monto = BigDecimal("100.00"), tipoOperacion = "VENTA_LOTE", cuenta1 = null, cuenta2 = null, cuenta3 = null,
                    montoUsd = montoUsd?.let { BigDecimal(it) }, umbralEstado = "SI_SE_REGISTRA"),
                fechaEnvio = enviado,
                beneficiarios = listOf(BeneficiarioEntity(nombre = "Persona Ficticia", tipoDocumento = "DNI", numeroDocumento = "00000009"))
            )
        )
    }!!

    @Test
    fun `estados, oficina y monto en dolares se filtran en la base y solo dentro de la empresa`() {
        val a = cuenta("ana", "Empresa BA $sufijo")
        val b = cuenta("luis", "Empresa BB $sufijo")
        val octubre = LocalDate.of(2026, 10, 10)
        val pendiente = guardar(a, "RO-2026-000001", "Lima", "50000.00", octubre)
        val enviado = guardar(a, "RO-2026-000002", "Sede Norte", "150000.00", octubre, enviado = LocalDate.of(2026, 11, 2))
        val inusual = guardar(a, "RO-2026-000003", "Lima", null, octubre)
        analisis.save(AnalisisOperacionEntity(registroId = requireNotNull(inusual.id), estado = "EN_ANALISIS"))
        val ajeno = guardar(b, "RO-2026-000001", "Lima", "50000.00", octubre)
        analisis.save(AnalisisOperacionEntity(registroId = requireNotNull(ajeno.id), estado = "SOSPECHOSA"))
        val empresa = a.empresa!!

        fun ids(f: FiltrosOperaciones) = consulta.listar(empresa, f, 0, 10).first.map { it.id }.toSet()
        assertEquals(setOf(pendiente.id, inusual.id), ids(FiltrosOperaciones(estados = setOf("PENDIENTE"))))
        assertEquals(setOf(enviado.id), ids(FiltrosOperaciones(estados = setOf("ENVIADO"))))
        assertEquals(setOf(inusual.id), ids(FiltrosOperaciones(estados = setOf("INUSUAL"))))
        assertEquals(setOf(enviado.id, inusual.id), ids(FiltrosOperaciones(estados = setOf("ENVIADO", "INUSUAL"))))
        assertEquals(setOf(enviado.id), ids(FiltrosOperaciones(oficina = " sede norte ")))
        assertEquals(setOf(enviado.id), ids(FiltrosOperaciones(montoMinUsd = BigDecimal("100000"))))
        assertEquals(setOf(pendiente.id), ids(FiltrosOperaciones(montoMaxUsd = BigDecimal("100000"))))
        val fila = consulta.listar(empresa, FiltrosOperaciones(estados = setOf("INUSUAL")), 0, 10).first.single()
        assertEquals("EN_ANALISIS", fila.estadoAnalisis)

        val c = consulta.conteos(empresa, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(3L, c.total)
        assertEquals(1L, c.enviados)
        assertEquals(1L, c.inusuales)
        assertEquals(0L, c.sospechosas) // la sospechosa es de la otra empresa
        assertEquals(1L, consulta.conteos(b.empresa!!, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).sospechosas)

        val periodo = consulta.delPeriodo(empresa, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(setOf(pendiente.id, enviado.id, inusual.id), periodo.map { it.first }.toSet())
        assertEquals(LocalDate.of(2026, 11, 2), periodo.single { it.first == enviado.id }.second)
        assertTrue(consulta.delPeriodo(empresa, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).isEmpty())
    }

    @Test
    fun `un analisis por registro, un tipo de cambio por empresa, moneda y mes, y los umbrales sembrados`() {
        val a = cuenta("eva", "Empresa BC $sufijo")
        val r = guardar(a, "RO-2026-000001", "Lima", null, LocalDate.of(2026, 10, 1))
        analisis.save(AnalisisOperacionEntity(registroId = requireNotNull(r.id), estado = "EN_ANALISIS"))
        assertFailsWith<DataIntegrityViolationException> {
            analisis.saveAndFlush(AnalisisOperacionEntity(registroId = requireNotNull(r.id), estado = "EN_ANALISIS"))
        }
        val clave = "empresa bc $sufijo"
        tiposCambio.saveAndFlush(TipoCambioOperacionEntity(empresa = clave, moneda = "USD", mes = "2026-09", valor = BigDecimal("3.5")))
        assertFailsWith<DataIntegrityViolationException> {
            tiposCambio.saveAndFlush(TipoCambioOperacionEntity(empresa = clave, moneda = "USD", mes = "2026-09", valor = BigDecimal("3.6")))
        }
        assertEquals(BigDecimal("3.500000"), tiposCambio.findByEmpresaAndMonedaAndMes(clave, "USD", "2026-09")!!.valor)
        configuraciones.saveAndFlush(ConfiguracionOperacionesEntity(empresa = clave, sector = "CONSTRUCCION_INMOBILIARIA").apply {
            oficinas.addAll(listOf("Lima", "Sede Norte"))
        })
        assertEquals(listOf("Lima", "Sede Norte"), configuraciones.findByEmpresa(clave)!!.oficinas)
        assertEquals(listOf("LOTE_TERRENO", "UNIDAD_INMOBILIARIA"), umbrales.findAllByGrupoOrderByOrdenAsc("INMOBILIARIO").map { it.tipoBien })
    }
}
