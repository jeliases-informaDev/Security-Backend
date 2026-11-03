package com.security.modules.creditos

import com.security.modules.creditos.application.service.CreditoService
import com.security.modules.creditos.domain.AccionCredito
import com.security.modules.creditos.domain.LicenciaEmpresa
import com.security.modules.creditos.domain.exceptions.CupoUsuariosExcedidoException
import com.security.modules.creditos.domain.exceptions.LicenciaVencidaException
import com.security.modules.creditos.domain.exceptions.SaldoInsuficienteException
import com.security.modules.creditos.infrastructure.repository.AvisoLicenciaRepository
import com.security.modules.creditos.infrastructure.repository.LicenciaEmpresaRepository
import com.security.modules.creditos.infrastructure.repository.MovimientoCreditoRepository
import com.security.modules.creditos.infrastructure.repository.TarifaCreditoRepository
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

class CreditoServiceTest {

    private val licenciaRepo = mockk<LicenciaEmpresaRepository>()
    private val movRepo = mockk<MovimientoCreditoRepository>()
    private val tarifaRepo = mockk<TarifaCreditoRepository>()
    private val avisoRepo = mockk<AvisoLicenciaRepository>()
    private val empresaRepo = mockk<EmpresaRepository>()
    private val usuarioRepo = mockk<UsuarioRepository>()
    private val service = CreditoService(
        licenciaRepo,
        movRepo,
        tarifaRepo,
        avisoRepo,
        empresaRepo,
        usuarioRepo,
        mockk(relaxed = true)
    )

    private fun usuario(nombre: String = "juan", empresa: String = "MiEmpresa") =
        Usuario(id = 1, usuario = nombre, clave = "x", correo = "$nombre@test.com", empresa = empresa)

    private fun empresa(id: Int = 10, nombre: String = "MiEmpresa") =
        Empresa(id = id, nombre = nombre, uuid = "uuid-123")

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `consumir descuenta de la bolsa cuando hay saldo suficiente`() {
        val user = usuario()
        val emp = empresa()
        val licencia = LicenciaEmpresa(
            id = 1,
            empresaId = emp.id!!,
            creditosTotales = 100,
            creditosConsumidos = 10,
            toleranciaPorcentaje = 10,
            fechaCreacion = LocalDateTime.now(),
            fechaActualizacion = LocalDateTime.now()
        )

        every { empresaRepo.findByNombreIgnoreCase(emp.nombre) } returns emp
        every { tarifaRepo.findById(AccionCredito.LISTA_BUSQUEDA) } returns Optional.empty() // costo inicial: 1
        every { licenciaRepo.findByEmpresaIdForUpdate(emp.id!!) } returns Optional.of(licencia)
        every { movRepo.save(any()) } answers { firstArg() }
        every { licenciaRepo.save(any()) } answers { firstArg() }

        service.consumir(user, AccionCredito.LISTA_BUSQUEDA, "ref-test")

        assertEquals(11, licencia.creditosConsumidos)
        assertFalse(licencia.enExceso())
        verify(exactly = 1) { movRepo.save(match { it.cantidad == 1L && !it.exceso }) }
    }

    @Test
    fun `consumir permite exceso si esta dentro de la tolerancia`() {
        val user = usuario()
        val emp = empresa()
        // Bolsa 100, consumidos 99. Tolerancia 10% = 10 créditos extra (tope 110)
        val licencia = LicenciaEmpresa(
            id = 1,
            empresaId = emp.id!!,
            creditosTotales = 100,
            creditosConsumidos = 99,
            toleranciaPorcentaje = 10,
            fechaCreacion = LocalDateTime.now(),
            fechaActualizacion = LocalDateTime.now()
        )

        every { empresaRepo.findByNombreIgnoreCase(emp.nombre) } returns emp
        every { tarifaRepo.findById(AccionCredito.SCORING_EVALUAR) } returns Optional.empty() // costo inicial: 2
        every { licenciaRepo.findByEmpresaIdForUpdate(emp.id!!) } returns Optional.of(licencia)
        every { movRepo.save(any()) } answers { firstArg() }
        every { licenciaRepo.save(any()) } answers { firstArg() }
        every { avisoRepo.save(any()) } answers { firstArg() }
        every { usuarioRepo.findAllActivosByRoles(any()) } returns emptyList()

        service.consumir(user, AccionCredito.SCORING_EVALUAR, "scoring-ref")

        assertEquals(101, licencia.creditosConsumidos)
        assertTrue(licencia.enExceso())
        verify(exactly = 1) { movRepo.save(match { it.cantidad == 2L && it.exceso }) }
    }

    @Test
    fun `consumir lanza SaldoInsuficienteException cuando supera la tolerancia`() {
        val user = usuario()
        val emp = empresa()
        // Bolsa 100, consumidos 110 (tope máximo con 10% de tolerancia). No admite más.
        val licencia = LicenciaEmpresa(
            id = 1,
            empresaId = emp.id!!,
            creditosTotales = 100,
            creditosConsumidos = 110,
            toleranciaPorcentaje = 10,
            fechaCreacion = LocalDateTime.now(),
            fechaActualizacion = LocalDateTime.now()
        )

        every { empresaRepo.findByNombreIgnoreCase(emp.nombre) } returns emp
        every { tarifaRepo.findById(AccionCredito.LISTA_BUSQUEDA) } returns Optional.empty()
        every { licenciaRepo.findByEmpresaIdForUpdate(emp.id!!) } returns Optional.of(licencia)

        assertFailsWith<SaldoInsuficienteException> {
            service.consumir(user, AccionCredito.LISTA_BUSQUEDA)
        }
    }

    @Test
    fun `consumir rechaza si la licencia esta vencida`() {
        val user = usuario()
        val emp = empresa()
        val licencia = LicenciaEmpresa(
            id = 1,
            empresaId = emp.id!!,
            creditosTotales = 100,
            creditosConsumidos = 0,
            fechaVencimiento = LocalDate.now().minusDays(15), // Ya suspendida
            fechaCreacion = LocalDateTime.now(),
            fechaActualizacion = LocalDateTime.now()
        )

        every { empresaRepo.findByNombreIgnoreCase(emp.nombre) } returns emp
        every { tarifaRepo.findById(AccionCredito.LISTA_BUSQUEDA) } returns Optional.empty()
        every { licenciaRepo.findByEmpresaIdForUpdate(emp.id!!) } returns Optional.of(licencia)

        assertFailsWith<LicenciaVencidaException> {
            service.consumir(user, AccionCredito.LISTA_BUSQUEDA)
        }
    }

    @Test
    fun `validarCupoUsuarios lanza excepcion si se supera el maximo`() {
        val emp = empresa()
        val licencia = LicenciaEmpresa(
            empresaId = emp.id!!,
            cupoUsuarios = 2
        )

        every { licenciaRepo.findByEmpresaId(emp.id!!) } returns Optional.of(licencia)
        every { empresaRepo.findById(emp.id!!) } returns Optional.of(emp)
        every { usuarioRepo.findAllByEmpresaAndActivoTrueOrderByNombresAscApePatAsc(emp.nombre) } returns listOf(
            usuario("u1"),
            usuario("u2")
        )

        assertFailsWith<CupoUsuariosExcedidoException> {
            service.validarCupoUsuarios(emp.id!!)
        }
    }
}
