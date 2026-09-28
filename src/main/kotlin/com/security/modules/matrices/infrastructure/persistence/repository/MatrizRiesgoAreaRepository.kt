package com.security.modules.matrices.infrastructure.persistence.repository

import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAreaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MatrizRiesgoAreaRepository :
    JpaRepository<MatrizRiesgoAreaEntity, Int> {

    fun findAllByUsuario_IdAndActivoTrueOrderByNombreAsc(
        usuarioId: Int
    ): List<MatrizRiesgoAreaEntity>

    fun existsByUsuario_IdAndNombreIgnoreCase(
        usuarioId: Int,
        nombre: String
    ): Boolean

    fun findByIdAndUsuario_IdAndActivoTrue(
        id: Int,
        usuarioId: Int
    ): MatrizRiesgoAreaEntity?
}