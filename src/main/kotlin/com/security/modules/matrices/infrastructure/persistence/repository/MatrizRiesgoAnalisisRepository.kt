package com.security.modules.matrices.infrastructure.persistence.repository

import com.security.modules.matrices.domain.enums.EstadoAnalisis
import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAnalisisEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MatrizRiesgoAnalisisRepository :
    JpaRepository<MatrizRiesgoAnalisisEntity, Int> {

    fun findAllByUsuario_IdOrderByFechaCreacionDesc(
        usuarioId: Int
    ): List<MatrizRiesgoAnalisisEntity>

    fun findAllByUsuario_IdAndEstadoOrderByFechaCreacionDesc(
        usuarioId: Int,
        estado: EstadoAnalisis
    ): List<MatrizRiesgoAnalisisEntity>

    fun findByIdAndUsuario_Id(
        id: Int,
        usuarioId: Int
    ): MatrizRiesgoAnalisisEntity?

    // Analisis de todas las cuentas de una empresa (administrador de empresa)
    @Query("""
        SELECT a FROM MatrizRiesgoAnalisisEntity a
        WHERE LOWER(TRIM(a.usuario.empresa)) = LOWER(TRIM(:empresa))
        ORDER BY a.fechaCreacion DESC
    """)
    fun buscarPorEmpresa(
        @Param("empresa") empresa: String
    ): List<MatrizRiesgoAnalisisEntity>

    // Todos (administrador universal)
    fun findAllByOrderByFechaCreacionDesc(): List<MatrizRiesgoAnalisisEntity>
}