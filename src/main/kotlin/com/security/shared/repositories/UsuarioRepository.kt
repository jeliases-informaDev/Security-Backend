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

    @Query("""
        SELECT u FROM Usuario u
        WHERE LOWER(TRIM(u.empresa)) = LOWER(TRIM(:empresa))
        ORDER BY u.nombres, u.apePat, u.id
    """)
    fun findAllByEmpresaOrderByNombresAscApePatAscIdAsc(@Param("empresa") empresa: String): List<Usuario>

    fun findAllByEmpresaAndActivoTrueOrderByNombresAscApePatAsc(empresa: String): List<Usuario>

    // Nombres de empresa escritos en las cuentas, sin espacios sobrantes (para armar la lista de empresas).
    @Query("""
        SELECT DISTINCT TRIM(u.empresa)
        FROM Usuario u
        WHERE u.empresa IS NOT NULL AND TRIM(u.empresa) <> ''
    """)
    fun findEmpresasEnUso(): List<String>

    // Cuantas cuentas tiene cada empresa; el nombre llega en minusculas y sin espacios sobrantes.
    @Query("""
        SELECT LOWER(TRIM(u.empresa)) AS nombre, COUNT(u) AS cuentas
        FROM Usuario u
        WHERE u.empresa IS NOT NULL AND TRIM(u.empresa) <> ''
        GROUP BY LOWER(TRIM(u.empresa))
    """)
    fun contarCuentasPorEmpresa(): List<CuentasPorEmpresa>

    fun findByCorreo(
        correo: String
    ): Usuario?

    // Para entrar con el correo (es unico entre empresas); trae los roles como findByUsuario
    @Query("""
        SELECT DISTINCT u
        FROM Usuario u
        JOIN FETCH u.roles ur
        JOIN FETCH ur.rol r
        WHERE LOWER(u.correo) = LOWER(:correo)
    """)
    fun findByCorreoConRoles(
        @Param("correo") correo: String
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
/** Fila de contarCuentasPorEmpresa: el nombre va en minusculas y sin espacios sobrantes. */
interface CuentasPorEmpresa {
    val nombre: String
    val cuentas: Long
}
