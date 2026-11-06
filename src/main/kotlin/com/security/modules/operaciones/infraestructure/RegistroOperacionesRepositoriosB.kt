package com.security.modules.operaciones.infraestructure

import com.security.modules.operaciones.domain.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface UmbralNormaRepository : JpaRepository<UmbralNormaEntity, Long> {
    fun findAllByGrupoOrderByOrdenAsc(grupo: String): List<UmbralNormaEntity>
}

interface ConfiguracionOperacionesRepository : JpaRepository<ConfiguracionOperacionesEntity, Long> {
    fun findByEmpresa(empresa: String): ConfiguracionOperacionesEntity?
}

interface TipoCambioOperacionRepository : JpaRepository<TipoCambioOperacionEntity, Long> {
    fun findByEmpresaAndMonedaAndMes(empresa: String, moneda: String, mes: String): TipoCambioOperacionEntity?
    fun findAllByEmpresaInAndMesStartingWith(empresas: Collection<String>, prefijo: String): List<TipoCambioOperacionEntity>
}

interface AnalisisOperacionRepository : JpaRepository<AnalisisOperacionEntity, Long> {
    fun findByRegistroId(registroId: Long): AnalisisOperacionEntity?
}

/** Adjuntos del analisis: guardar y leer (no se borran). */
interface AdjuntoAnalisisRepository : org.springframework.data.repository.Repository<AdjuntoAnalisisEntity, Long> {
    fun save(adjunto: AdjuntoAnalisisEntity): AdjuntoAnalisisEntity
    fun findAllByRegistroIdOrderBySubidoEnAscIdAsc(registroId: Long): List<AdjuntoAnalisisEntity>
    fun findByIdAndRegistroId(id: Long, registroId: Long): AdjuntoAnalisisEntity?
}

interface ExportacionOperacionesRepository : JpaRepository<ExportacionOperacionesEntity, Long> {
    fun findAllByEmpresaOrderByGeneradoEnDescIdDesc(empresa: String, pageable: Pageable): Page<ExportacionOperacionesEntity>
}
