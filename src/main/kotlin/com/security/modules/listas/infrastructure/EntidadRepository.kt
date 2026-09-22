package com.security.modules.listas.infrastructure

import com.security.modules.listas.domain.Entidad
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EntidadRepository : JpaRepository<Entidad, Int> {

    @Query(
        """
        SELECT DISTINCT e FROM Entidad e
        LEFT JOIN FETCH e.personaNatural pn
        LEFT JOIN FETCH e.personaJuridica pj
        LEFT JOIN FETCH e.tipoDocumento
        WHERE (:documento IS NULL OR e.documento = :documento)
        AND (:apellidoPaterno IS NULL OR UPPER(pn.apePat) LIKE UPPER(CONCAT('%', :apellidoPaterno, '%')))
        AND (:apellidoMaterno IS NULL OR UPPER(pn.apeMat) LIKE UPPER(CONCAT('%', :apellidoMaterno, '%')))
        AND (
            :nombres IS NULL
            OR UPPER(pn.nombre) LIKE UPPER(CONCAT('%', :nombres, '%'))
            OR UPPER(pn.segundoNombre) LIKE UPPER(CONCAT('%', :nombres, '%'))
            OR UPPER(pj.razonSocial) LIKE UPPER(CONCAT('%', :nombres, '%'))
        )
        """
    )
    fun buscar(
        @Param("documento") documento: String?,
        @Param("nombres") nombres: String?,
        @Param("apellidoPaterno") apellidoPaterno: String?,
        @Param("apellidoMaterno") apellidoMaterno: String?
    ): List<Entidad>

    @Query(
        """
        SELECT e FROM Entidad e
        LEFT JOIN FETCH e.personaNatural
        LEFT JOIN FETCH e.personaJuridica
        LEFT JOIN FETCH e.tipoDocumento
        WHERE e.id = :id
        """
    )
    fun buscarPorIdConDetalle(@Param("id") id: Int): Entidad?
}
