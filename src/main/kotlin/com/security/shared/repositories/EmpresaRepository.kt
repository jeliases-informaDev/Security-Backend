package com.security.shared.datos.repositories

import com.security.shared.datos.entities.Empresa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EmpresaRepository : JpaRepository<Empresa, Int> {

    fun findAllByOrderByNombreAsc(): List<Empresa>

    fun findByNombreIgnoreCase(nombre: String): Empresa?

    fun existsByNombreIgnoreCase(nombre: String): Boolean

    fun findByRuc(ruc: String): Empresa?
}
