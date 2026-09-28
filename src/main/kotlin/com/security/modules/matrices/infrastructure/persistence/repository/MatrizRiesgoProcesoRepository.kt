package com.security.modules.matrices.infrastructure.persistence.repository

import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoProcesoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MatrizRiesgoProcesoRepository :
    JpaRepository<MatrizRiesgoProcesoEntity, Int> {

    fun findAllByUsuario_IdAndActivoTrueOrderByNombreAsc(
        usuarioId: Int
    ): List<MatrizRiesgoProcesoEntity>

    fun existsByUsuario_IdAndNombreIgnoreCase(
        usuarioId: Int,
        nombre: String
    ): Boolean

    fun findByIdAndUsuario_IdAndActivoTrue(
        id: Int,
        usuarioId: Int
    ): MatrizRiesgoProcesoEntity?
}