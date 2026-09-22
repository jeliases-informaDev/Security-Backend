package com.security.shared.datos.repositories

import com.security.shared.datos.entities.Roles
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RolesRepository : JpaRepository<Roles, Int> {
    fun findByCodigo(codigo: String): Roles?
}
