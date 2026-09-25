package com.security.modules.matrices.infrastructure.persistence.repository

import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAreaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MatrizRiesgoAreaRepository :
    JpaRepository<MatrizRiesgoAreaEntity, Int> {

    fun findAllByUsuarioIdAndActivoTrue(
        usuarioId: Int
    ): List<MatrizRiesgoAreaEntity>

    fun existsByUsuarioIdAndNombreIgnoreCase(
        usuarioId: Int,
        nombre: String
    ): Boolean
}