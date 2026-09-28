package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringRiesgo
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScoringRiesgoRepository : JpaRepository<ScoringRiesgo, Int> {

    @Query(
        """
        SELECT sr FROM ScoringRiesgo sr
        JOIN FETCH sr.entidad e
        LEFT JOIN FETCH e.personaNatural
        LEFT JOIN FETCH e.personaJuridica
        JOIN FETCH sr.ocupacion
        JOIN FETCH sr.departamento
        WHERE sr.usuario.id = :usuarioId
        ORDER BY sr.fechaCreacion DESC
        """
    )
    fun buscarPorUsuario(@Param("usuarioId") usuarioId: Int, pageable: Pageable): Page<ScoringRiesgo>
}
