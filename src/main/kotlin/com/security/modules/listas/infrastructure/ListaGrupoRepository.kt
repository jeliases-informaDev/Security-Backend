package com.security.modules.listas.infrastructure

import com.security.modules.listas.domain.ListaGrupo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ListaGrupoRepository : JpaRepository<ListaGrupo, Int>
