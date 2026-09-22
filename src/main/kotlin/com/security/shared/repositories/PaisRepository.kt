package com.security.shared.datos.repositories

import com.security.shared.datos.entities.Pais
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaisRepository : JpaRepository<Pais, Int> {
    fun findByNombre(nombre: String): Pais?
}
