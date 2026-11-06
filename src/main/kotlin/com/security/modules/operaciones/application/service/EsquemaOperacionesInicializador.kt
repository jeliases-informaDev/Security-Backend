package com.security.modules.operaciones.application.service

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Ajustes de esquema del Registro de Operaciones que Hibernate (ddl-auto: update) no hace solo en una base que ya existe.
 * Hibernate agrega las columnas y tablas nuevas; esto, al arrancar y sin pasos a mano:
 *  1. Quita el NOT NULL de beneficiarios_operacion_v2.fecha_nacimiento (el diseño nuevo no la pide).
 *  2. Pone el indice unico (empresa, numero_registro) y luego quita el viejo, que era solo numero_registro: el numero
 *     RO-AAAA-NNNNNN es correlativo por empresa y se repite entre empresas. Primero se crea el nuevo, asi nunca queda
 *     sin unicidad.
 * Cada paso mira information_schema antes de cambiar nada (idempotente). Un fallo se registra y no impide arrancar.
 */
@Component
@Order(2)
class EsquemaOperacionesInicializador(private val jdbc: JdbcTemplate) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        paso("fecha de nacimiento opcional del beneficiario") { fechaNacimientoOpcional() }
        paso("numero unico por empresa") { numeroUnicoPorEmpresa() }
    }

    private fun paso(nombre: String, accion: () -> Unit) {
        try {
            accion()
        } catch (e: Exception) {
            log.warn("Operaciones: no se pudo ajustar el esquema ({}): {}", nombre, e.message)
        }
    }

    internal fun fechaNacimientoOpcional(): Boolean {
        val nulo = jdbc.queryForList(
            """
            SELECT is_nullable FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'beneficiarios_operacion_v2' AND column_name = 'fecha_nacimiento'
            """,
            String::class.java
        ).firstOrNull() ?: return false
        if (nulo.equals("YES", ignoreCase = true)) return false
        jdbc.execute("ALTER TABLE beneficiarios_operacion_v2 MODIFY fecha_nacimiento DATE NULL")
        log.info("Operaciones: beneficiarios_operacion_v2.fecha_nacimiento ahora admite vacio")
        return true
    }

    /** Devuelve los indices viejos que quito. */
    internal fun numeroUnicoPorEmpresa(): List<String> {
        val unicos = jdbc.queryForList(
            """
            SELECT index_name AS indice, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columnas
            FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'registro_operaciones_v2' AND non_unique = 0
            GROUP BY index_name
            """
        ).map { (it["indice"] ?: it["INDICE"]).toString() to (it["columnas"] ?: it["COLUMNAS"]).toString().lowercase() }
        if (unicos.isEmpty()) return emptyList()
        if (unicos.none { it.second == "empresa,numero_registro" }) {
            jdbc.execute("ALTER TABLE registro_operaciones_v2 ADD CONSTRAINT $INDICE_NUEVO UNIQUE (empresa, numero_registro)")
            log.info("Operaciones: indice unico {} creado", INDICE_NUEVO)
        }
        val viejos = unicos.filter { it.second == "numero_registro" }.map { it.first }
        viejos.forEach { indice ->
            jdbc.execute("ALTER TABLE registro_operaciones_v2 DROP INDEX `${indice.replace("`", "")}`")
            log.info("Operaciones: indice unico viejo {} (solo numero_registro) quitado", indice)
        }
        return viejos
    }

    companion object {
        const val INDICE_NUEVO = "uk_reg_v2_empresa_numero"
    }
}
