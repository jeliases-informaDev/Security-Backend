package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringDepartamento
import com.security.modules.scoring.domain.ScoringOcupacion
import com.security.modules.scoring.domain.ScoringOpcion
import com.security.modules.scoring.domain.ScoringPaisGrupo
import com.security.modules.scoring.domain.metodologia.CalculoScoring
import com.security.shared.datos.repositories.PaisRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

/**
 * Catalogos del scoring de riesgo. Corre SIEMPRE al arrancar (tambien con APP_SEED_ENABLED apagado, como en
 * produccion), despues del seed de desarrollo (que crea el pais "Peru") y antes de las provincias de Operaciones
 * (que cuelgan de los departamentos). Lee los .csv de src/main/resources/scoring (ver LEEME.md en esa carpeta).
 *
 * Es idempotente y nunca pisa lo que alguien cambio a mano: crea lo que falta y completa lo que esta vacio.
 *  - Ocupaciones y departamentos: crea los que faltan (con su puntaje 1-5 de la version 1 y el 1-4 de la
 *    version 2) y completa `puntaje_v2` donde esta vacio. Los puntajes de la version 1 no se tocan.
 *  - Producto, canal y medio de pago: crea los que faltan y completa el puntaje vacio.
 *  - Paises: a cada pais de la tabla `pais` le da su grupo y la marca del GAFI. No crea paises: si un pais del
 *    archivo no existe en la tabla, lo avisa en el log. Una clasificacion del archivo con una vigencia mas nueva
 *    que la guardada reemplaza a la guardada (asi se actualiza la lista del GAFI); para un cambio a mano en la
 *    tabla, pon tambien la vigencia del dia del cambio.
 * Un fallo se registra en el log y no impide que la API arranque.
 */
@Component
@Order(2)
class ScoringCatalogoInicializador(
    private val ocupacionRepository: ScoringOcupacionRepository,
    private val departamentoRepository: ScoringDepartamentoRepository,
    private val opcionRepository: ScoringOpcionRepository,
    private val paisGrupoRepository: ScoringPaisGrupoRepository,
    private val paisRepository: PaisRepository,
    transactionManager: PlatformTransactionManager
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    override fun run(args: ApplicationArguments) {
        paso("ocupaciones") { sembrarOcupaciones(leer("scoring/ocupaciones.csv")) }
        paso("departamentos") { sembrarDepartamentos(leer("scoring/departamentos.csv")) }
        paso("productos") { sembrarOpciones(ScoringOpcion.PRODUCTO, leer("scoring/productos.csv")) }
        paso("canales") { sembrarOpciones(ScoringOpcion.CANAL, leer("scoring/canales.csv")) }
        paso("medios de pago") { sembrarOpciones(ScoringOpcion.MEDIO_PAGO, leer("scoring/medios-pago.csv")) }
        paso("paises") { sembrarPaises(leer("scoring/paises.csv")) }
    }

    private fun paso(nombre: String, accion: () -> Unit) {
        try {
            tx.executeWithoutResult { accion() }
        } catch (e: Exception) {
            log.warn("Scoring: no se pudo preparar el catalogo de {}: {}", nombre, e.message)
        }
    }

    internal fun sembrarOcupaciones(filas: List<List<String>>) {
        val existentes = ocupacionRepository.findAll().associateBy { clave(it.nombre) }
        var nuevas = 0
        var completadas = 0
        filas.forEach { f ->
            val nombre = f[0]
            val v1 = f.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val v2 = f.getOrNull(2)?.toIntOrNull()
            val actual = existentes[clave(nombre)]
            when {
                actual == null -> {
                    ocupacionRepository.save(ScoringOcupacion(nombre = nombre, puntaje = v1, puntajeV2 = v2))
                    nuevas++
                }
                actual.puntajeV2 == null && v2 != null -> {
                    actual.puntajeV2 = v2
                    completadas++
                }
            }
        }
        val sinPuntaje = ocupacionRepository.findAll().count { it.puntajeV2 == null }
        log.info("Scoring: ocupaciones nuevas={}, puntajes v2 completados={}, sin puntaje en la metodologia={}", nuevas, completadas, sinPuntaje)
    }

    internal fun sembrarDepartamentos(filas: List<List<String>>) {
        val peru = paisRepository.findAll().firstOrNull { clave(it.nombre ?: "") == clave("Peru") }
        val existentes = departamentoRepository.findAll()
            .filter { peru == null || it.pais.id == peru.id }
            .associateBy { clave(it.nombre) }
        var nuevos = 0
        var completados = 0
        val sinCrear = mutableListOf<String>()
        filas.forEach { f ->
            val nombre = f[0]
            val v1 = f.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val v2 = f.getOrNull(2)?.toIntOrNull()
            val actual = existentes[clave(nombre)]
            when {
                actual == null && peru == null -> sinCrear.add(nombre)
                actual == null -> {
                    departamentoRepository.save(ScoringDepartamento(pais = peru!!, nombre = nombre, puntaje = v1, puntajeV2 = v2))
                    nuevos++
                }
                actual.puntajeV2 == null && v2 != null -> {
                    actual.puntajeV2 = v2
                    completados++
                }
            }
        }
        if (sinCrear.isNotEmpty()) {
            log.warn("Scoring: no se crearon {} departamentos porque falta el pais 'Peru' en la tabla pais.", sinCrear.size)
        }
        log.info("Scoring: departamentos nuevos={}, puntajes v2 completados={}", nuevos, completados)
    }

    internal fun sembrarOpciones(tipo: String, filas: List<List<String>>) {
        val existentes = opcionRepository.findByTipo(tipo).associateBy { clave(it.nombre) }
        var nuevas = 0
        var completadas = 0
        filas.forEachIndexed { i, f ->
            val nombre = f[0]
            val puntaje = f.getOrNull(1)?.toIntOrNull()
            val pide = f.getOrNull(2)?.let { esSi(it) } ?: false
            val actual = existentes[clave(nombre)]
            if (actual == null) {
                opcionRepository.save(ScoringOpcion(tipo = tipo, nombre = nombre, puntaje = puntaje, orden = i + 1, pideOrigenFondos = pide))
                nuevas++
            } else {
                var cambio = false
                if (actual.puntaje == null && puntaje != null) { actual.puntaje = puntaje; cambio = true }
                if (actual.orden == null) { actual.orden = i + 1; cambio = true }
                if (actual.pideOrigenFondos == null) { actual.pideOrigenFondos = pide; cambio = true }
                if (cambio) completadas++
            }
        }
        log.info("Scoring: opciones {} nuevas={}, completadas={}", tipo, nuevas, completadas)
    }

    internal fun sembrarPaises(filas: List<List<String>>) {
        val clasificaciones = filas.filter { it[0] != COMODIN }.map { Clasificacion.de(it) }
        val porDefecto = filas.firstOrNull { it[0] == COMODIN }?.let { Clasificacion.de(it) }
        val porNombre = mutableMapOf<String, Clasificacion>()
        clasificaciones.forEach { c -> c.nombres.forEach { porNombre[clave(it)] = c } }

        val paises = paisRepository.findAll()
        val guardados = paisGrupoRepository.todosConPais().associateBy { it.pais.id }
        val encontrados = mutableSetOf<String>()
        var nuevos = 0
        var actualizados = 0
        paises.forEach { pais ->
            val nombre = pais.nombre ?: return@forEach
            val propia = porNombre[clave(nombre)]
            if (propia != null) encontrados.add(propia.nombres.first())
            val c = propia ?: porDefecto ?: return@forEach
            val actual = guardados[pais.id]
            when {
                actual == null -> {
                    paisGrupoRepository.save(ScoringPaisGrupo(pais = pais, grupo = c.grupo, altoRiesgoGafi = c.gafi, vigencia = c.vigencia))
                    nuevos++
                }
                debeActualizar(actual, c) -> {
                    actual.grupo = c.grupo
                    actual.altoRiesgoGafi = c.gafi
                    actual.vigencia = c.vigencia
                    actualizados++
                }
            }
        }
        val faltan = clasificaciones.map { it.nombres.first() }.filter { it !in encontrados }
        if (faltan.isNotEmpty()) {
            log.warn("Scoring: estos paises de la clasificacion no existen en la tabla pais (no se crearon): {}", faltan)
        }
        log.info("Scoring: paises clasificados nuevos={}, actualizados={}, en la tabla={}", nuevos, actualizados, paises.size)
    }

    /** Un grupo vacio se completa; una clasificacion del archivo con vigencia mas nueva reemplaza a la guardada. */
    private fun debeActualizar(actual: ScoringPaisGrupo, c: Clasificacion): Boolean {
        if (actual.grupo == null) return true
        val nueva = c.vigencia ?: return false
        val guardada = actual.vigencia ?: return true
        return nueva.isAfter(guardada)
    }

    private data class Clasificacion(val nombres: List<String>, val grupo: Int?, val gafi: Boolean, val vigencia: LocalDate?) {
        companion object {
            fun de(f: List<String>) = Clasificacion(
                nombres = listOf(f[0]) + (f.getOrNull(4)?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()),
                grupo = f.getOrNull(1)?.toIntOrNull(),
                gafi = f.getOrNull(2)?.let { esSi(it) } ?: false,
                vigencia = f.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
            )
        }
    }

    companion object {
        const val COMODIN = "*"

        fun clave(texto: String): String = CalculoScoring.normalizar(texto)

        fun esSi(texto: String): Boolean = texto.trim().lowercase() in setOf("si", "sí", "true", "1", "x")

        /** Filas "a;b;c" de un .csv con encabezado (las columnas vacias quedan como ""). */
        fun leer(ruta: String): List<List<String>> =
            ClassPathResource(ruta).inputStream.bufferedReader(Charsets.UTF_8).useLines { lineas ->
                lineas.drop(1)
                    .map { it.removePrefix("﻿") }
                    .filter { it.isNotBlank() }
                    .map { linea -> linea.split(";").map { it.trim() } }
                    .toList()
            }
    }
}
