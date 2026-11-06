package com.security.modules.operaciones.infraestructure

import com.security.modules.operaciones.domain.CatalogoOperacionEntity
import com.security.modules.operaciones.domain.EventoOperacionEntity
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface CatalogoOperacionRepository : JpaRepository<CatalogoOperacionEntity, Long> {
    fun findAllByActivoTrueOrderByCatalogoAscOrdenAsc(): List<CatalogoOperacionEntity>
}

/**
 * Historial de un registro: solo guardar y leer. A proposito no extiende JpaRepository (no hay delete ni saveAll de
 * entidades existentes): el historial no se edita ni se borra.
 */
interface HistorialOperacionRepository : Repository<EventoOperacionEntity, Long> {
    fun save(evento: EventoOperacionEntity): EventoOperacionEntity
    fun findAllByRegistroIdOrderByFechaDescIdDesc(registroId: Long): List<EventoOperacionEntity>
    fun existsByRegistroIdAndTipo(registroId: Long, tipo: String): Boolean
}

/**
 * Numero RO-AAAA-NNNNNN por empresa y año. La fila del contador se bloquea (SELECT … FOR UPDATE) dentro de la misma
 * transaccion que guarda el registro: una segunda alta simultanea espera a que la primera termine y toma el siguiente.
 * Si la primera falla, su numero no se usa (rollback). Ademas, el indice unico (empresa, numero_registro) impide repetir.
 */
@org.springframework.stereotype.Repository
class NumeradorRegistros(
    private val em: EntityManager,
    private val jdbc: JdbcTemplate,
    transacciones: PlatformTransactionManager
) {

    // La fila del año se crea en una transaccion corta y aparte: asi dos primeras altas simultaneas no se bloquean
    // mutuamente (el INSERT IGNORE de la segunda suelta su bloqueo al terminar, antes del SELECT … FOR UPDATE).
    private val aparte = TransactionTemplate(transacciones).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun siguiente(empresa: String, anio: Int): String {
        val clave = empresa.trim().lowercase()
        val existe = jdbc.queryForObject(
            "SELECT COUNT(*) FROM contador_registro_operaciones WHERE empresa = ? AND anio = ?", Long::class.javaObjectType, clave, anio
        ) ?: 0L
        if (existe == 0L) {
            aparte.executeWithoutResult {
                jdbc.update("INSERT IGNORE INTO contador_registro_operaciones (empresa, anio, ultimo) VALUES (?, ?, 0)", clave, anio)
            }
        }
        val ultimo = (em.createNativeQuery(
            "SELECT ultimo FROM contador_registro_operaciones WHERE empresa = :empresa AND anio = :anio FOR UPDATE"
        )
            .setParameter("empresa", clave)
            .setParameter("anio", anio)
            .singleResult as Number).toInt()
        // Por si el contador quedara atras de los numeros ya usados (p. ej. una base restaurada sin esta tabla).
        val prefijo = "RO-%04d-".format(anio)
        val usado = (em.createNativeQuery(
            """
            SELECT COALESCE(MAX(CAST(SUBSTRING(numero_registro, 9) AS UNSIGNED)), 0) FROM registro_operaciones_v2
            WHERE LOWER(TRIM(empresa)) = :empresa AND numero_registro LIKE :patron
              AND id NOT IN (SELECT m.id_fila FROM inicio_demo_marca m WHERE m.tipo = 'REGISTRO')
            """
        )
            .setParameter("empresa", clave)
            .setParameter("patron", "$prefijo%")
            .singleResult as Number).toInt()
        val siguiente = maxOf(ultimo, usado) + 1
        em.createNativeQuery("UPDATE contador_registro_operaciones SET ultimo = :ultimo WHERE empresa = :empresa AND anio = :anio")
            .setParameter("ultimo", siguiente)
            .setParameter("empresa", clave)
            .setParameter("anio", anio)
            .executeUpdate()
        return prefijo + "%06d".format(siguiente)
    }
}
