package com.security.modules.matrices.infrastructure.persistence.repository

import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAnalisisEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MatrizRiesgoAnalisisRepository :
    JpaRepository<MatrizRiesgoAnalisisEntity, Int> {

    fun findAllByUsuario_IdOrderByFechaCreacionDesc(
        usuarioId: Int
    ): List<MatrizRiesgoAnalisisEntity>

    fun findAllByUsuario_IdAndEstadoOrderByFechaCreacionDesc(
        usuarioId: Int,
        estado: String
    ): List<MatrizRiesgoAnalisisEntity>

    fun findByIdAndUsuario_Id(
        id: Int,
        usuarioId: Int
    ): MatrizRiesgoAnalisisEntity?
}