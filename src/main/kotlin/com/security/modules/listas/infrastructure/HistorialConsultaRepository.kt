package com.security.modules.listas.infrastructure

import com.security.modules.listas.domain.HistorialConsulta
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface HistorialConsultaRepository : JpaRepository<HistorialConsulta, Int> {

    @Query(
        """
        SELECT hc FROM HistorialConsulta hc
        JOIN FETCH hc.entidad e
        LEFT JOIN FETCH e.personaNatural
        LEFT JOIN FETCH e.personaJuridica
        LEFT JOIN FETCH e.tipoDocumento
        WHERE hc.usuario.id = :usuarioId
        ORDER BY hc.fechaConsulta DESC
        """
    )
    fun buscarPorUsuario(@Param("usuarioId") usuarioId: Int, pageable: Pageable): Page<HistorialConsulta>

    // Consultas de todas las cuentas de una empresa (administrador de empresa)
    @Query(
        """
        SELECT hc FROM HistorialConsulta hc
        JOIN FETCH hc.entidad e
        LEFT JOIN FETCH e.personaNatural
        LEFT JOIN FETCH e.personaJuridica
        LEFT JOIN FETCH e.tipoDocumento
        WHERE LOWER(TRIM(hc.usuario.empresa)) = LOWER(TRIM(:empresa))
        ORDER BY hc.fechaConsulta DESC
        """
    )
    fun buscarPorEmpresa(@Param("empresa") empresa: String, pageable: Pageable): Page<HistorialConsulta>

    // Todas (administrador universal)
    @Query(
        """
        SELECT hc FROM HistorialConsulta hc
        JOIN FETCH hc.entidad e
        LEFT JOIN FETCH e.personaNatural
        LEFT JOIN FETCH e.personaJuridica
        LEFT JOIN FETCH e.tipoDocumento
        ORDER BY hc.fechaConsulta DESC
        """
    )
    fun buscarTodas(pageable: Pageable): Page<HistorialConsulta>
}
