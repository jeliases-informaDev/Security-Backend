package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.domain.CatalogoOperacionEntity
import com.security.modules.operaciones.domain.Moneda
import com.security.modules.operaciones.infraestructure.CatalogoOperacionRepository
import com.security.modules.operaciones.domain.Provincia
import com.security.modules.operaciones.infraestructure.MonedaRepository
import com.security.modules.operaciones.infraestructure.ProvinciaRepository
import com.security.modules.scoring.infrastructure.ScoringDepartamentoRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Al arrancar, deja llenos los combos "Moneda" y "Provincia" del Registro de Operaciones. Una base nueva
 * trae las tablas `monedas` y `provincia` vacias y, sin filas, los combos salen sin opciones.
 *
 * Agrega solo lo que falta (monedas por codigo, provincias por departamento y nombre): no cambia ni borra
 * nada de lo que ya hay. Se ejecuta siempre, sin depender de app.seed, y va despues de los catalogos de
 * scoring porque las provincias cuelgan de los departamentos. Un fallo se registra y no impide que la
 * API arranque. Las provincias salen de operaciones/provincias.csv (196 del Peru, sin tildes como el
 * resto de los catalogos); un departamento que no exista en la base se omite con aviso.
 */
@Component
@Order(3)
class CatalogoOperacionesInicializador(
    private val monedaRepository: MonedaRepository,
    private val provinciaRepository: ProvinciaRepository,
    private val departamentoRepository: ScoringDepartamentoRepository,
    transactionManager: PlatformTransactionManager,
    // Opciones del formulario nuevo (modalidad, origen de fondos, estado civil, tipo de documento y de operacion)
    private val catalogoRegistroRepository: CatalogoOperacionRepository? = null
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    // Todo dentro de una transaccion: con provincias ya guardadas se lee el nombre de su departamento, que es
    // perezoso, y fuera de una sesion fallaba ("Could not initialize proxy ... no session").
    private val tx = TransactionTemplate(transactionManager)

    override fun run(args: ApplicationArguments) {
        // Cada catalogo en su transaccion: si uno falla, el otro igual queda.
        try {
            tx.executeWithoutResult { sembrarMonedas() }
        } catch (e: Exception) {
            log.warn("No se pudieron preparar las monedas de Operaciones: {}", e.message)
        }
        try {
            tx.executeWithoutResult { sembrarProvincias(leerProvincias()) }
        } catch (e: Exception) {
            log.warn("No se pudieron preparar las provincias de Operaciones: {}", e.message)
        }
        if (catalogoRegistroRepository != null) try {
            tx.executeWithoutResult { sembrarCatalogosRegistro(leerCatalogosRegistro()) }
        } catch (e: Exception) {
            log.warn("No se pudieron preparar los catalogos del formulario de Operaciones: {}", e.message)
        }
    }

    /** Agrega las opciones que faltan (por catalogo y codigo); no cambia ni borra las que ya estan. */
    internal fun sembrarCatalogosRegistro(filas: List<CatalogoOperacionEntity>): Int {
        val repositorio = catalogoRegistroRepository ?: return 0
        val existentes = repositorio.findAll().map { it.catalogo to it.codigo }.toSet()
        val nuevas = filas.filter { (it.catalogo to it.codigo) !in existentes }
        if (nuevas.isNotEmpty()) {
            repositorio.saveAll(nuevas)
            log.info("Operaciones: opciones de catalogo agregadas={}", nuevas.size)
        }
        return nuevas.size
    }

    /** Lee "catalogo;codigo;nombre;orden;pide_texto;sector" (con encabezado) desde el classpath. */
    internal fun leerCatalogosRegistro(): List<CatalogoOperacionEntity> =
        ClassPathResource(RUTA_CATALOGOS).inputStream.bufferedReader(Charsets.UTF_8).useLines { lineas ->
            lineas.drop(1)
                .filter { it.isNotBlank() }
                .map { linea ->
                    val c = linea.split(";").map { it.trim() }
                    CatalogoOperacionEntity(
                        catalogo = c[0], codigo = c[1], nombre = c[2], orden = c[3].toInt(),
                        pideTexto = c[4].toBoolean(), sector = c.getOrNull(5)?.takeIf { it.isNotEmpty() }
                    )
                }
                .toList()
        }

    internal fun sembrarMonedas(): Int {
        val existentes = monedaRepository.findAll().mapNotNull { it.codigo?.trim()?.uppercase() }.toSet()
        val nuevas = MONEDAS.filter { it.codigo !in existentes }
            .map { Moneda(codigo = it.codigo, nombre = it.nombre, simbolo = it.simbolo) }
        if (nuevas.isNotEmpty()) {
            monedaRepository.saveAll(nuevas)
            log.info("Operaciones: monedas agregadas={}", nuevas.size)
        }
        return nuevas.size
    }

    internal fun sembrarProvincias(filas: List<Pair<String, String>>): Int {
        val departamentos = departamentoRepository.findAll().associateBy { clave(it.nombre) }
        val existentes = provinciaRepository.findAll()
            .map { clave(it.departamento.nombre) to clave(it.nombre) }.toMutableSet()
        val sinDepartamento = mutableSetOf<String>()
        val nuevas = mutableListOf<Provincia>()
        for ((nombreDepartamento, nombre) in filas) {
            val departamento = departamentos[clave(nombreDepartamento)]
            if (departamento == null) {
                sinDepartamento.add(nombreDepartamento)
                continue
            }
            if (existentes.add(clave(nombreDepartamento) to clave(nombre))) {
                nuevas.add(Provincia(nombre = nombre, departamento = departamento))
            }
        }
        if (sinDepartamento.isNotEmpty()) {
            log.warn("Operaciones: sin provincias para los departamentos que no existen en la base: {}", sinDepartamento)
        }
        if (nuevas.isNotEmpty()) {
            provinciaRepository.saveAll(nuevas)
            log.info("Operaciones: provincias agregadas={}", nuevas.size)
        }
        return nuevas.size
    }

    /** Lee "departamento;provincia" (con encabezado) desde el classpath. */
    internal fun leerProvincias(): List<Pair<String, String>> =
        ClassPathResource(RUTA_PROVINCIAS).inputStream.bufferedReader(Charsets.UTF_8).useLines { lineas ->
            lineas.drop(1)
                .filter { it.isNotBlank() }
                .map { linea ->
                    val (departamento, provincia) = linea.split(";")
                    departamento.trim() to provincia.trim()
                }
                .toList()
        }

    private fun clave(texto: String) = texto.trim().lowercase()

    internal data class MonedaBase(val codigo: String, val nombre: String, val simbolo: String)

    companion object {
        const val RUTA_PROVINCIAS = "operaciones/provincias.csv"
        const val RUTA_CATALOGOS = "operaciones/catalogos-registro.csv"

        internal val MONEDAS = listOf(
            MonedaBase("PEN", "Soles", "S/"),
            MonedaBase("USD", "Dolares americanos", "$"),
            MonedaBase("EUR", "Euros", "€")
        )
    }
}
