package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.domain.ScoringSustento
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScoringSustentoRepository : JpaRepository<ScoringSustento, String> {

    @Query("SELECT s FROM ScoringSustento s JOIN FETCH s.usuario WHERE s.id = :id")
    fun buscarConUsuario(@Param("id") id: String): ScoringSustento?
}
