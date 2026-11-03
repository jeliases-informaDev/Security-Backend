package com.security.modules.operaciones.infraestructure

import com.security.modules.operaciones.domain.Moneda
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MonedaRepository : JpaRepository<Moneda, Int>
