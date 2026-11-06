package com.security.modules.operaciones

import com.security.modules.operaciones.FixturesOperaciones.catalogos
import com.security.modules.operaciones.FixturesOperaciones.registro
import com.security.modules.operaciones.FixturesOperaciones.registroViejo
import com.security.modules.operaciones.application.service.AutorEvento
import com.security.modules.operaciones.application.service.CodigosOperaciones
import com.security.modules.operaciones.application.service.FotoOperacion
import com.security.modules.operaciones.application.service.HistorialOperacionService
import com.security.modules.operaciones.application.service.OperacionesException
import com.security.modules.operaciones.domain.BeneficiarioEntity
import com.security.modules.operaciones.domain.CambioCampo
import com.security.modules.operaciones.domain.EventoOperacionEntity
import com.security.modules.operaciones.infraestructure.HistorialOperacionRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.*

class FotoHistorialOperacionTest {

    @Test
    fun `los cambios llegan legibles, con su etiqueta, y un beneficiario agregado no tiene antes`() {
        val r = registro()
        val antes = FotoOperacion.campos(r, catalogos())
        r.operacion.monto = BigDecimal("128500.00")
        r.personaFisica.telefono = "900000002"
        r.operacion.modalidadPago = "EFECTIVO"
        r.beneficiarios.add(BeneficiarioEntity(nombre = "Persona Ficticia Dos", tipoDocumento = "DNI", numeroDocumento = "00000004"))
        val cambios = FotoOperacion.diferencias(antes, FotoOperacion.campos(r, catalogos()))
            .associateBy { it.campo }

        assertEquals("Monto", cambios.getValue("operacion.monto").etiqueta)
        assertEquals("125,800.00", cambios.getValue("operacion.monto").antes)
        assertEquals("128,500.00", cambios.getValue("operacion.monto").despues)
        assertEquals("Teléfono", cambios.getValue("personaFisica.telefono").etiqueta)
        assertEquals("Transferencia bancaria", cambios.getValue("operacion.modalidadPago").antes)
        assertEquals("Efectivo", cambios.getValue("operacion.modalidadPago").despues)
        val nuevo = cambios.getValue("beneficiarios[1].nombre")
        assertEquals("Beneficiario 2 · Nombre", nuevo.etiqueta)
        assertNull(nuevo.antes)
        assertEquals(setOf("operacion.monto", "personaFisica.telefono", "operacion.modalidadPago",
            "beneficiarios[1].nombre", "beneficiarios[1].tipoDocumento", "beneficiarios[1].numeroDocumento"), cambios.keys)
    }

    @Test
    fun `la huella cambia si cambia el contenido y es igual para el mismo contenido`() {
        val a = registro()
        val b = registro()
        val huella = FotoOperacion.huella(a, FotoOperacion.campos(a, catalogos()))
        assertEquals(64, huella.length)
        assertEquals(huella, FotoOperacion.huella(b, FotoOperacion.campos(b, catalogos())))
        b.personaFisica.telefono = "900000009"
        assertNotEquals(huella, FotoOperacion.huella(b, FotoOperacion.campos(b, catalogos())))
    }

    private val repositorio = mockk<HistorialOperacionRepository>()
    private val historial = HistorialOperacionService(repositorio)

    private fun evento(id: Long, tipo: String, minuto: Int) = EventoOperacionEntity(
        id = id, registroId = 42, tipo = tipo, titulo = tipo, usuario = "ana", nombreUsuario = "Ana Prueba",
        fecha = LocalDateTime.of(2026, 11, 5, 11, minuto)
    )

    @Test
    fun `un registro sin evento de creacion muestra el evento de registro anterior al historial`() {
        val viejo = registroViejo()
        every { repositorio.findAllByRegistroIdOrderByFechaDescIdDesc(43) } returns listOf(evento(1, EventoOperacionEntity.EDICION, 8))
        val eventos = historial.historial(viejo, null).eventos
        assertEquals(listOf("EDICION", "ANTERIOR"), eventos.map { it.tipo })
        val anterior = eventos.last()
        assertNull(anterior.id)
        assertTrue(anterior.sistema)
        assertEquals("Registro anterior al historial", anterior.titulo)
        assertEquals(viejo.fechaCreacion, anterior.fecha)
        assertEquals(LocalDate.of(2031, 10, 12), historial.historial(viejo, "TODO").conservarHasta)
    }

    @Test
    fun `los filtros del historial y el de analisis vacio`() {
        val r = registro()
        every { repositorio.findAllByRegistroIdOrderByFechaDescIdDesc(42) } returns listOf(
            evento(3, EventoOperacionEntity.DESCARGA, 12), evento(2, EventoOperacionEntity.RESPONSABLE, 10),
            evento(1, EventoOperacionEntity.CREACION, 1)
        )
        assertEquals(3, historial.historial(r, "todo").eventos.size)
        assertEquals(listOf("DESCARGA"), historial.historial(r, "DESCARGA").eventos.map { it.tipo })
        assertEquals(listOf("RESPONSABLE"), historial.historial(r, "RESPONSABLE").eventos.map { it.tipo })
        assertTrue(historial.historial(r, "ANALISIS").eventos.isEmpty())
        val e = assertFailsWith<OperacionesException> { historial.historial(r, "BORRADO") }
        assertEquals(CodigosOperaciones.FILTRO_INVALIDO, e.code)
    }

    @Test
    fun `una edicion guarda motivo, autor y cada cambio, y el detalle ya viene armado`() {
        val guardado = slot<EventoOperacionEntity>()
        every { repositorio.save(capture(guardado)) } answers { firstArg() }
        historial.edicion(42, AutorEvento(7, "ana", "Ana Prueba"), LocalDateTime.of(2026, 11, 5, 11, 8), "Contrato firmado",
            listOf(CambioCampo("operacion.monto", "Monto", "125,800.00", "128,500.00"), CambioCampo("operacion.lugar", "Lugar", null, "Lima")))
        val e = guardado.captured
        assertEquals("EDICION", e.tipo)
        assertEquals("Contrato firmado", e.motivo)
        assertEquals("ana", e.usuario)
        assertEquals(7, e.idUsuario)
        assertEquals("Monto: 125,800.00 → 128,500.00 · Lugar: — → Lima", e.detalle)
        assertEquals(2, e.cambios.size)
    }
}
