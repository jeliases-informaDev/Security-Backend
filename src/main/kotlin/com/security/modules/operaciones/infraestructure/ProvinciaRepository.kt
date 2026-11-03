package com.security.modules.operaciones.infraestructure

import com.security.modules.operaciones.domain.Provincia
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProvinciaRepository : JpaRepository<Provincia, Int>
