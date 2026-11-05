package com.security.modules.listas.infrastructure

import com.security.modules.listas.domain.ListaGrupoPeso
import com.security.modules.listas.domain.ListaRegla
import com.security.modules.listas.domain.ListaReglaOtras
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ListaGrupoPesoRepository : JpaRepository<ListaGrupoPeso, Int> {
    fun findByCodigo(codigo: String): ListaGrupoPeso?
}

@Repository
interface ListaReglaRepository : JpaRepository<ListaRegla, Int> {
    fun findByCodigo(codigo: String): ListaRegla?
}

@Repository
interface ListaReglaOtrasRepository : JpaRepository<ListaReglaOtras, Int> {
    fun findByOrdenGrupo(ordenGrupo: Int): ListaReglaOtras?
}
