package com.security.modules.denuncias.infrastructure.persistence.repository

import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DenunciaRepository :
    JpaRepository<DenunciaEntity, Int> {

    // =========================================================
    // ADMINISTRADOR
    // =========================================================

    fun findAllByOrderByFechaCreacionDesc():
            List<DenunciaEntity>

    fun findAllByEstadoOrderByFechaCreacionDesc(
        estado: EstadoDenuncia
    ): List<DenunciaEntity>


    // =========================================================
    // ADMINISTRADOR DE EMPRESA: la empresa de una denuncia es la de su receptor
    // o, si no tiene, la de quien la registro
    // =========================================================

    @Query("""
        SELECT d FROM DenunciaEntity d
        LEFT JOIN d.receptorUsuario r
        LEFT JOIN d.creadoPorUsuario c
        WHERE LOWER(TRIM(COALESCE(r.empresa, c.empresa))) = LOWER(TRIM(:empresa))
        ORDER BY d.fechaCreacion DESC
    """)
    fun buscarPorEmpresa(
        @Param("empresa") empresa: String
    ): List<DenunciaEntity>

    @Query("""
        SELECT d FROM DenunciaEntity d
        LEFT JOIN d.receptorUsuario r
        LEFT JOIN d.creadoPorUsuario c
        WHERE LOWER(TRIM(COALESCE(r.empresa, c.empresa))) = LOWER(TRIM(:empresa))
          AND d.estado = :estado
        ORDER BY d.fechaCreacion DESC
    """)
    fun buscarPorEmpresaYEstado(
        @Param("empresa") empresa: String,
        @Param("estado") estado: EstadoDenuncia
    ): List<DenunciaEntity>

    // =========================================================
    // SUPERVISOR / RECEPTOR
    // =========================================================

    fun findAllByReceptorUsuario_IdOrderByFechaCreacionDesc(
        receptorUsuarioId: Int
    ): List<DenunciaEntity>

    fun findAllByReceptorUsuario_IdAndEstadoOrderByFechaCreacionDesc(
        receptorUsuarioId: Int,
        estado: EstadoDenuncia
    ): List<DenunciaEntity>


    // =========================================================
    // SEGUIMIENTO
    // =========================================================

    fun existsByCodigoSeguimiento(
        codigoSeguimiento: String
    ): Boolean

    fun findByCodigoSeguimiento(
        codigoSeguimiento: String
    ): DenunciaEntity?
}