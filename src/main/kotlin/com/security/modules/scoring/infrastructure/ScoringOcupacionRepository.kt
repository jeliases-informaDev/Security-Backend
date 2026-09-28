package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringOcupacion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ScoringOcupacionRepository : JpaRepository<ScoringOcupacion, Int>
