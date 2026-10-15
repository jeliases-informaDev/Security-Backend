package com.security.modules.operaciones.infraestructure

import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OperacionRepository : JpaRepository<RegistroOperacionesEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE RegistroOperacionesEntity r SET r.idUsuario = :usuarioNuevoId
        WHERE r.id = :id AND r.idUsuario = :usuarioAnteriorId
    """)
    fun cambiarUsuario(
        @Param("id") id: Long,
        @Param("usuarioAnteriorId") usuarioAnteriorId: Int,
        @Param("usuarioNuevoId") usuarioNuevoId: Int
    ): Int

    @Query(value = "SELECT usuario FROM usuarios WHERE id = :id", nativeQuery = true)
    fun buscarNombreUsuarioPorId(@Param("id") id: Int): String?

    // 1. Consulta para la lista paginada de resumen
    @Query(
        value = """
            SELECT 
                r.id AS id,
                r.numero_registro AS numeroRegistro,
                NULLIF(CONCAT_WS(', ', NULLIF(pf.apellidos, ''), NULLIF(pf.nombres, '')), '') AS personaFisica,
                NULLIF(CONCAT_WS(', ', NULLIF(pr.apellidos, ''), NULLIF(pr.nombres, '')), '') AS personaNombre,
                r.oficina AS oficina,
                r.fecha_registro AS fecha
            FROM registro_operaciones_v2 r
            JOIN usuarios u ON u.id = r.id_usuario
            LEFT JOIN personas_operacion pf ON pf.id = r.id_persona_fisica
            LEFT JOIN personas_operacion pr ON pr.id = r.id_persona_representada
            WHERE u.empresa = :nombreEmpresa
            ORDER BY r.fecha_registro DESC, r.id DESC
            """,
                countQuery = """
            SELECT COUNT(r.id)
            FROM registro_operaciones_v2 r
            JOIN usuarios u ON u.id = r.id_usuario
            WHERE u.empresa = :nombreEmpresa
    """,
        nativeQuery = true
    )
    fun listarResumenPorEmpresa(
        @Param("nombreEmpresa") nombreEmpresa: String,
        pageable: Pageable
    ): Page<OperacionResumenProjection>

    // 2. Consulta para obtener la información del usuario por ID
    @Query(
        value = """
    SELECT 
        u.id AS id,
        u.usuario AS usuario,
        u.empresa AS empresa
    FROM usuarios u
    WHERE u.usuario = :usuario
    """,
        nativeQuery = true
    )
    fun buscarPorUsuario(@Param("usuario") usuario: String): UsuarioProjection?



    // 2. Consulta para obtener el resumen de una operación por ID
    @Query(
        value = """
            SELECT
                r.id AS id,
                r.numero_registro AS numeroRegistro,
                NULLIF(CONCAT_WS(', ',
                    NULLIF(pf.apellidos, ''),
                    NULLIF(pf.nombres, '')
                ), '') AS personaFisica,
                NULLIF(CONCAT_WS(', ',
                    NULLIF(pr.apellidos, ''),
                    NULLIF(pr.nombres, '')
                ), '') AS personaNombre,
                r.oficina AS oficina,
                r.fecha_registro AS fecha
            FROM registro_operaciones_v2 r
            JOIN usuarios u ON u.id = r.id_usuario
            LEFT JOIN personas_operacion pf
                ON pf.id = r.id_persona_fisica
            LEFT JOIN personas_operacion pr
                ON pr.id = r.id_persona_representada
            WHERE u.empresa = :nombreEmpresa
            ORDER BY r.fecha_registro DESC, r.id DESC
        """,
        nativeQuery = true
    )
    fun buscarResumenPorEmpresa(
        @Param("nombreEmpresa") nombreEmpresa: String
    ): List<OperacionResumenProjection>
}
