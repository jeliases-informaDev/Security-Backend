package com.security.modules.listas.infrastructure

import com.security.modules.listas.domain.Entidad
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EntidadRepository : JpaRepository<Entidad, Int> {

    // Busqueda por documento exacto y/o nombres que "contienen" el texto, de a una pagina y ordenada por apellidos.
    // El conteo va aparte y sin fetch (con los joins de arriba Spring Data no puede derivarlo). Las apariciones en
    // listas no se traen aqui: una coleccion con fetch obligaria a paginar en memoria (ver cargarManchas).
    @Query(
        value = """
        SELECT e FROM Entidad e
        JOIN FETCH e.tipoDocumento td
        LEFT JOIN FETCH e.pais
        LEFT JOIN FETCH e.personaNatural pn
        LEFT JOIN FETCH e.personaJuridica pj
        WHERE (:documento IS NULL OR e.documento = :documento)
        AND (:tipoDocumento IS NULL OR td.nombre = :tipoDocumento)
        AND (:apellidoPaterno IS NULL OR UPPER(pn.apePat) LIKE UPPER(CONCAT('%', :apellidoPaterno, '%')))
        AND (:apellidoMaterno IS NULL OR UPPER(pn.apeMat) LIKE UPPER(CONCAT('%', :apellidoMaterno, '%')))
        AND (
            :nombres IS NULL
            OR UPPER(pn.nombre) LIKE UPPER(CONCAT('%', :nombres, '%'))
            OR UPPER(pn.segundoNombre) LIKE UPPER(CONCAT('%', :nombres, '%'))
            OR UPPER(pj.razonSocial) LIKE UPPER(CONCAT('%', :nombres, '%'))
        )
        ORDER BY COALESCE(pn.apePat, pj.razonSocial), pn.apeMat, pn.nombre, e.id
        """,
        countQuery = """
        SELECT COUNT(e) FROM Entidad e
        JOIN e.tipoDocumento td
        LEFT JOIN e.personaNatural pn
        LEFT JOIN e.personaJuridica pj
        WHERE (:documento IS NULL OR e.documento = :documento)
        AND (:tipoDocumento IS NULL OR td.nombre = :tipoDocumento)
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
        @Param("tipoDocumento") tipoDocumento: String?,
        @Param("nombres") nombres: String?,
        @Param("apellidoPaterno") apellidoPaterno: String?,
        @Param("apellidoMaterno") apellidoMaterno: String?,
        pageable: Pageable
    ): Page<Entidad>

    // Segunda consulta de la busqueda: las apariciones en listas (con su lista y su grupo) de todas las personas de
    // la pagina de una sola vez, en lugar de una consulta por persona.
    @Query(
        """
        SELECT e FROM Entidad e
        LEFT JOIN FETCH e.manchas m
        LEFT JOIN FETCH m.tipoLista tl
        LEFT JOIN FETCH tl.grupo
        WHERE e.id IN :ids
        """
    )
    fun cargarManchas(@Param("ids") ids: Collection<Int>): List<Entidad>

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
