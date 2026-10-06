package com.security.modules.scoring.application.service

import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.scoring.domain.ScoringDepartamento
import com.security.modules.scoring.domain.ScoringOcupacion
import com.security.modules.scoring.domain.ScoringRiesgo
import com.security.modules.scoring.dto.*
import com.security.modules.scoring.infrastructure.ScoringDepartamentoRepository
import com.security.modules.scoring.infrastructure.ScoringOcupacionRepository
import com.security.modules.scoring.infrastructure.ScoringRiesgoRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer

@Service
class ScoringService(
    private val entidadRepository: EntidadRepository,
    private val ocupacionRepository: ScoringOcupacionRepository,
    private val departamentoRepository: ScoringDepartamentoRepository,
    private val scoringRiesgoRepository: ScoringRiesgoRepository,
    private val usuarioRepository: UsuarioRepository
) {

    @Transactional(readOnly = true)
    fun obtenerCatalogos(): CatalogosScoringResponse {
        return CatalogosScoringResponse(
            ocupaciones = ocupacionRepository.findAll().map { CatalogoItemResponse(it.id!!, it.nombre) },
            departamentos = departamentoRepository.findAll().map { CatalogoItemResponse(it.id!!, it.nombre) }
        )
    }

    @Transactional
    fun evaluar(username: String, request: EvaluarScoringRequest): ScoringResultResponse {

        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException("Usuario no encontrado")

        val entidad = entidadRepository.buscarPorIdConDetalle(request.entidadId)
            ?: throw ResourceNotFoundException("No se encontró la entidad a evaluar")

        val ocupacion = ocupacionRepository.findById(request.idOcupacion)
            .orElseThrow { ResourceNotFoundException("Ocupación no encontrada") }

        val departamento = departamentoRepository.findById(request.idDepartamento)
            .orElseThrow { ResourceNotFoundException("Departamento no encontrado") }

        val (clienteSensibleTipo, clienteSensiblePuntaje) = evaluarClienteSensible(entidad)
        val volumenPuntaje = evaluarVolumenTransaccional(request.volumenTransaccional)

        val factores = listOf(
            FactorEvaluado("Ocupación / Profesión", ocupacion.nombre, ocupacion.puntaje, PESO_OCUPACION),
            FactorEvaluado("Cliente Sensible", clienteSensibleTipo, clienteSensiblePuntaje, PESO_CLIENTE_SENSIBLE),
            FactorEvaluado("Zona Geográfica (Departamento)", departamento.nombre, departamento.puntaje, PESO_DEPARTAMENTO),
            FactorEvaluado("Volumen Transaccional Estimado", request.volumenTransaccional.toPlainString(), volumenPuntaje, PESO_VOLUMEN)
        )

        val puntajeTotal = factores
            .sumOf { it.puntajePonderado() }
            .setScale(2, RoundingMode.HALF_UP)

        val categoria = categorizar(puntajeTotal)

        val sustento = factores.joinToString("; ") { "${it.nombre}: ${it.valor} (${it.puntaje} x ${it.peso}%)" }

        val scoringGuardado = scoringRiesgoRepository.save(
            ScoringRiesgo(
                usuario = usuario,
                entidad = entidad,
                ocupacion = ocupacion,
                departamento = departamento,
                clienteSensibleTipo = clienteSensibleTipo,
                volumenTransaccional = request.volumenTransaccional,
                puntaje = puntajeTotal,
                sustento = sustento,
                categoria = categoria
            )
        )

        return ScoringResultResponse(
            id = scoringGuardado.id!!,
            entidadId = entidad.id!!,
            nombreCompleto = nombreCompletoDe(entidad),
            documento = entidad.documento,
            factores = factores.map {
                FactorScoringResponse(it.nombre, it.valor, it.puntaje, it.peso, it.puntajePonderado())
            },
            puntajeTotal = puntajeTotal,
            categoria = categoria,
            fechaCreacion = scoringGuardado.fechaCreacion
        )
    }

    @Transactional(readOnly = true)
    fun obtenerHistorial(username: String, pageable: Pageable): Page<ScoringResultResponse> {

        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException("Usuario no encontrado")

        return scoringRiesgoRepository.buscarPorUsuario(usuario.id!!, pageable).map { sr ->
            ScoringResultResponse(
                id = sr.id!!,
                entidadId = sr.entidad.id!!,
                nombreCompleto = nombreCompletoDe(sr.entidad),
                documento = sr.entidad.documento,
                factores = listOf(
                    FactorScoringResponse("Ocupación / Profesión", sr.ocupacion.nombre, sr.ocupacion.puntaje, PESO_OCUPACION, BigDecimal.ZERO),
                    FactorScoringResponse("Cliente Sensible", sr.clienteSensibleTipo, 0, PESO_CLIENTE_SENSIBLE, BigDecimal.ZERO),
                    FactorScoringResponse("Zona Geográfica (Departamento)", sr.departamento.nombre, sr.departamento.puntaje, PESO_DEPARTAMENTO, BigDecimal.ZERO),
                    FactorScoringResponse("Volumen Transaccional Estimado", sr.volumenTransaccional.toPlainString(), 0, PESO_VOLUMEN, BigDecimal.ZERO)
                ),
                puntajeTotal = sr.puntaje,
                categoria = sr.categoria,
                fechaCreacion = sr.fechaCreacion
            )
        }
    }

    private fun nombreCompletoDe(entidad: Entidad): String = when {
        entidad.personaNatural != null -> listOfNotNull(
            entidad.personaNatural?.nombre,
            entidad.personaNatural?.segundoNombre,
            entidad.personaNatural?.apePat,
            entidad.personaNatural?.apeMat
        ).joinToString(" ")
        entidad.personaJuridica != null -> entidad.personaJuridica?.razonSocial ?: ""
        else -> ""
    }

    // Cliente Sensible se deriva automaticamente, no se pide al usuario:
    // si la entidad ya tiene un registro en una lista PEP de Listas Negativas, se marca PEP;
    // si no, se distingue Nacional/Extranjero segun el pais registrado.
    // Puntajes del libro vigente (hoja FactorClientePN, variable Cliente_Sensible).
    // "No Residente" (3) no se puede derivar todavia: el sistema no registra la residencia.
    private fun evaluarClienteSensible(entidad: Entidad): Pair<String, Int> {
        val esPep = entidad.manchas.any { it.tipoLista.esPep }
        if (esPep) return "PEP" to PUNTAJE_CLIENTE_PEP

        val esNacional = entidad.pais?.nombre?.let { sinTildes(it).equals("Peru", ignoreCase = true) } ?: true
        return if (esNacional) "Nacional" to PUNTAJE_CLIENTE_NACIONAL else "Extranjero" to PUNTAJE_CLIENTE_EXTRANJERO
    }

    private fun sinTildes(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    // Rangos del libro vigente para persona natural (hoja FactorClienteRangos, Volumen_Transaccional_Esperado):
    // hasta 8 000 -> 1; de 8 000 a 20 000 -> 2; mas de 20 000 -> 3.
    private fun evaluarVolumenTransaccional(monto: BigDecimal): Int = when {
        monto <= TOPE_VOLUMEN_BAJO -> 1
        monto <= TOPE_VOLUMEN_MEDIO -> 2
        else -> 3
    }

    private fun categorizar(puntaje: BigDecimal): String = when {
        puntaje <= BigDecimal("1.80") -> "Riesgo Muy Bajo"
        puntaje <= BigDecimal("2.60") -> "Riesgo Bajo"
        puntaje <= BigDecimal("3.40") -> "Riesgo Medio"
        puntaje <= BigDecimal("4.20") -> "Riesgo Alto"
        else -> "Riesgo Muy Alto"
    }

    private data class FactorEvaluado(val nombre: String, val valor: String, val puntaje: Int, val peso: Int) {
        fun puntajePonderado(): BigDecimal =
            BigDecimal(puntaje).multiply(BigDecimal(peso)).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
    }

    companion object {
        // Pesos del libro vigente (Scoring y Factores_Riesgo LAFT / Puntaje paises, nov-2020):
        // Ocupacion 5, Cliente Sensible 10, Residencia 3 y Volumen 7 (suman 25 de 100).
        // Esta evaluacion solo cubre esas 4 variables, asi que se reescalan para sumar 100:
        // 5/25 = 20 %, 10/25 = 40 %, 3/25 = 12 % y 7/25 = 28 %.
        // El modelo completo (15 variables en persona natural) esta pendiente.
        private const val PESO_OCUPACION = 20
        private const val PESO_CLIENTE_SENSIBLE = 40
        private const val PESO_DEPARTAMENTO = 12
        private const val PESO_VOLUMEN = 28

        private const val PUNTAJE_CLIENTE_NACIONAL = 1
        private const val PUNTAJE_CLIENTE_EXTRANJERO = 2
        private const val PUNTAJE_CLIENTE_PEP = 4

        private val TOPE_VOLUMEN_BAJO = BigDecimal(8000)
        private val TOPE_VOLUMEN_MEDIO = BigDecimal(20000)
    }
}
