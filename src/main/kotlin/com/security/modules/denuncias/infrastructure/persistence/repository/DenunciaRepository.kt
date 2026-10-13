package com.security.modules.denuncias.infrastructure.persistence.repository

import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import org.springframework.data.jpa.repository.JpaRepository

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