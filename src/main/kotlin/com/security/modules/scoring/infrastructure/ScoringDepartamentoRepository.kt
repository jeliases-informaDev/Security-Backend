package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringDepartamento
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ScoringDepartamentoRepository : JpaRepository<ScoringDepartamento, Int>
