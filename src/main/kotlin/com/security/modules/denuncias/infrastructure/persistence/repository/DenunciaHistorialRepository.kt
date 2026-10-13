package com.security.modules.denuncias.infrastructure.persistence.repository

import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaHistorialEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DenunciaHistorialRepository :
    JpaRepository<DenunciaHistorialEntity, Int> {

    fun findAllByDenuncia_IdOrderByFechaCreacionAsc(
        denunciaId: Int
    ): List<DenunciaHistorialEntity>
}