package com.security.modules.scoring.infrastructure

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/** Total de operaciones de una moneda (tal como esta escrita en el registro, p. ej. "PEN — Soles"). */
data class TotalMoneda(val moneda: String?, val total: BigDecimal, val cantidad: Long)

/**
 * Solo lectura sobre el Registro de Operaciones (tablas registro_operaciones_v2, operaciones_v2 y
 * personas_operacion) para el factor "Monto y frecuencia". Ubica al cliente por su numero de documento, como
 * persona que realiza la operacion o como persona en cuyo nombre se realiza, y solo entre los registros de la
 * empresa indicada (la empresa del registro es la de la cuenta duena, igual que en la lista del registro).
 */
@Repository
class VolumenOperacionesConsulta(private val em: EntityManager) {

    // En su propia transaccion: si la consulta falla, no arrastra a la evaluacion que se esta guardando.
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun totalesPorMoneda(empresa: String, documento: String, desde: LocalDate, hasta: LocalDate): List<TotalMoneda> {
        val filas = em.createNativeQuery(
            """
            SELECT o.moneda, COALESCE(SUM(o.monto), 0), COUNT(*)
            FROM registro_operaciones_v2 r
            JOIN operaciones_v2 o ON o.id = r.id_operacion
            LEFT JOIN usuarios u ON u.id = r.id_usuario
            LEFT JOIN personas_operacion pf ON pf.id = r.id_persona_fisica
            LEFT JOIN personas_operacion pr ON pr.id = r.id_persona_representada
            WHERE LOWER(TRIM(COALESCE(u.empresa, r.empresa))) = LOWER(TRIM(:empresa))
              AND o.fecha_operacion BETWEEN :desde AND :hasta
              AND (TRIM(pf.numero_documento) = :documento OR TRIM(pr.numero_documento) = :documento)
            GROUP BY o.moneda
            """
        )
            .setParameter("empresa", empresa)
            .setParameter("documento", documento.trim())
            .setParameter("desde", desde)
            .setParameter("hasta", hasta)
            .resultList

        return filas.map { fila ->
            val columnas = fila as Array<*>
            TotalMoneda(
                moneda = columnas[0]?.toString(),
                total = when (val t = columnas[1]) {
                    is BigDecimal -> t
                    is Number -> BigDecimal(t.toString())
                    else -> BigDecimal.ZERO
                },
                cantidad = (columnas[2] as? Number)?.toLong() ?: 0L
            )
        }
    }
}
