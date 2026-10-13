package com.security.modules.denuncias.infrastructure.persistence.repository

import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaInvolucradoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DenunciaInvolucradoRepository :
    JpaRepository<DenunciaInvolucradoEntity, Int> {

    fun findAllByDenuncia_IdOrderByIdAsc(
        denunciaId: Int
    ): List<DenunciaInvolucradoEntity>
}