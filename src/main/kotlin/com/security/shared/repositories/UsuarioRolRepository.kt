package com.security.shared.datos.repositories

import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.entities.UsuarioRolId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UsuarioRolRepository : JpaRepository<UsuarioRol, UsuarioRolId> {

    @Modifying
    @Query("DELETE FROM UsuarioRol ur WHERE ur.usuario.id = :usuarioId")
    fun deleteByUsuarioId(@Param("usuarioId") usuarioId: Int)
}
