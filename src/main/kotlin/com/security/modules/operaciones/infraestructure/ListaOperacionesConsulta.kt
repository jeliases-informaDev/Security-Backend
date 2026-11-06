package com.security.modules.operaciones.infraestructure

import com.security.modules.operaciones.dto.FilaOperacion
import com.security.modules.operaciones.dto.FiltrosOperaciones
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Conteos de las tarjetas de RO 1. */
data class ConteosOperaciones(
    val total: Long,
    val sinResponsable: Long,
    val delPeriodo: Long,
    val enviados: Long = 0,
    val inusuales: Long = 0,
    val sospechosas: Long = 0
)

/**
 * Lista e indicadores del Registro de Operaciones, resueltos en la base. Siempre filtra por la empresa del registro, que
 * es la de la cuenta dueña (si ya no existe, la guardada en el registro), igual que AccesoEmpresaService.
 */
@Repository
class ListaOperacionesConsulta(private val em: EntityManager) {

    private val desdeTablas = """
        FROM registro_operaciones_v2 r
        JOIN operaciones_v2 o ON o.id = r.id_operacion
        LEFT JOIN usuarios u ON u.id = r.id_usuario
        LEFT JOIN personas_operacion pf ON pf.id = r.id_persona_fisica
        LEFT JOIN personas_operacion pr ON pr.id = r.id_persona_representada
        LEFT JOIN usuarios ur ON ur.id = r.id_usuario_responsable
        LEFT JOIN analisis_operaciones a ON a.registro_id = r.id
    """

    private val deLaEmpresa = "LOWER(TRIM(COALESCE(NULLIF(TRIM(u.empresa), ''), r.empresa))) = LOWER(TRIM(:empresa))"

    @Transactional(readOnly = true)
    fun listar(empresa: String, filtros: FiltrosOperaciones, page: Int, size: Int): Pair<List<FilaOperacion>, Long> {
        val (condiciones, parametros) = condiciones(filtros)
        val where = (listOf(deLaEmpresa) + condiciones).joinToString(" AND ")
        val total = (parametrizar(em.createNativeQuery("SELECT COUNT(*) $desdeTablas WHERE $where"), empresa, parametros)
            .singleResult as Number).toLong()
        if (total == 0L) return emptyList<FilaOperacion>() to 0L
        val filas = parametrizar(
            em.createNativeQuery(
                """
                SELECT r.id, r.numero_registro,
                       NULLIF(CONCAT_WS(', ', NULLIF(pf.apellidos, ''), NULLIF(pf.nombres, '')), ''),
                       COALESCE(NULLIF(pr.razon_social, ''), NULLIF(CONCAT_WS(', ', NULLIF(pr.apellidos, ''), NULLIF(pr.nombres, '')), '')),
                       r.oficina, r.fecha_registro, r.fecha_creacion, o.fecha_operacion, o.tipo_operacion, o.modalidad_pago,
                       o.monto, o.moneda, r.id_usuario_responsable,
                       NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(ur.nombres), ''), NULLIF(TRIM(ur.ape_pat), ''), NULLIF(TRIM(ur.ape_mat), ''))), ''),
                       ur.usuario, a.estado, r.fecha_envio, o.monto_usd, o.umbral_estado
                $desdeTablas
                WHERE $where
                ORDER BY r.fecha_registro DESC, r.fecha_creacion DESC, r.id DESC
                """
            ),
            empresa, parametros
        )
            .setFirstResult(page * size)
            .setMaxResults(size)
            .resultList
            .map { fila ->
                val c = fila as Array<*>
                FilaOperacion(
                    id = (c[0] as Number).toLong(),
                    numeroRegistro = c[1]?.toString(),
                    personaFisica = c[2]?.toString(),
                    personaNombre = c[3]?.toString(),
                    oficina = c[4]?.toString(),
                    fecha = fecha(c[5]),
                    creadoEn = fechaHora(c[6]),
                    fechaOperacion = fecha(c[7]),
                    tipoOperacion = c[8]?.toString(),
                    modalidadPago = c[9]?.toString(),
                    monto = decimal(c[10]),
                    moneda = c[11]?.toString(),
                    responsableId = (c[12] as Number?)?.toInt(),
                    responsableNombre = (c[13] ?: c[14])?.toString(),
                    estadoAnalisis = c[15]?.toString(),
                    fechaEnvio = fecha(c[16]),
                    montoUsd = decimal(c[17]),
                    umbralEstado = c[18]?.toString()
                )
            }
        return filas to total
    }

    @Transactional(readOnly = true)
    fun conteos(empresa: String, desde: LocalDate, hasta: LocalDate): ConteosOperaciones {
        val c = em.createNativeQuery(
            """
            SELECT COUNT(*),
                   COALESCE(SUM(CASE WHEN r.id_usuario_responsable IS NULL THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN r.fecha_registro BETWEEN :desde AND :hasta THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN r.fecha_envio IS NOT NULL THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN a.estado IN ('EN_ANALISIS', 'SOSPECHOSA') THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN a.estado = 'SOSPECHOSA' THEN 1 ELSE 0 END), 0)
            $desdeTablas
            WHERE $deLaEmpresa
            """
        )
            .setParameter("empresa", empresa)
            .setParameter("desde", desde)
            .setParameter("hasta", hasta)
            .singleResult as Array<*>
        fun n(i: Int) = (c[i] as Number).toLong()
        return ConteosOperaciones(n(0), n(1), n(2), n(3), n(4), n(5))
    }

    /** Registros de la empresa cuya fecha de operacion cae en [desde, hasta]: id y fecha de envio (null = pendiente). */
    @Transactional(readOnly = true)
    fun delPeriodo(empresa: String, desde: LocalDate, hasta: LocalDate): List<Pair<Long, LocalDate?>> =
        em.createNativeQuery(
            """
            SELECT r.id, r.fecha_envio
            $desdeTablas
            WHERE $deLaEmpresa AND o.fecha_operacion BETWEEN :desde AND :hasta
            ORDER BY o.fecha_operacion, r.id
            """
        )
            .setParameter("empresa", empresa)
            .setParameter("desde", desde)
            .setParameter("hasta", hasta)
            .resultList
            .map { fila -> val c = fila as Array<*>; (c[0] as Number).toLong() to fecha(c[1]) }

    private fun condiciones(f: FiltrosOperaciones): Pair<List<String>, Map<String, Any>> {
        val cond = mutableListOf<String>()
        val par = mutableMapOf<String, Any>()
        f.q?.let { q ->
            cond.add(
                """(LOWER(r.numero_registro) LIKE :q ESCAPE '!'
                    OR LOWER(pf.numero_documento) LIKE :q ESCAPE '!' OR LOWER(pr.numero_documento) LIKE :q ESCAPE '!'
                    OR LOWER(CONCAT_WS(' ', pf.nombres, pf.apellidos)) LIKE :q ESCAPE '!'
                    OR LOWER(CONCAT_WS(' ', pf.apellidos, pf.nombres)) LIKE :q ESCAPE '!'
                    OR LOWER(CONCAT_WS(' ', pr.nombres, pr.apellidos)) LIKE :q ESCAPE '!'
                    OR LOWER(CONCAT_WS(' ', pr.apellidos, pr.nombres)) LIKE :q ESCAPE '!'
                    OR LOWER(pr.razon_social) LIKE :q ESCAPE '!')"""
            )
            par["q"] = "%" + q.lowercase().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
        }
        f.desde?.let { cond.add("o.fecha_operacion >= :desde"); par["desde"] = it }
        f.hasta?.let { cond.add("o.fecha_operacion <= :hasta"); par["hasta"] = it }
        if (f.tipoOperacion.isNotEmpty()) {
            cond.add("o.tipo_operacion IN (:tipos)"); par["tipos"] = f.tipoOperacion
        }
        if (f.modalidadPago.isNotEmpty()) {
            cond.add("o.modalidad_pago IN (:modalidades)"); par["modalidades"] = f.modalidadPago
        }
        f.moneda?.let { codigo ->
            // Registros nuevos: el codigo; viejos: "PEN — Soles" o el id de la tabla monedas
            val partes = mutableListOf("UPPER(TRIM(o.moneda)) = :moneda", "UPPER(o.moneda) LIKE :monedaTexto")
            par["moneda"] = codigo.uppercase()
            par["monedaTexto"] = codigo.uppercase() + " %"
            f.monedaId?.let { partes.add("TRIM(o.moneda) = :monedaId"); par["monedaId"] = it.toString() }
            cond.add(partes.joinToString(" OR ", "(", ")"))
        }
        if (f.estados.isNotEmpty()) {
            val partes = f.estados.mapNotNull {
                when (it) {
                    "PENDIENTE" -> "r.fecha_envio IS NULL"
                    "ENVIADO" -> "r.fecha_envio IS NOT NULL"
                    "INUSUAL" -> "a.estado IN ('EN_ANALISIS', 'SOSPECHOSA')"
                    else -> null
                }
            }
            if (partes.isNotEmpty()) cond.add(partes.joinToString(" OR ", "(", ")"))
        }
        f.oficina?.let { cond.add("LOWER(TRIM(r.oficina)) = LOWER(TRIM(:oficina))"); par["oficina"] = it }
        f.montoMinUsd?.let { cond.add("o.monto_usd >= :montoMin"); par["montoMin"] = it }
        f.montoMaxUsd?.let { cond.add("o.monto_usd <= :montoMax"); par["montoMax"] = it }
        if (f.sinResponsable) cond.add("r.id_usuario_responsable IS NULL")
        else f.responsableId?.let { cond.add("r.id_usuario_responsable = :responsable"); par["responsable"] = it }
        return cond to par
    }

    private fun parametrizar(query: Query, empresa: String, parametros: Map<String, Any>): Query {
        query.setParameter("empresa", empresa)
        parametros.forEach { (k, v) -> query.setParameter(k, v) }
        return query
    }

    private fun fecha(v: Any?): LocalDate? = when (v) {
        null -> null
        is LocalDate -> v
        is java.sql.Date -> v.toLocalDate()
        is LocalDateTime -> v.toLocalDate()
        is java.sql.Timestamp -> v.toLocalDateTime().toLocalDate()
        else -> LocalDate.parse(v.toString().take(10))
    }

    private fun fechaHora(v: Any?): LocalDateTime? = when (v) {
        null -> null
        is LocalDateTime -> v
        is java.sql.Timestamp -> v.toLocalDateTime()
        is java.time.OffsetDateTime -> v.toLocalDateTime()
        is java.time.Instant -> LocalDateTime.ofInstant(v, java.time.ZoneId.systemDefault())
        else -> LocalDateTime.parse(v.toString().replace(' ', 'T'))
    }

    private fun decimal(v: Any?): BigDecimal? = when (v) {
        null -> null
        is BigDecimal -> v
        is Number -> BigDecimal(v.toString())
        else -> BigDecimal(v.toString())
    }
}
