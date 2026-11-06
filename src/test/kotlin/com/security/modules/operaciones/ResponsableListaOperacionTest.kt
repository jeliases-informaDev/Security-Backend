package com.security.modules.operaciones

import com.security.modules.operaciones.FixturesOperaciones.catalogos
import com.security.modules.operaciones.FixturesOperaciones.registro
import com.security.modules.operaciones.FixturesOperaciones.usuario
import com.security.modules.operaciones.application.service.*
import com.security.modules.operaciones.controller.infrastructure.OperacionController
import com.security.modules.operaciones.dto.FilaOperacion
import com.security.modules.operaciones.dto.FiltrosOperaciones
import com.security.modules.operaciones.infraestructure.ConteosOperaciones
import com.security.modules.operaciones.infraestructure.ListaOperacionesConsulta
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

class ResponsableListaOperacionTest {
    private val operaciones = mockk<OperacionRepository>()
    private val usuarios = mockk<UsuarioRepository>()
    private val acceso = mockk<AccesoEmpresaService>()
    private val historial = mockk<HistorialOperacionService>(relaxed = true)
    private val autor = AutorEvento(1, "ana", "Ana")
    private val responsables = ResponsableOperacionService(operaciones, usuarios, acceso, historial)

    private fun preparar(): com.security.modules.operaciones.domain.RegistroOperacionesEntity {
        val r = registro()
        r.idUsuarioResponsable = null
        every { operaciones.findById(42) } returns Optional.of(r)
        every { operaciones.flush() } just Runs
        every { acceso.empresaDelRegistro(r) } returns "Empresa Gamma"
        return r
    }

    @Test
    fun `solo un usuario activo de la empresa del registro puede ser responsable`() {
        preparar()
        listOf(usuario(2, "luis", "Empresa Delta"), usuario(2, "luis", "Empresa Gamma", activo = false), null).forEach { candidato ->
            every { usuarios.findById(2) } returns Optional.ofNullable(candidato)
            val e = assertFailsWith<OperacionesException> { responsables.cambiar(42, 2, autor) }
            assertEquals(CodigosOperaciones.RESPONSABLE_INVALIDO, e.code)
        }
        verify(exactly = 0) { historial.responsable(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cambiar responsable lo guarda y lo anota con antes y despues, y repetirlo no cambia nada`() {
        val r = preparar()
        every { usuarios.findById(2) } returns Optional.of(usuario(2, "luis", " empresa gamma "))
        val resultado = responsables.cambiar(42, 2, autor)
        assertEquals(2, resultado.responsable.id)
        assertEquals(2, r.idUsuarioResponsable)
        verify { historial.responsable(42, autor, any(), null, "Nombre2 Prueba") }
        val e = assertFailsWith<OperacionesException> { responsables.cambiar(42, 2, autor) }
        assertEquals(CodigosOperaciones.SIN_CAMBIOS, e.code)
    }

    @Test
    fun `los posibles responsables son los activos de la empresa del registro`() {
        preparar()
        every { usuarios.findAllByEmpresaOrderByNombresAscApePatAscIdAsc("Empresa Gamma") } returns
            listOf(usuario(2, "luis", "Empresa Gamma"), usuario(3, "eva", "Empresa Gamma", activo = false))
        assertEquals(listOf(2), responsables.posibles(42).map { it.id })
        verify(exactly = 0) { usuarios.findAllByEmpresaOrderByNombresAscApePatAscIdAsc("Empresa Delta") }
    }

    // ---------- Lista e indicadores ----------

    private val consulta = mockk<ListaOperacionesConsulta>()
    private val catalogosRegistro = mockk<CatalogosRegistro> { every { cargar() } answers { catalogos() } }
    private val lista = ListaOperacionesService(consulta, catalogosRegistro)
    private val controlador = OperacionController(mockk(), acceso, mockk(), lista, responsables)

    private fun fila(id: Long, moneda: String, tipo: String) = FilaOperacion(
        id, "RO-2026-00000$id", "Prueba Ejemplo, Ana", null, "Lima", LocalDate.of(2026, 11, 5), LocalDateTime.of(2026, 11, 5, 10, 0),
        LocalDate.of(2026, 11, 5), tipo, "EFECTIVO", BigDecimal("100.00"), moneda, null, null
    )

    @Test
    fun `la lista y los indicadores usan la empresa que decide el acceso, no la que pide una cuenta comun`() {
        every { acceso.empresaPermitida("Empresa Delta") } returns "Empresa Gamma"
        every { consulta.listar("Empresa Gamma", any(), 0, 10) } returns (listOf(fila(1, "PEN — Soles", "Deposito"), fila(2, "USD", "VENTA_LOTE")) to 2L)
        every { consulta.conteos("Empresa Gamma", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)) } returns ConteosOperaciones(25, 2, 10)

        val r = controlador.listarResumen("Empresa Delta", 0, 10)
        assertEquals(2L, r.totalElements)
        assertEquals(1, r.totalPages)
        assertEquals("PEN", r.content[0].monedaCodigo)
        assertEquals("Deposito", r.content[0].tipoOperacionNombre)
        assertEquals("Venta de lote de terreno", r.content[1].tipoOperacionNombre)
        assertEquals("Efectivo", r.content[1].modalidadPagoNombre)
        assertNull(r.content[0].responsable)
        assertTrue(r.content.all { it.estado == "PENDIENTE" })

        val i = controlador.indicadores("Empresa Delta", "2026-10")
        assertEquals(10, i.registrosDelPeriodo)
        assertEquals(25, i.pendientesDeEnvio)
        assertEquals(2, i.sinResponsable)
        verify(exactly = 0) { consulta.listar("Empresa Delta", any(), any(), any()); consulta.conteos("Empresa Delta", any(), any()) }
    }

    @Test
    fun `los filtros se traducen y un codigo encuentra tambien el nombre escrito en registros viejos`() {
        val filtros = slot<FiltrosOperaciones>()
        every { consulta.listar("Empresa Gamma", capture(filtros), 0, 10) } returns (emptyList<FilaOperacion>() to 0L)
        lista.listar("Empresa Gamma", ParametrosLista(q = "  0000 ", tipoOperacion = "venta_lote", moneda = "usd", sinResponsable = true, estado = "pendiente"), 0, 10)
        val f = filtros.captured
        assertEquals("0000", f.q)
        assertEquals(listOf("VENTA_LOTE", "Venta de lote de terreno"), f.tipoOperacion)
        assertEquals("USD", f.moneda)
        assertEquals(2L, f.monedaId)
        assertTrue(f.sinResponsable)
    }

    @Test
    fun `filtros, periodo y paginas invalidos responden con su codigo`() {
        fun codigo(bloque: () -> Unit) = assertFailsWith<OperacionesException> { bloque() }.code
        assertEquals(CodigosOperaciones.FILTRO_PRONTO, codigo { lista.listar("E", ParametrosLista(estado = "ENVIADO"), 0, 10) })
        assertEquals(CodigosOperaciones.FILTRO_INVALIDO, codigo { lista.listar("E", ParametrosLista(estado = "BORRADO"), 0, 10) })
        assertEquals(CodigosOperaciones.FILTRO_INVALIDO, codigo { lista.listar("E", ParametrosLista(tipoOperacion = "X"), 0, 10) })
        assertEquals(CodigosOperaciones.FILTRO_INVALIDO, codigo { lista.listar("E", ParametrosLista(moneda = "JPY"), 0, 10) })
        assertEquals(CodigosOperaciones.FILTRO_INVALIDO, codigo {
            lista.listar("E", ParametrosLista(desde = LocalDate.of(2026, 11, 2), hasta = LocalDate.of(2026, 11, 1)), 0, 10)
        })
        assertEquals(CodigosOperaciones.PAGINA_INVALIDA, codigo { lista.listar("E", ParametrosLista(), -1, 10) })
        assertEquals(CodigosOperaciones.PAGINA_INVALIDA, codigo { lista.listar("E", ParametrosLista(), 0, 101) })
        assertEquals(CodigosOperaciones.PERIODO_INVALIDO, codigo { lista.indicadores("E", "2026-13") })
        verify { consulta wasNot Called }
    }
}
