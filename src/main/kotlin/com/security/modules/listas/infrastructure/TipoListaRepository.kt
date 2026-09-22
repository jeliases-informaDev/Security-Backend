package com.security.modules.listas.infrastructure

import com.security.modules.listas.domain.TipoLista
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TipoListaRepository : JpaRepository<TipoLista, Int> {
    fun findByCodigo(codigo: String): TipoLista?
}
