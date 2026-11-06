package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.EsquemaOperacionesInicializador
import com.security.modules.operaciones.domain.*
import com.security.modules.operaciones.dto.FiltrosOperaciones
import com.security.modules.operaciones.infraestructure.HistorialOperacionRepository
import com.security.modules.operaciones.infraestructure.ListaOperacionesConsulta
import com.security.modules.operaciones.infraestructure.NumeradorRegistros
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Contra la MySQL real del CI (la misma que usa contextLoads): el SQL nativo de la lista, los indicadores y el numerador,
 * los indices unicos y el esquema. Cada prueba usa empresas con nombre aleatorio para no chocar con otras.
 */
@SpringBootTest
class RegistroOperacionesMysqlTest {

    @Autowired lateinit var usuarios: UsuarioRepository
    @Autowired lateinit var operaciones: OperacionRepository
    @Autowired lateinit var numerador: NumeradorRegistros
    @Autowired lateinit var consulta: ListaOperacionesConsulta
    @Autowired lateinit var historial: HistorialOperacionRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var esquema: EsquemaOperacionesInicializador
    @Autowired lateinit var transacciones: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transacciones) }
    private val sufijo = UUID.randomUUID().toString().take(8)

    private fun cuenta(nombre: String, empresa: String): Usuario = usuarios.save(
        Usuario(
            usuario = "$nombre.$sufijo", clave = "x", correo = "$nombre.$sufijo@example.test", nombres = nombre, apePat = "Prueba",
            empresa = empresa, activo = true, fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now()
        )
    )

    private fun guardar(
        duenio: Usuario, numero: String, documento: String, moneda: String = "USD", tipo: String = "VENTA_LOTE",
        responsable: Int? = duenio.id, fechaOperacion: LocalDate = LocalDate.of(2026, 11, 5), empresa: String = duenio.empresa!!
    ): RegistroOperacionesEntity = tx.execute {
        operaciones.saveAndFlush(
            RegistroOperacionesEntity(
                numeroRegistro = numero, idUsuario = requireNotNull(duenio.id), empresa = empresa, oficina = "Lima",
                fechaRegistro = LocalDate.of(2026, 11, 5), fechaCreacion = LocalDateTime.of(2026, 11, 5, 10, 0),
                personaFisica = Persona().apply { tipoDocumento = "DNI"; numeroDocumento = documento; nombres = "Ana"; apellidos = "Ficticia $sufijo" },
                personaRepresentada = null,
                operacion = Operacion(fechaOperacion = fechaOperacion, lugar = null, modalidadPago = "EFECTIVO", moneda = moneda,
                    monto = BigDecimal("100.00"), tipoOperacion = tipo, cuenta1 = null, cuenta2 = null, cuenta3 = null),
                idUsuarioResponsable = responsable,
                beneficiarios = listOf(BeneficiarioEntity(nombre = "Persona Ficticia", tipoDocumento = "DNI", numeroDocumento = "00000009"))
            )
        )
    }!!

    @Test
    fun `cada empresa ve solo sus registros, sus filtros y sus cifras`() {
        val empresaA = "Empresa A $sufijo"
        val empresaB = "Empresa B $sufijo"
        val ana = cuenta("ana", empresaA)
        val luis = cuenta("luis", empresaB)
        val a1 = guardar(ana, "RO-2026-000001", "11111111", moneda = "PEN — Soles", tipo = "Deposito")
        val a2 = guardar(ana, "RO-2026-000002", "22222222", responsable = null, fechaOperacion = LocalDate.of(2026, 10, 1))
        val b1 = guardar(luis, "RO-2026-000001", "11111111")

        val (filasA, totalA) = consulta.listar(empresaA, FiltrosOperaciones(), 0, 10)
        assertEquals(2L, totalA)
        assertEquals(setOf(a1.id, a2.id), filasA.map { it.id }.toSet())
        assertTrue(filasA.none { it.id == b1.id })
        // el mismo documento existe en B: el buscador de A no lo trae
        assertEquals(listOf(a1.id), consulta.listar(empresaA, FiltrosOperaciones(q = "11111111"), 0, 10).first.map { it.id })
        assertEquals(listOf(b1.id), consulta.listar(empresaB.uppercase(), FiltrosOperaciones(q = "11111111"), 0, 10).first.map { it.id })
        // filtros
        assertEquals(listOf(a1.id), consulta.listar(empresaA, FiltrosOperaciones(moneda = "PEN", monedaId = 999), 0, 10).first.map { it.id })
        assertEquals(listOf(a2.id), consulta.listar(empresaA, FiltrosOperaciones(sinResponsable = true), 0, 10).first.map { it.id })
        assertEquals(listOf(a1.id), consulta.listar(empresaA, FiltrosOperaciones(responsableId = ana.id), 0, 10).first.map { it.id })
        assertEquals(listOf(a1.id), consulta.listar(empresaA, FiltrosOperaciones(tipoOperacion = listOf("Deposito")), 0, 10).first.map { it.id })
        assertEquals(listOf(a2.id), consulta.listar(empresaA, FiltrosOperaciones(hasta = LocalDate.of(2026, 10, 31)), 0, 10).first.map { it.id })
        assertTrue(consulta.listar(empresaA, FiltrosOperaciones(responsableId = luis.id), 0, 10).first.isEmpty())
        // la fila trae el responsable con su nombre
        assertEquals("ana Prueba", filasA.single { it.id == a1.id }.responsableNombre)

        val cifrasA = consulta.conteos(empresaA, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(2L, cifrasA.total)
        assertEquals(1L, cifrasA.sinResponsable)
        assertEquals(2L, cifrasA.delPeriodo)
        assertEquals(1L, consulta.conteos(empresaB, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).total)
        assertEquals(0L, consulta.conteos("Empresa C $sufijo", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).total)
    }

    @Test
    fun `el numero es correlativo por empresa y año, no se repite con altas simultaneas`() {
        val empresa = "Empresa N $sufijo"
        val otra = "Empresa M $sufijo"
        val pool = Executors.newFixedThreadPool(4)
        val salida = CountDownLatch(1)
        val tareas = (1..4).map {
            pool.submit<String> {
                salida.await()
                tx.execute { numerador.siguiente(empresa, 2026) }!!
            }
        }
        salida.countDown()
        val numeros = tareas.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(setOf("RO-2026-000001", "RO-2026-000002", "RO-2026-000003", "RO-2026-000004"), numeros.toSet())
        assertEquals("RO-2026-000005", tx.execute { numerador.siguiente(" ${empresa.uppercase()} ", 2026) })
        assertEquals("RO-2026-000001", tx.execute { numerador.siguiente(otra, 2026) })
        assertEquals("RO-2027-000001", tx.execute { numerador.siguiente(empresa, 2027) })
    }

    @Test
    fun `el contador nunca queda atras de un numero ya usado`() {
        val ana = cuenta("eva", "Empresa R $sufijo")
        guardar(ana, "RO-2026-000041", "33333333")
        assertEquals("RO-2026-000042", tx.execute { numerador.siguiente(ana.empresa!!, 2026) })
    }

    @Test
    fun `el numero es unico dentro de la empresa pero se repite entre empresas`() {
        val ana = cuenta("rosa", "Empresa U $sufijo")
        val luis = cuenta("ivan", "Empresa V $sufijo")
        guardar(ana, "RO-2026-000001", "44444444")
        guardar(luis, "RO-2026-000001", "44444444")
        assertFailsWith<DataIntegrityViolationException> { guardar(ana, "RO-2026-000001", "55555555") }
    }

    @Test
    fun `el esquema ya quedo ajustado y repetirlo no cambia nada`() {
        assertTrue(esquema.numeroUnicoPorEmpresa().isEmpty())
        assertFalse(esquema.fechaNacimientoOpcional())
        val columnas = jdbc.queryForList(
            """
            SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'registro_operaciones_v2' AND non_unique = 0
            GROUP BY index_name
            """, String::class.java
        ).map { it.lowercase() }
        assertTrue("empresa,numero_registro" in columnas, "Indices unicos: $columnas")
        assertFalse("numero_registro" in columnas, "Indices unicos: $columnas")
    }

    @Test
    fun `el historial guarda cada cambio y se lee en orden`() {
        val ana = cuenta("lia", "Empresa H $sufijo")
        val r = guardar(ana, "RO-2026-000001", "66666666")
        val id = requireNotNull(r.id)
        historial.save(EventoOperacionEntity(registroId = id, tipo = "CREACION", titulo = "Creación del registro", usuario = ana.usuario,
            fecha = LocalDateTime.of(2026, 11, 5, 10, 0)))
        historial.save(EventoOperacionEntity(registroId = id, tipo = "EDICION", titulo = "Edición del registro", motivo = "Prueba",
            usuario = ana.usuario, fecha = LocalDateTime.of(2026, 11, 5, 11, 0),
            cambios = listOf(CambioCampo("operacion.monto", "Monto", "100.00", "200.00"), CambioCampo("operacion.lugar", "Lugar", null, "Lima"))))
        val eventos = historial.findAllByRegistroIdOrderByFechaDescIdDesc(id)
        assertEquals(listOf("EDICION", "CREACION"), eventos.map { it.tipo })
        assertEquals(listOf("operacion.monto", "operacion.lugar"), eventos.first().cambios.map { it.campo })
        assertNull(eventos.first().cambios[1].antes)
        assertTrue(historial.existsByRegistroIdAndTipo(id, "CREACION"))
        // el beneficiario sin fecha de nacimiento se guardo
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM beneficiarios_operacion_v2 WHERE registro_id = ? AND fecha_nacimiento IS NULL", Int::class.javaObjectType, id))
    }
}
