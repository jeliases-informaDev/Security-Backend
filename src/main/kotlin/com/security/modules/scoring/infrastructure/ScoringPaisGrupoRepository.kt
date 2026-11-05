package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringPaisGrupo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScoringPaisGrupoRepository : JpaRepository<ScoringPaisGrupo, Int> {

    @Query("SELECT g FROM ScoringPaisGrupo g JOIN FETCH g.pais")
    fun todosConPais(): List<ScoringPaisGrupo>

    @Query("SELECT g FROM ScoringPaisGrupo g JOIN FETCH g.pais p WHERE p.id = :paisId")
    fun buscarPorPais(@Param("paisId") paisId: Int): ScoringPaisGrupo?
}
