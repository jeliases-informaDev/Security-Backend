package com.security.shared.datos.repositories

import com.security.shared.datos.entities.TipoDocumento
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TipoDocumentoRepository : JpaRepository<TipoDocumento, Int> {
    fun findByNombre(nombre: String): TipoDocumento?
}
