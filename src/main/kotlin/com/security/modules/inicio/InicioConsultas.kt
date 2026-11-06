package com.security.modules.inicio

import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.ResultadoCierreDenuncia
import com.security.modules.listas.infrastructure.ALCANCE_CONSULTAS
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Hasta donde mira cada bloque (sale del token, nunca del cliente). */
data class AlcanceInicio(val todo: Boolean, val empresa: String?, val usuarioId: Int?)

/** Una combinacion clasificacion + filas con coincidencia, con sus conteos (el reparto se arma con esto). */
data class GrupoConsultas(val clasificacion: String?, val filas: String?, val cantidad: Long, val pep: Long, val homonimos: Long)

data class DiaConsultas(val dia: LocalDate, val total: Long, val conCoincidencia: Long, val pep: Long, val homonimos: Long)

data class CifrasOperaciones(val registros: Long, val montoUsd: BigDecimal, val inusuales: Long, val sobreUmbral: Long, val mismoDia: Long)

data class FilaUltimoRegistro(
    val id: Long, val numero: String?, val apellidos: String?, val nombres: String?, val razonSocial: String?,
    val tipoOperacion: String?, val monto: BigDecimal?, val moneda: String?, val responsableId: Number?,
    val estadoAnalisis: String?, val fechaEnvio: Any?
)

data class UbicacionRegistros(val departamento: String?, val provincia: String?, val registros: Long, val montoUsd: BigDecimal)

data class AbiertasDenuncias(val porAtender: Long, val sinAbrir: Long, val vencidas: Long)

/**
 * Todas las cifras del tablero se cuentan y agrupan en la base (COUNT, SUM, GROUP BY): no se traen filas a memoria,
 * salvo los ultimos 3 registros y las fechas de las denuncias cerradas en el mes (para el promedio de dias).
 */
@Repository
class InicioConsultas(private val em: EntityManager) {

    // ------------------------------------------------------------------ Listas negativas (consultas_listas)

    private val baseConsultas = "FROM ConsultaLista c WHERE c.estado = 'RESUELTA' AND $ALCANCE_CONSULTAS"

    private fun alcanceListas(q: Query, a: AlcanceInicio): Query =
        q.setParameter("todo", a.todo).setParameter("empresa", a.empresa).setParameter("usuarioId", a.usuarioId)

    @Transactional(readOnly = true)
    fun contarConsultas(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): Long =
        (alcanceListas(em.createQuery("SELECT COUNT(c) $baseConsultas AND c.fecha >= :desde AND c.fecha < :hasta"), a)
            .setParameter("desde", desde).setParameter("hasta", hasta).singleResult as Number).toLong()

    @Transactional(readOnly = true)
    fun gruposConsultas(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): List<GrupoConsultas> =
        alcanceListas(
            em.createQuery(
                """
                SELECT c.clasificacion, c.listasCoincidenciaTexto, COUNT(c),
                       SUM(CASE WHEN c.esPep = true THEN 1 ELSE 0 END), SUM(CASE WHEN c.homonimo = true THEN 1 ELSE 0 END)
                $baseConsultas AND c.fecha >= :desde AND c.fecha < :hasta
                GROUP BY c.clasificacion, c.listasCoincidenciaTexto
                """
            ), a
        ).setParameter("desde", desde).setParameter("hasta", hasta).resultList.map {
            val r = it as Array<*>
            GrupoConsultas(r[0] as String?, r[1] as String?, n(r[2]), n(r[3]), n(r[4]))
        }

    @Transactional(readOnly = true)
    fun consultasPorDia(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): List<DiaConsultas> =
        alcanceListas(
            em.createQuery(
                """
                SELECT CAST(c.fecha AS LocalDate), COUNT(c),
                       SUM(CASE WHEN c.clasificacion <> 'SIN_COINCIDENCIAS' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN c.esPep = true THEN 1 ELSE 0 END), SUM(CASE WHEN c.homonimo = true THEN 1 ELSE 0 END)
                $baseConsultas AND c.fecha >= :desde AND c.fecha < :hasta
                GROUP BY CAST(c.fecha AS LocalDate)
                """
            ), a
        ).setParameter("desde", desde).setParameter("hasta", hasta).resultList.map {
            val r = it as Array<*>
            DiaConsultas(fecha(r[0])!!, n(r[1]), n(r[2]), n(r[3]), n(r[4]))
        }

    /** Criticas sin la decision «Es homonimo» (de cualquier fecha): cantidad y fecha de la mas antigua. */
    @Transactional(readOnly = true)
    fun criticasSinDecidir(a: AlcanceInicio): Pair<Long, LocalDateTime?> {
        val r = alcanceListas(
            em.createQuery("SELECT COUNT(c), MIN(c.fecha) $baseConsultas AND c.clasificacion = 'CRITICA' AND c.homonimo = false"), a
        ).singleResult as Array<*>
        return n(r[0]) to (r[1] as LocalDateTime?)
    }

    // ------------------------------------------------------------------ Registro de operaciones (nativo, como la lista)

    private val desdeRegistros = """
        FROM registro_operaciones_v2 r
        JOIN operaciones_v2 o ON o.id = r.id_operacion
        LEFT JOIN usuarios u ON u.id = r.id_usuario
        LEFT JOIN analisis_operaciones a ON a.registro_id = r.id
    """

    // La empresa de un registro es la de su cuenta duena (si ya no existe, la guardada): igual que ListaOperacionesConsulta.
    private val deLaEmpresa = "LOWER(TRIM(COALESCE(NULLIF(TRIM(u.empresa), ''), r.empresa))) = LOWER(TRIM(:empresa))"

    @Transactional(readOnly = true)
    fun cifrasOperaciones(empresa: String, desde: LocalDate, hasta: LocalDate): CifrasOperaciones {
        val r = em.createNativeQuery(
            """
            SELECT COUNT(*),
                   COALESCE(SUM(COALESCE(o.monto_usd, CASE WHEN UPPER(TRIM(o.moneda)) = 'USD' THEN o.monto END)), 0),
                   COALESCE(SUM(CASE WHEN a.estado IN ('EN_ANALISIS', 'SOSPECHOSA') THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN o.umbral_estado = 'SI_SE_REGISTRA' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN r.fecha_registro = o.fecha_operacion THEN 1 ELSE 0 END), 0)
            $desdeRegistros
            WHERE $deLaEmpresa AND r.fecha_registro BETWEEN :desde AND :hasta
            """
        ).setParameter("empresa", empresa).setParameter("desde", desde).setParameter("hasta", hasta).singleResult as Array<*>
        return CifrasOperaciones(n(r[0]), decimal(r[1]) ?: BigDecimal.ZERO, n(r[2]), n(r[3]), n(r[4]))
    }

    @Transactional(readOnly = true)
    fun ultimosRegistros(empresa: String, cuantos: Int): List<FilaUltimoRegistro> =
        em.createNativeQuery(
            """
            SELECT r.id, r.numero_registro, pf.apellidos, pf.nombres, pr.razon_social, o.tipo_operacion, o.monto, o.moneda,
                   r.id_usuario_responsable, a.estado, r.fecha_envio
            $desdeRegistros
            LEFT JOIN personas_operacion pf ON pf.id = r.id_persona_fisica
            LEFT JOIN personas_operacion pr ON pr.id = r.id_persona_representada
            WHERE $deLaEmpresa
            ORDER BY r.fecha_creacion DESC, r.id DESC
            """
        ).setParameter("empresa", empresa).setMaxResults(cuantos).resultList.map {
            val c = it as Array<*>
            FilaUltimoRegistro(
                n(c[0]), c[1]?.toString(), c[2]?.toString(), c[3]?.toString(), c[4]?.toString(), c[5]?.toString(),
                decimal(c[6]), c[7]?.toString(), c[8] as Number?, c[9]?.toString(), c[10]
            )
        }

    // ------------------------------------------------------------------ Canal de denuncias

    // La empresa de una denuncia es la de su receptor o, si no tiene, la de quien la registro (AutorizacionDenunciaService).
    private val baseDenuncias = """
        FROM DenunciaEntity d LEFT JOIN d.receptorUsuario r LEFT JOIN d.creadoPorUsuario c
        WHERE (:todo = true
               OR (:empresa IS NOT NULL AND LOWER(TRIM(COALESCE(r.empresa, c.empresa))) = LOWER(TRIM(:empresa)))
               OR r.id = :usuarioId)
    """

    private fun alcanceDenuncias(q: Query, a: AlcanceInicio): Query =
        q.setParameter("todo", a.todo).setParameter("empresa", a.empresa).setParameter("usuarioId", a.usuarioId)

    @Transactional(readOnly = true)
    fun recibidas(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): Long =
        (alcanceDenuncias(em.createQuery("SELECT COUNT(d) $baseDenuncias AND d.fechaCreacion >= :desde AND d.fechaCreacion < :hasta"), a)
            .setParameter("desde", desde).setParameter("hasta", hasta).singleResult as Number).toLong()

    /** Abiertas hoy (de cualquier fecha): por atender, sin abrir y vencidas (creadas antes de `limite`). */
    @Transactional(readOnly = true)
    fun abiertas(a: AlcanceInicio, limite: LocalDateTime): AbiertasDenuncias {
        val r = alcanceDenuncias(
            em.createQuery(
                """
                SELECT COALESCE(SUM(CASE WHEN d.estado <> com.security.modules.denuncias.domain.enums.EstadoDenuncia.CERRADO THEN 1 ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN d.estado = com.security.modules.denuncias.domain.enums.EstadoDenuncia.RECIBIDO THEN 1 ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN d.estado <> com.security.modules.denuncias.domain.enums.EstadoDenuncia.CERRADO
                                          AND d.fechaCreacion < :limite THEN 1 ELSE 0 END), 0)
                $baseDenuncias
                """
            ), a
        ).setParameter("limite", limite).singleResult as Array<*>
        return AbiertasDenuncias(n(r[0]), n(r[1]), n(r[2]))
    }

    @Transactional(readOnly = true)
    fun porCausa(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): List<Pair<CausaDenuncia, Long>> =
        alcanceDenuncias(
            em.createQuery("SELECT d.causa, COUNT(d) $baseDenuncias AND d.fechaCreacion >= :desde AND d.fechaCreacion < :hasta GROUP BY d.causa"), a
        ).setParameter("desde", desde).setParameter("hasta", hasta).resultList.map {
            val r = it as Array<*>
            (r[0] as CausaDenuncia) to n(r[1])
        }

    /** Fecha de creacion y de cierre de las denuncias cerradas en el periodo (solo dos columnas). */
    @Transactional(readOnly = true)
    fun cerradas(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): List<Pair<LocalDateTime, LocalDateTime>> =
        alcanceDenuncias(
            em.createQuery(
                """
                SELECT d.fechaCreacion, d.fechaCierre $baseDenuncias
                AND d.estado = com.security.modules.denuncias.domain.enums.EstadoDenuncia.CERRADO
                AND d.fechaCierre >= :desde AND d.fechaCierre < :hasta
                """
            ), a
        ).setParameter("desde", desde).setParameter("hasta", hasta).resultList.map {
            val r = it as Array<*>
            (r[0] as LocalDateTime) to (r[1] as LocalDateTime)
        }

    /** Cerradas en el periodo con resultado, por resultado (las que no tienen resultado no entran). */
    @Transactional(readOnly = true)
    fun cierresPorResultado(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): Map<ResultadoCierreDenuncia, Long> =
        alcanceDenuncias(
            em.createQuery(
                """
                SELECT d.resultadoCierre, COUNT(d) $baseDenuncias
                AND d.estado = com.security.modules.denuncias.domain.enums.EstadoDenuncia.CERRADO
                AND d.resultadoCierre IS NOT NULL AND d.fechaCierre >= :desde AND d.fechaCierre < :hasta
                GROUP BY d.resultadoCierre
                """
            ), a
        ).setParameter("desde", desde).setParameter("hasta", hasta).resultList.associate {
            val r = it as Array<*>
            (r[0] as ResultadoCierreDenuncia) to n(r[1])
        }

    /** Derivadas al oficial en el periodo (solo la cantidad: el detalle es reservado). */
    @Transactional(readOnly = true)
    fun derivadas(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): Long =
        (alcanceDenuncias(
            em.createQuery("SELECT COUNT(d) $baseDenuncias AND d.derivadaAlOficial = true AND d.fechaDerivacion >= :desde AND d.fechaDerivacion < :hasta"), a
        ).setParameter("desde", desde).setParameter("hasta", hasta).singleResult as Number).toLong()

    // ------------------------------------------------------------------ Cabina: scoring (solo version 2)

    // Visibilidad del scoring: la empresa ACTUAL de la cuenta que evaluo (scoring_riesgo no guarda empresa), como el historial.
    private val baseScoring = """
        FROM ScoringRiesgo s JOIN s.usuario u
        WHERE s.version = 2
          AND (:todo = true OR (:empresa IS NOT NULL AND LOWER(TRIM(u.empresa)) = LOWER(TRIM(:empresa))) OR u.id = :usuarioId)
          AND s.fechaCreacion >= :desde AND s.fechaCreacion < :hasta
    """

    private fun alcanceScoring(q: Query, a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): Query =
        q.setParameter("todo", a.todo).setParameter("empresa", a.empresa).setParameter("usuarioId", a.usuarioId)
            .setParameter("desde", desde).setParameter("hasta", hasta)

    /** [nivel, cantidad, suma de puntajes] del periodo. */
    @Transactional(readOnly = true)
    fun scoringPorNivel(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): List<Triple<String?, Long, BigDecimal>> =
        alcanceScoring(em.createQuery("SELECT s.nivel, COUNT(s), SUM(s.puntaje) $baseScoring GROUP BY s.nivel"), a, desde, hasta)
            .resultList.map {
                val r = it as Array<*>
                Triple(r[0] as String?, n(r[1]), decimal(r[2]) ?: BigDecimal.ZERO)
            }

    /** El resultado completo (JSON) de cada evaluacion del periodo: de ahi salen los 8 factores del radar. */
    @Transactional(readOnly = true)
    fun scoringResultados(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): List<String> =
        alcanceScoring(em.createQuery("SELECT s.resultado $baseScoring AND s.resultado IS NOT NULL"), a, desde, hasta)
            .resultList.mapNotNull { it as String? }

    @Transactional(readOnly = true)
    fun scoringPorDia(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): Map<LocalDate, Long> =
        alcanceScoring(
            em.createQuery("SELECT CAST(s.fechaCreacion AS LocalDate), COUNT(s) $baseScoring GROUP BY CAST(s.fechaCreacion AS LocalDate)"),
            a, desde, hasta
        ).resultList.associate { val r = it as Array<*>; fecha(r[0])!! to n(r[1]) }

    /** [departamento de residencia, evaluaciones, altas] del periodo. */
    @Transactional(readOnly = true)
    fun scoringPorDepartamento(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): List<Triple<String, Long, Long>> =
        alcanceScoring(
            em.createQuery(
                "SELECT s.departamento.nombre, COUNT(s), SUM(CASE WHEN s.nivel = 'ALTO' THEN 1 ELSE 0 END) $baseScoring GROUP BY s.departamento.nombre"
            ), a, desde, hasta
        ).resultList.map { val r = it as Array<*>; Triple(r[0].toString(), n(r[1]), n(r[2])) }

    // ------------------------------------------------------------------ Cabina: registros, denuncias y catalogos

    @Transactional(readOnly = true)
    fun registrosPorDia(empresa: String, desde: LocalDate, hasta: LocalDate): Map<LocalDate, Long> =
        em.createNativeQuery(
            "SELECT r.fecha_registro, COUNT(*) $desdeRegistros WHERE $deLaEmpresa AND r.fecha_registro BETWEEN :desde AND :hasta GROUP BY r.fecha_registro"
        ).setParameter("empresa", empresa).setParameter("desde", desde).setParameter("hasta", hasta).resultList
            .associate { val r = it as Array<*>; fecha(r[0])!! to n(r[1]) }

    /** [departamento, provincia, registros, monto en US$] del periodo, tal como estan en la persona que realiza. */
    @Transactional(readOnly = true)
    fun registrosPorUbicacion(empresa: String, desde: LocalDate, hasta: LocalDate): List<UbicacionRegistros> =
        em.createNativeQuery(
            """
            SELECT pf.departamento, pf.provincia, COUNT(*),
                   COALESCE(SUM(COALESCE(o.monto_usd, CASE WHEN UPPER(TRIM(o.moneda)) = 'USD' THEN o.monto END)), 0)
            $desdeRegistros
            LEFT JOIN personas_operacion pf ON pf.id = r.id_persona_fisica
            WHERE $deLaEmpresa AND r.fecha_registro BETWEEN :desde AND :hasta
            GROUP BY pf.departamento, pf.provincia
            """
        ).setParameter("empresa", empresa).setParameter("desde", desde).setParameter("hasta", hasta).resultList.map {
            val r = it as Array<*>
            UbicacionRegistros(r[0]?.toString(), r[1]?.toString(), n(r[2]), decimal(r[3]) ?: BigDecimal.ZERO)
        }

    /** Registros con fecha de operacion en [desde, hasta] aun sin constancia de envio. */
    @Transactional(readOnly = true)
    fun pendientesDeEnvio(empresa: String, desde: LocalDate, hasta: LocalDate): Long =
        n(
            em.createNativeQuery(
                "SELECT COUNT(*) $desdeRegistros WHERE $deLaEmpresa AND o.fecha_operacion BETWEEN :desde AND :hasta AND r.fecha_envio IS NULL"
            ).setParameter("empresa", empresa).setParameter("desde", desde).setParameter("hasta", hasta).singleResult
        )

    /** Provincias del catalogo con su departamento: [id, provincia, departamento]. */
    @Transactional(readOnly = true)
    fun provincias(): List<Triple<Int, String, String>> =
        em.createQuery("SELECT p.id, p.nombre, d.nombre FROM Provincia p JOIN p.departamento d").resultList.map {
            val r = it as Array<*>
            Triple((r[0] as Number).toInt(), r[1].toString(), r[2].toString())
        }

    @Transactional(readOnly = true)
    fun departamentos(): Map<Int, String> =
        em.createQuery("SELECT d.id, d.nombre FROM ScoringDepartamento d").resultList
            .associate { val r = it as Array<*>; (r[0] as Number).toInt() to r[1].toString() }

    @Transactional(readOnly = true)
    fun denunciasPorDia(a: AlcanceInicio, desde: LocalDateTime, hasta: LocalDateTime): Map<LocalDate, Long> =
        alcanceDenuncias(
            em.createQuery(
                "SELECT CAST(d.fechaCreacion AS LocalDate), COUNT(d) $baseDenuncias AND d.fechaCreacion >= :desde AND d.fechaCreacion < :hasta GROUP BY CAST(d.fechaCreacion AS LocalDate)"
            ), a
        ).setParameter("desde", desde).setParameter("hasta", hasta).resultList
            .associate { val r = it as Array<*>; fecha(r[0])!! to n(r[1]) }

    /** Las abiertas mas antiguas (las primeras en vencer): [id, codigo de seguimiento, creacion]. Nada del denunciante. */
    @Transactional(readOnly = true)
    fun denunciasAbiertasMasAntiguas(a: AlcanceInicio, cuantas: Int): List<Triple<Int, String?, LocalDateTime>> =
        alcanceDenuncias(
            em.createQuery(
                """
                SELECT d.id, d.codigoSeguimiento, d.fechaCreacion $baseDenuncias
                AND d.estado <> com.security.modules.denuncias.domain.enums.EstadoDenuncia.CERRADO
                ORDER BY d.fechaCreacion ASC, d.id ASC
                """
            ), a
        ).setMaxResults(cuantas).resultList.map {
            val r = it as Array<*>
            Triple((r[0] as Number).toInt(), r[1] as String?, r[2] as LocalDateTime)
        }

    // ------------------------------------------------------------------ piezas

    private fun n(v: Any?): Long = (v as? Number)?.toLong() ?: 0L

    private fun decimal(v: Any?): BigDecimal? = when (v) {
        null -> null
        is BigDecimal -> v
        else -> BigDecimal(v.toString())
    }

    private fun fecha(v: Any?): LocalDate? = when (v) {
        null -> null
        is LocalDate -> v
        is java.sql.Date -> v.toLocalDate()
        is LocalDateTime -> v.toLocalDate()
        else -> LocalDate.parse(v.toString().take(10))
    }
}
