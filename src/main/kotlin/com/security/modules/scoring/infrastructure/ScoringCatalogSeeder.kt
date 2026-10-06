package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringDepartamento
import com.security.modules.scoring.domain.ScoringOcupacion
import com.security.shared.datos.repositories.PaisRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Catalogos del scoring de riesgo (ocupaciones y departamentos con su puntaje 1-5). Sin ellos los combos
 * "Ocupacion / Profesion" y "Departamento" de la pantalla de Scoring salen vacios.
 *
 * Los valores salen de los archivos .csv de src/main/resources/scoring (libro vigente, ver LEEME.md en esa carpeta).
 * Solo corre con app.seed.enabled=true (APP_SEED_ENABLED), igual que DevDataSeeder, y despues de este
 * (necesita el pais "Peru"). Es idempotente: agrega lo que falta y deja los puntajes iguales al CSV.
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
        sembrarOcupaciones()
        sembrarDepartamentos()
    }

    private fun sembrarOcupaciones() {
        val existentes = ocupacionRepository.findAll().associateBy { it.nombre }
        var nuevas = 0
        var corregidas = 0
        leerCsv("scoring/ocupaciones.csv").forEach { (nombre, puntaje) ->
            val actual = existentes[nombre]
            when {
                actual == null -> {
                    ocupacionRepository.save(ScoringOcupacion(nombre = nombre, puntaje = puntaje))
                    nuevas++
                }
                actual.puntaje != puntaje -> {
                    actual.puntaje = puntaje
                    corregidas++
                }
            }
        }
        log.info("Scoring: ocupaciones nuevas={}, puntajes corregidos={}", nuevas, corregidas)
    }

    private fun sembrarDepartamentos() {
        val peru = paisRepository.findByNombre("Peru")
        if (peru == null) {
            log.warn("No se cargaron los departamentos del scoring: falta el pais 'Peru'.")
            return
        }
        val existentes = departamentoRepository.findAll().filter { it.pais.id == peru.id }.associateBy { it.nombre }
        var nuevos = 0
        var corregidos = 0
        leerCsv("scoring/departamentos.csv").forEach { (nombre, puntaje) ->
            val actual = existentes[nombre]
            when {
                actual == null -> {
                    departamentoRepository.save(ScoringDepartamento(pais = peru, nombre = nombre, puntaje = puntaje))
                    nuevos++
                }
                actual.puntaje != puntaje -> {
                    actual.puntaje = puntaje
                    corregidos++
                }
            }
        }
        log.info("Scoring: departamentos nuevos={}, puntajes corregidos={}", nuevos, corregidos)
    }

    /** Lee "nombre;puntaje" (con encabezado) desde el classpath. */
    private fun leerCsv(ruta: String): List<Pair<String, Int>> =
        ClassPathResource(ruta).inputStream.bufferedReader(Charsets.UTF_8).useLines { lineas ->
            lineas.drop(1)
                .filter { it.isNotBlank() }
                .map { linea ->
                    val (nombre, puntaje) = linea.split(";")
                    nombre.trim() to puntaje.trim().toInt()
                }
                .toList()
        }
}
