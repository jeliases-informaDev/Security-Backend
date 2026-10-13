package com.security.modules.denuncias.infrastructure.persistence.repository

import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaArchivoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DenunciaArchivoRepository :
    JpaRepository<DenunciaArchivoEntity, Int> {

    fun findAllByDenuncia_IdOrderByFechaCreacionAsc(
        denunciaId: Int
    ): List<DenunciaArchivoEntity>

    fun countByDenuncia_Id(
        denunciaId: Int
    ): Long

    fun findByIdAndDenuncia_Id(
        id: Int,
        denunciaId: Int
    ): DenunciaArchivoEntity?
}