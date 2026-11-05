package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringOpcion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ScoringOpcionRepository : JpaRepository<ScoringOpcion, Int> {
    fun findByTipo(tipo: String): List<ScoringOpcion>
}
