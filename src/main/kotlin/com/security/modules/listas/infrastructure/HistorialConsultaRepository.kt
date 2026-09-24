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
}
