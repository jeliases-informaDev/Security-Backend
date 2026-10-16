package com.security.modules.empresas

import com.security.modules.empresas.application.service.EmpresaCatalogoInicializador
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationArguments
import kotlin.test.*

class EmpresaCatalogoInicializadorTest {
    private val empresas = mockk<EmpresaRepository>()
    private val usuarios = mockk<UsuarioRepository>()
    private val inicializador = EmpresaCatalogoInicializador(empresas, usuarios)

    private fun guardadas(): MutableList<Empresa> {
        val lista = mutableListOf<Empresa>()
        every { empresas.save(capture(lista)) } answers { firstArg() }
        return lista
    }

    @Test
    fun `agrega las empresas que ya tienen las cuentas sin repetir y sin tocar las que existen`() {
        every { usuarios.findEmpresasEnUso() } returns listOf(" Peru Luz ", "peru luz", "Otra", "")
        every { empresas.existsByNombreIgnoreCase("Peru Luz") } returns false
        every { empresas.existsByNombreIgnoreCase("Otra") } returns true
        every { empresas.count() } returns 2L
        val guardadas = guardadas()

        inicializador.sincronizar()

        assertEquals(listOf("Peru Luz"), guardadas.map { it.nombre })
        assertTrue(guardadas.single().activa)
    }

    @Test
    fun `si la lista queda vacia agrega la empresa de demostracion`() {
        every { usuarios.findEmpresasEnUso() } returns emptyList()
        every { empresas.count() } returns 0L
        every { empresas.existsByNombreIgnoreCase(EmpresaCatalogoInicializador.EMPRESA_DEMO) } returns false
        val guardadas = guardadas()

        inicializador.sincronizar()

        assertEquals(listOf("Empresa Demo"), guardadas.map { it.nombre })
    }

    @Test
    fun `no agrega la demostracion si ya hay empresas`() {
        every { usuarios.findEmpresasEnUso() } returns emptyList()
        every { empresas.count() } returns 3L

        inicializador.sincronizar()

        verify(exactly = 0) { empresas.save(any()) }
    }

    @Test
    fun `un fallo al preparar la lista no impide que la API arranque`() {
        every { usuarios.findEmpresasEnUso() } throws IllegalStateException("base no disponible")

        inicializador.run(mockk<ApplicationArguments>())

        verify(exactly = 0) { empresas.save(any()) }
    }

    @Test
    fun `si una empresa falla al guardarse las demas se guardan igual`() {
        every { usuarios.findEmpresasEnUso() } returns listOf("Mala", "Buena")
        every { empresas.existsByNombreIgnoreCase(any()) } returns false
        every { empresas.count() } returns 1L
        val guardadas = mutableListOf<Empresa>()
        every { empresas.save(any()) } answers {
            val empresa = firstArg<Empresa>()
            if (empresa.nombre == "Mala") throw IllegalStateException("no se pudo") else empresa.also { guardadas.add(it) }
        }

        inicializador.sincronizar()

        assertEquals(listOf("Buena"), guardadas.map { it.nombre })
    }
}
