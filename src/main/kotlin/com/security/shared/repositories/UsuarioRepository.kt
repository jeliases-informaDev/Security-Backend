package com.security.shared.datos.repositories

import com.security.shared.datos.entities.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UsuarioRepository : JpaRepository<Usuario, Int> {

    @Query("""
    SELECT DISTINCT u
    FROM Usuario u
    JOIN FETCH u.roles ur
    JOIN FETCH ur.rol r
    WHERE u.usuario = :usuario
""")
    fun findByUsuario(@Param("usuario") usuario: String): Usuario?
}