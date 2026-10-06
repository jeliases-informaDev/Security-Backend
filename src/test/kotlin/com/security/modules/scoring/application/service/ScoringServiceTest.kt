package com.security.modules.scoring.application.service

import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.domain.HistorialMancha
import com.security.modules.listas.domain.TipoLista
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.scoring.domain.ScoringDepartamento
import com.security.modules.scoring.domain.ScoringOcupacion
import com.security.modules.scoring.domain.ScoringRiesgo
import com.security.modules.scoring.dto.EvaluarScoringRequest
import com.security.modules.scoring.dto.ScoringResultResponse
import com.security.modules.scoring.infrastructure.ScoringDepartamentoRepository
import com.security.modules.scoring.infrastructure.ScoringOcupacionRepository
import com.security.modules.scoring.infrastructure.ScoringRiesgoRepository
import com.security.shared.datos.entities.Pais
import com.security.shared.datos.entities.TipoDocumento
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

/**
 * Fijan los puntajes y pesos del libro vigente (Scoring y Factores_Riesgo LAFT, nov-2020):
 * Ocupacion 20 %, Cliente Sensible 40 %, Departamento 12 %, Volumen 28 %;
 * cliente sensible Nacional 1 / Extranjero 2 / PEP 4; volumen <=8 000 -> 1, <=20 000 -> 2, resto 3.
 * Si cambian los pesos o puntajes a proposito, hay que actualizar estas cifras junto con la fuente.
 */
class ScoringServiceTest {

    private val entidadRepository = mockk<EntidadRepository>()
    private val ocupacionRepository = mockk<ScoringOcupacionRepository>()
    private val departamentoRepository = mockk<ScoringDepartamentoRepository>()
    private val scoringRiesgoRepository = mockk<ScoringRiesgoRepository>()
    private val usuarioRepository = mockk<UsuarioRepository>()
    private lateinit var service: ScoringService

    private val peru = Pais(id = 1, nombre = "Peru", continente = "America")

    @BeforeEach
    fun setUp() {
        service = ScoringService(entidadRepository, ocupacionRepository, departamentoRepository, scoringRiesgoRepository, usuarioRepository)
        every { usuarioRepository.findByUsuario("analista") } returns Usuario(id = 1, usuario = "analista", clave = "x", correo = "a@b.c")
        every { scoringRiesgoRepository.save(any<ScoringRiesgo>()) } answers { firstArg<ScoringRiesgo>().also { it.id = 99 } }
    }

    private fun entidad(pais: Pais? = peru, esPep: Boolean = false): Entidad {
        val e = Entidad(id = 1, tipoDocumento = TipoDocumento(id = 1, nombre = "DNI"), pais = pais, documento = "12345678", tipoEntidad = Entidad.TIPO_NATURAL)
        if (esPep) {
            val lista = TipoLista(id = 10, nombre = "Personas Expuestas Publicamente", esPep = true)
            e.manchas.add(HistorialMancha(entidad = e, tipoLista = lista))
        }
        return e
    }

    private fun evaluar(entidad: Entidad, ocupacion: Int, departamento: Int, volumen: String): ScoringResultResponse {
        every { entidadRepository.buscarPorIdConDetalle(1) } returns entidad
        every { ocupacionRepository.findById(1) } returns Optional.of(ScoringOcupacion(id = 1, nombre = "OCUPACION", puntaje = ocupacion))
        every { departamentoRepository.findById(1) } returns Optional.of(ScoringDepartamento(id = 1, pais = peru, nombre = "DEPTO", puntaje = departamento))
        return service.evaluar("analista", EvaluarScoringRequest(entidadId = 1, idOcupacion = 1, idDepartamento = 1, volumenTransaccional = BigDecimal(volumen)))
    }

    private fun puntajeDe(r: ScoringResultResponse, nombre: String) = r.factores.first { it.nombre.startsWith(nombre) }.puntaje

    @Test
    fun `cliente nacional con volumen de 15 000 da 1,80 y Riesgo Muy Bajo`() {
        // contador 3 * 0,20 + nacional 1 * 0,40 + Lima 2 * 0,12 + volumen 2 * 0,28 = 1,80
        val r = evaluar(entidad(), ocupacion = 3, departamento = 2, volumen = "15000")
        assertEquals(BigDecimal("1.80"), r.puntajeTotal)
        assertEquals("Riesgo Muy Bajo", r.categoria)
    }

    @Test
    fun `PEP con volumen alto en Piura da 3,44 y Riesgo Alto`() {
        // abogado 2 * 0,20 + PEP 4 * 0,40 + Piura 5 * 0,12 + volumen 3 * 0,28 = 3,44
        val r = evaluar(entidad(esPep = true), ocupacion = 2, departamento = 5, volumen = "30000")
        assertEquals(BigDecimal("3.44"), r.puntajeTotal)
        assertEquals("Riesgo Alto", r.categoria)
        assertEquals(4, puntajeDe(r, "Cliente Sensible"))
    }

    @Test
    fun `cliente extranjero puntua 2 como cliente sensible`() {
        val r = evaluar(entidad(pais = Pais(id = 2, nombre = "Colombia")), ocupacion = 1, departamento = 2, volumen = "5000")
        assertEquals(2, puntajeDe(r, "Cliente Sensible"))
        // 1 * 0,20 + 2 * 0,40 + 2 * 0,12 + 1 * 0,28 = 1,52
        assertEquals(BigDecimal("1.52"), r.puntajeTotal)
    }

    @Test
    fun `Peru con tilde tambien es nacional`() {
        val r = evaluar(entidad(pais = Pais(id = 1, nombre = "Perú")), ocupacion = 1, departamento = 1, volumen = "1000")
        assertEquals(1, puntajeDe(r, "Cliente Sensible"))
    }

    @Test
    fun `volumen transaccional se puntua por los rangos del libro`() {
        listOf("0" to 1, "8000" to 1, "8000.01" to 2, "20000" to 2, "20000.01" to 3, "999999" to 3).forEach { (monto, esperado) ->
            val r = evaluar(entidad(), ocupacion = 1, departamento = 1, volumen = monto)
            assertEquals(esperado, puntajeDe(r, "Volumen"), "volumen $monto")
        }
    }

    @Test
    fun `los pesos suman 100`() {
        val r = evaluar(entidad(), ocupacion = 1, departamento = 1, volumen = "1")
        assertEquals(100, r.factores.sumOf { it.peso })
    }
}
