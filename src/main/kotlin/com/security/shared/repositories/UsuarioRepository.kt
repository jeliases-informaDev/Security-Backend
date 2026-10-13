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
    fun findByUsuario(
        @Param("usuario") usuario: String
    ): Usuario?

    fun findByCorreo(
        correo: String
    ): Usuario?

    fun existsByUsuario(
        usuario: String
    ): Boolean

    fun existsByCorreo(
        correo: String
    ): Boolean

    @Query("""
        SELECT DISTINCT u
        FROM Usuario u
        LEFT JOIN FETCH u.roles ur
        LEFT JOIN FETCH ur.rol r
        ORDER BY u.id
    """)
    fun findAllConRoles(): List<Usuario>

    // =========================================================
    // RECEPTORES PARA CANAL DE DENUNCIAS
    // =========================================================

    @Query("""
        SELECT DISTINCT u
        FROM Usuario u
        JOIN FETCH u.roles ur
        JOIN FETCH ur.rol r
        WHERE u.activo = true
          AND r.activo = true
          AND r.codigo IN :codigos
        ORDER BY u.id
    """)
    fun findAllActivosByRoles(
        @Param("codigos")
        codigos: Collection<String>
    ): List<Usuario>
}