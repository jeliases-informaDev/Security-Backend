package com.security.modules.empresas

import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.empresas.dto.EmpresaCreateRequest
import com.security.modules.empresas.dto.EmpresaUpdateRequest
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.repositories.CuentasPorEmpresa
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ConflictException
import com.security.shared.exceptions.ResourceNotFoundException
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

class EmpresaServiceTest {
    private val empresas = mockk<EmpresaRepository>()
    private val usuarios = mockk<UsuarioRepository>()
    private val service = EmpresaService(empresas, usuarios)

    private fun empresa(id: Int = 1, nombre: String = "Peru Luz S.A.C", ruc: String? = null, activa: Boolean = true) =
        Empresa(id = id, nombre = nombre, ruc = ruc, activa = activa,
            fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now())

    private fun cuentas(nombreEmpresa: String, cantidad: Long) = object : CuentasPorEmpresa {
        override val nombre = nombreEmpresa
        override val cuentas = cantidad
    }

    private fun sinCuentas() {
        every { usuarios.contarCuentasPorEmpresa() } returns emptyList()
    }

    @Test
    fun `listar une cada empresa con sus cuentas sin distinguir mayusculas`() {
        every { empresas.findAllByOrderByNombreAsc() } returns listOf(empresa(1, "Peru Luz S.A.C"), empresa(2, "Otra"))
        every { usuarios.contarCuentasPorEmpresa() } returns listOf(cuentas("peru luz s.a.c", 3))

        val lista = service.listar()

        assertEquals(listOf("Peru Luz S.A.C", "Otra"), lista.map { it.nombre })
        assertEquals(listOf(3L, 0L), lista.map { it.cuentas })
    }

    @Test
    fun `crear guarda la empresa activa con el nombre sin espacios sobrantes`() {
        every { empresas.existsByNombreIgnoreCase("Peru Luz S.A.C") } returns false
        val guardada = slot<Empresa>()
        every { empresas.save(capture(guardada)) } answers { guardada.captured.also { it.id = 10 } }

        val respuesta = service.crear(EmpresaCreateRequest(nombre = "  Peru Luz S.A.C  ", ruc = null))

        assertEquals("Peru Luz S.A.C", guardada.captured.nombre)
        assertTrue(guardada.captured.activa)
        assertNull(guardada.captured.ruc)
        assertEquals(10, respuesta.id)
        assertEquals(0L, respuesta.cuentas)
    }

    @Test
    fun `crear acepta un RUC de 11 digitos`() {
        every { empresas.existsByNombreIgnoreCase(any()) } returns false
        every { empresas.findByRuc("20123456789") } returns null
        val guardada = slot<Empresa>()
        every { empresas.save(capture(guardada)) } answers { guardada.captured.also { it.id = 11 } }

        val respuesta = service.crear(EmpresaCreateRequest(nombre = "Peru Luz", ruc = " 20123456789 "))

        assertEquals("20123456789", respuesta.ruc)
    }

    @Test
    fun `crear rechaza nombre repetido o RUC repetido`() {
        every { empresas.existsByNombreIgnoreCase("peru luz") } returns true
        assertFailsWith<ConflictException> { service.crear(EmpresaCreateRequest(nombre = "peru luz")) }

        every { empresas.existsByNombreIgnoreCase("Nueva") } returns false
        every { empresas.findByRuc("20123456789") } returns empresa(7, "Otra", "20123456789")
        assertFailsWith<ConflictException> { service.crear(EmpresaCreateRequest(nombre = "Nueva", ruc = "20123456789")) }

        verify(exactly = 0) { empresas.save(any()) }
    }

    @Test
    fun `crear rechaza nombre vacio o largo y RUC que no tiene 11 digitos`() {
        listOf(
            EmpresaCreateRequest(nombre = "   "),
            EmpresaCreateRequest(nombre = "x".repeat(256)),
            EmpresaCreateRequest(nombre = "Nueva", ruc = "123"),
            EmpresaCreateRequest(nombre = "Nueva", ruc = "2012345678A"),
            EmpresaCreateRequest(nombre = "Nueva", ruc = "201234567890")
        ).forEach {
            val error = assertFailsWith<ResponseStatusException> { service.crear(it) }
            assertEquals(400, error.statusCode.value())
        }
        verify(exactly = 0) { empresas.save(any()) }
    }

    @Test
    fun `actualizar cambia el RUC y permite dejarlo vacio`() {
        val existente = empresa(5, "Peru Luz", "20123456789")
        every { empresas.findById(5) } returns Optional.of(existente)
        every { empresas.findByRuc("20999999999") } returns null
        every { empresas.save(any()) } answers { firstArg() }
        sinCuentas()

        assertEquals("20999999999", service.actualizar(5, EmpresaUpdateRequest("20999999999")).ruc)
        assertNull(service.actualizar(5, EmpresaUpdateRequest("  ")).ruc)
    }

    @Test
    fun `actualizar rechaza el RUC de otra empresa y la empresa que no existe`() {
        every { empresas.findById(5) } returns Optional.of(empresa(5, "Peru Luz"))
        every { empresas.findByRuc("20123456789") } returns empresa(6, "Otra", "20123456789")
        assertFailsWith<ConflictException> { service.actualizar(5, EmpresaUpdateRequest("20123456789")) }

        every { empresas.findById(99) } returns Optional.empty()
        assertFailsWith<ResourceNotFoundException> { service.actualizar(99, EmpresaUpdateRequest(null)) }
        verify(exactly = 0) { empresas.save(any()) }
    }

    @Test
    fun `actualizar deja el mismo RUC en la misma empresa`() {
        val existente = empresa(5, "Peru Luz", "20123456789")
        every { empresas.findById(5) } returns Optional.of(existente)
        every { empresas.findByRuc("20123456789") } returns existente
        every { empresas.save(any()) } answers { firstArg() }
        sinCuentas()

        assertEquals("20123456789", service.actualizar(5, EmpresaUpdateRequest("20123456789")).ruc)
    }

    @Test
    fun `cambiarEstado activa y desactiva`() {
        val existente = empresa(5, "Peru Luz")
        every { empresas.findById(5) } returns Optional.of(existente)
        every { empresas.save(any()) } answers { firstArg() }
        every { usuarios.contarCuentasPorEmpresa() } returns listOf(cuentas("peru luz", 4))

        val inactiva = service.cambiarEstado(5, false)
        assertFalse(inactiva.activa)
        assertEquals(4L, inactiva.cuentas)
        assertTrue(service.cambiarEstado(5, true).activa)

        every { empresas.findById(99) } returns Optional.empty()
        assertFailsWith<ResourceNotFoundException> { service.cambiarEstado(99, true) }
    }

    @Test
    fun `resolverActiva devuelve el nombre exacto de la lista sin importar mayusculas ni espacios`() {
        every { empresas.findByNombreIgnoreCase("peru luz s.a.c") } returns empresa(1, "Peru Luz S.A.C")

        assertEquals("Peru Luz S.A.C", service.resolverActiva("  peru luz s.a.c "))
    }

    @Test
    fun `resolverActiva rechaza vacia inexistente e inactiva`() {
        every { empresas.findByNombreIgnoreCase("Fantasma") } returns null
        every { empresas.findByNombreIgnoreCase("Cerrada") } returns empresa(2, "Cerrada", activa = false)

        listOf(null, "", "   ", "Fantasma", "Cerrada").forEach {
            val error = assertFailsWith<ResponseStatusException> { service.resolverActiva(it) }
            assertEquals(400, error.statusCode.value())
        }
    }
}
