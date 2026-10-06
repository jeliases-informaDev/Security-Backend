package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringDepartamento
import com.security.modules.scoring.domain.ScoringOcupacion
import com.security.shared.datos.repositories.PaisRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Catalogos del scoring de riesgo (ocupaciones y departamentos con su puntaje 1-5). Sin ellos los combos
 * "Ocupacion / Profesion" y "Departamento" de la pantalla de Scoring salen vacios.
 *
 * Solo corre con app.seed.enabled=true (APP_SEED_ENABLED), igual que DevDataSeeder, y despues de este
 * (necesita el pais "Peru"). Es idempotente: solo agrega lo que falte y nunca cambia un puntaje existente.
 * Para una base que no usa el seed existe el mismo contenido en docs/catalogos-scoring.sql.
 */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "app.seed", name = ["enabled"], havingValue = "true")
class ScoringCatalogSeeder(
    private val ocupacionRepository: ScoringOcupacionRepository,
    private val departamentoRepository: ScoringDepartamentoRepository,
    private val paisRepository: PaisRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val existentes = ocupacionRepository.findAll().map { it.nombre }.toSet()
        OCUPACIONES.filter { it.first !in existentes }
            .forEach { (nombre, puntaje) -> ocupacionRepository.save(ScoringOcupacion(nombre = nombre, puntaje = puntaje)) }

        val peru = paisRepository.findByNombre("Peru")
        if (peru == null) {
            log.warn("No se cargaron los departamentos del scoring: falta el pais 'Peru'.")
            return
        }
        val departamentosExistentes = departamentoRepository.findAll()
            .filter { it.pais.id == peru.id }.map { it.nombre }.toSet()
        DEPARTAMENTOS.filter { it.first !in departamentosExistentes }
            .forEach { (nombre, puntaje) -> departamentoRepository.save(ScoringDepartamento(pais = peru, nombre = nombre, puntaje = puntaje)) }
    }

    companion object {
        // Mismos valores que las bases reales del equipo.
        private val OCUPACIONES = listOf(
            "ABOGADO" to 2,
            "ADMINISTRADOR DE EMPRESAS" to 2,
            "AGRONOMO O AFINES" to 1,
            "ANTROPOLOGO, ARQUEOLOGO, HISTORIADOR" to 1,
            "ARQUITECTO, URBANISTA" to 1,
            "BIBLIOTECARIO, DOCUMENTALISTA" to 1,
            "BIOLOGO" to 1,
            "BOTANICO Y ZOOLOGO" to 1,
            "CONTADOR" to 3,
            "ECONOMISTA" to 1,
            "ENFERMERIA" to 1,
            "INDEPENDIENTE" to 4,
            "SIN EMPLEO" to 2,
            "OTROS" to 3
        )

        private val DEPARTAMENTOS = listOf(
            "Amazonas" to 1,
            "Ancash" to 2,
            "Apurimac" to 2,
            "Arequipa" to 1,
            "Ayacucho" to 2,
            "Cajamarca" to 2,
            "Callao" to 2,
            "Cusco" to 2,
            "Huancavelica" to 2,
            "Huanuco" to 2,
            "Ica" to 1,
            "Junin" to 2,
            "La Libertad" to 3,
            "Lambayeque" to 2,
            "Lima" to 2,
            "Loreto" to 2,
            "Madre de Dios" to 1,
            "Moquegua" to 1,
            "Pasco" to 1,
            "Piura" to 3,
            "Puno" to 2,
            "San Martin" to 2,
            "Tacna" to 1,
            "Tumbes" to 1,
            "Ucayali" to 2
        )
    }
}
