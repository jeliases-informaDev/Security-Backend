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
    // si la entidad ya tiene un registro PEP en Listas Negativas, se marca PEP;
    // si no, se distingue Nacional/Extranjero segun el pais registrado.
    private fun evaluarClienteSensible(entidad: Entidad): Pair<String, Int> {
        val esPep = entidad.manchas.any { it.tipoLista.esPep }
        if (esPep) return "PEP" to 5

        val esNacional = entidad.pais?.nombre?.equals("Peru", ignoreCase = true) ?: true
        return if (esNacional) "Nacional" to 1 else "Extranjero" to 3
    }

    private fun evaluarVolumenTransaccional(monto: BigDecimal): Int = when {
        monto <= BigDecimal(8000) -> 1
        monto <= BigDecimal(20000) -> 3
        else -> 5
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
        private const val PESO_OCUPACION = 30
        private const val PESO_CLIENTE_SENSIBLE = 25
        private const val PESO_DEPARTAMENTO = 25
        private const val PESO_VOLUMEN = 20
    }
}
