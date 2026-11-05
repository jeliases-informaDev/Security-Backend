package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringRiesgo
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

// La entidad es opcional (version 2 con un cliente que no esta en la base de listas): LEFT JOIN.
@Repository
interface ScoringRiesgoRepository : JpaRepository<ScoringRiesgo, Int> {

    @Query(
        value = """
        SELECT sr FROM ScoringRiesgo sr
        LEFT JOIN FETCH sr.entidad e
        LEFT JOIN FETCH e.personaNatural
        LEFT JOIN FETCH e.personaJuridica
        JOIN FETCH sr.ocupacion
        JOIN FETCH sr.departamento
        WHERE sr.usuario.id = :usuarioId
        ORDER BY sr.fechaCreacion DESC, sr.id DESC
        """,
        countQuery = "SELECT COUNT(sr) FROM ScoringRiesgo sr WHERE sr.usuario.id = :usuarioId"
    )
    fun buscarPorUsuario(@Param("usuarioId") usuarioId: Int, pageable: Pageable): Page<ScoringRiesgo>

    // Todas las evaluaciones de las cuentas de una empresa (administrador de empresa)
    @Query(
        value = """
        SELECT sr FROM ScoringRiesgo sr
        LEFT JOIN FETCH sr.entidad e
        LEFT JOIN FETCH e.personaNatural
        LEFT JOIN FETCH e.personaJuridica
        JOIN FETCH sr.ocupacion
        JOIN FETCH sr.departamento
        WHERE LOWER(TRIM(sr.usuario.empresa)) = LOWER(TRIM(:empresa))
        ORDER BY sr.fechaCreacion DESC, sr.id DESC
        """,
        countQuery = "SELECT COUNT(sr) FROM ScoringRiesgo sr WHERE LOWER(TRIM(sr.usuario.empresa)) = LOWER(TRIM(:empresa))"
    )
    fun buscarPorEmpresa(@Param("empresa") empresa: String, pageable: Pageable): Page<ScoringRiesgo>

    // Todas (administrador universal)
    @Query(
        value = """
        SELECT sr FROM ScoringRiesgo sr
        LEFT JOIN FETCH sr.entidad e
        LEFT JOIN FETCH e.personaNatural
        LEFT JOIN FETCH e.personaJuridica
        JOIN FETCH sr.ocupacion
        JOIN FETCH sr.departamento
        ORDER BY sr.fechaCreacion DESC, sr.id DESC
        """,
        countQuery = "SELECT COUNT(sr) FROM ScoringRiesgo sr"
    )
    fun buscarTodas(pageable: Pageable): Page<ScoringRiesgo>

    // Una evaluacion con quien la hizo (para decidir si quien pide la puede ver)
    @Query(
        """
        SELECT sr FROM ScoringRiesgo sr
        JOIN FETCH sr.usuario
        LEFT JOIN FETCH sr.entidad e
        LEFT JOIN FETCH e.personaNatural
        LEFT JOIN FETCH e.personaJuridica
        LEFT JOIN FETCH e.tipoDocumento
        JOIN FETCH sr.ocupacion
        JOIN FETCH sr.departamento
        WHERE sr.id = :id
        """
    )
    fun buscarPorIdConDetalle(@Param("id") id: Int): ScoringRiesgo?

    // Guarda la decision solo si todavia no hay una: dos clics a la vez no pueden decidir dos veces.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE ScoringRiesgo sr
        SET sr.decisionAccion = :accion, sr.decisionComentario = :comentario, sr.decisionPor = :por, sr.decisionFecha = :fecha
        WHERE sr.id = :id AND sr.decisionAccion IS NULL
        """
    )
    fun registrarDecision(
        @Param("id") id: Int,
        @Param("accion") accion: String,
        @Param("comentario") comentario: String?,
        @Param("por") por: String,
        @Param("fecha") fecha: LocalDateTime
    ): Int
}
