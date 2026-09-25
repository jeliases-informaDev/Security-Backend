package com.security.modules.matrices.infrastructure.persistence.repository

import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoProcesoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MatrizRiesgoProcesoRepository :
    JpaRepository<MatrizRiesgoProcesoEntity, Int> {

    fun findAllByUsuarioIdAndActivoTrue(
        usuarioId: Int
    ): List<MatrizRiesgoProcesoEntity>

    fun existsByUsuarioIdAndNombreIgnoreCase(
        usuarioId: Int,
        nombre: String
    ): Boolean
}
