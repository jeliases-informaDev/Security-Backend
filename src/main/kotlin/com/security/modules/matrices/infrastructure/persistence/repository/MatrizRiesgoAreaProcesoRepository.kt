package com.security.modules.matrices.infrastructure.persistence.repository

import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAreaProcesoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MatrizRiesgoAreaProcesoRepository :
    JpaRepository<MatrizRiesgoAreaProcesoEntity, Int> {

    fun findAllByArea_Id(
        areaId: Int
    ): List<MatrizRiesgoAreaProcesoEntity>

    fun findAllByProceso_Id(
        procesoId: Int
    ): List<MatrizRiesgoAreaProcesoEntity>

    fun existsByArea_IdAndProceso_Id(
        areaId: Int,
        procesoId: Int
    ): Boolean
}
