package com.security.modules.operaciones.infraestructure

import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OperacionRepository : JpaRepository<RegistroOperacionesEntity, Long> {

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
        LEFT JOIN personas_operacion pf ON pf.id = r.id_persona_fisica
        LEFT JOIN personas_operacion pr ON pr.id = r.id_persona_representada
        ORDER BY r.fecha_registro DESC, r.id DESC
        """,
        countQuery = """
        SELECT COUNT(r.id)
        FROM registro_operaciones_v2 r
        """,
        nativeQuery = true
    )
    fun listarResumen(pageable: Pageable): Page<OperacionResumenProjection>

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
}
