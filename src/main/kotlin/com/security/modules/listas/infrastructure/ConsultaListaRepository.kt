package com.security.modules.listas.infrastructure

import com.security.modules.listas.domain.ConsultaLista
import com.security.modules.listas.domain.ConsultaListaDecision
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

// Hasta donde ve la cuenta (VisibilidadEmpresaService): todo, su empresa (la guardada en la consulta) o lo propio.
internal const val ALCANCE_CONSULTAS = """
    (:todo = true
     OR (:empresa IS NOT NULL AND LOWER(TRIM(c.empresa)) = LOWER(TRIM(:empresa)))
     OR c.usuario.id = :usuarioId)
"""

internal const val FILTRO_CONSULTAS = """
    c.estado = 'RESUELTA'
    AND (:filtro = 'TODAS'
         OR (:filtro = 'CON_COINCIDENCIAS' AND c.clasificacion <> 'SIN_COINCIDENCIAS')
         OR (:filtro = 'SIN_COINCIDENCIAS' AND c.clasificacion = 'SIN_COINCIDENCIAS')
         OR (:filtro = 'CRITICAS' AND c.clasificacion = 'CRITICA'))
    AND (:q IS NULL
         OR LOWER(c.consultadoNombre) LIKE :q
         OR LOWER(c.consultadoDocumento) LIKE :q
         OR LOWER(c.criterioTexto) LIKE :q)
"""

@Repository
interface ConsultaListaRepository : JpaRepository<ConsultaLista, Long> {

    /** Historial: q ya viene en minusculas y con los % ("%garcia%"), o null. Mas reciente primero. */
    @Query(
        value = "SELECT c FROM ConsultaLista c WHERE $FILTRO_CONSULTAS AND $ALCANCE_CONSULTAS ORDER BY c.fecha DESC, c.id DESC",
        countQuery = "SELECT COUNT(c) FROM ConsultaLista c WHERE $FILTRO_CONSULTAS AND $ALCANCE_CONSULTAS"
    )
    fun historial(
        @Param("todo") todo: Boolean,
        @Param("empresa") empresa: String?,
        @Param("usuarioId") usuarioId: Int?,
        @Param("filtro") filtro: String,
        @Param("q") q: String?,
        pageable: Pageable
    ): Page<ConsultaLista>

    /** Cifras de un periodo: [clasificacion, cantidad, cuantas marcadas homonimo]. */
    @Query(
        """
        SELECT c.clasificacion, COUNT(c), SUM(CASE WHEN c.homonimo = true THEN 1 ELSE 0 END)
        FROM ConsultaLista c
        WHERE c.estado = 'RESUELTA' AND c.fecha >= :desde AND c.fecha < :hasta AND $ALCANCE_CONSULTAS
        GROUP BY c.clasificacion
        """
    )
    fun cifras(
        @Param("todo") todo: Boolean,
        @Param("empresa") empresa: String?,
        @Param("usuarioId") usuarioId: Int?,
        @Param("desde") desde: LocalDateTime,
        @Param("hasta") hasta: LocalDateTime
    ): List<Array<Any?>>

    fun findFirstByIdBusquedaAndIdEntidadOrderByIdAsc(idBusqueda: Long, idEntidad: Int): ConsultaLista?

    fun findByIdBusqueda(idBusqueda: Long): List<ConsultaLista>

    /**
     * Las consultas de esa persona marcadas "Es homonimo" por la empresa de la cuenta (o por la propia cuenta si no
     * tiene empresa), la mas reciente primero. Es lo que respeta el scoring.
     */
    @Query(
        """
        SELECT c FROM ConsultaLista c
        WHERE c.estado = 'RESUELTA' AND c.idEntidad = :entidadId AND c.homonimo = true
        AND ((:empresa IS NOT NULL AND LOWER(TRIM(c.empresa)) = LOWER(TRIM(:empresa))) OR c.usuario.id = :usuarioId)
        ORDER BY c.homonimoFecha DESC, c.id DESC
        """
    )
    fun homonimosDe(
        @Param("entidadId") entidadId: Int,
        @Param("empresa") empresa: String?,
        @Param("usuarioId") usuarioId: Int?,
        pageable: Pageable
    ): List<ConsultaLista>
}

@Repository
interface ConsultaListaDecisionRepository : JpaRepository<ConsultaListaDecision, Long> {
    fun findByIdConsultaOrderByFechaAscIdAsc(idConsulta: Long): List<ConsultaListaDecision>
}
