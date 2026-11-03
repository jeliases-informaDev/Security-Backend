package com.security.modules.creditos.infrastructure.repository

import com.security.modules.creditos.domain.AvisoLicencia
import com.security.modules.creditos.domain.LicenciaEmpresa
import com.security.modules.creditos.domain.MovimientoCredito
import com.security.modules.creditos.domain.TarifaCredito
import com.security.modules.creditos.domain.AccionCredito
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import jakarta.persistence.LockModeType
import java.util.Optional

@Repository
interface LicenciaEmpresaRepository : JpaRepository<LicenciaEmpresa, Int> {
    fun findByEmpresaId(empresaId: Int): Optional<LicenciaEmpresa>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LicenciaEmpresa l WHERE l.empresaId = :empresaId")
    fun findByEmpresaIdForUpdate(@Param("empresaId") empresaId: Int): Optional<LicenciaEmpresa>
}

@Repository
interface MovimientoCreditoRepository : JpaRepository<MovimientoCredito, Int> {
    fun findAllByEmpresaIdOrderByFechaDesc(empresaId: Int, pageable: Pageable): Page<MovimientoCredito>
    fun findAllByEmpresaIdAndUsuarioIdOrderByFechaDesc(empresaId: Int, usuarioId: Int, pageable: Pageable): Page<MovimientoCredito>
    fun findAllByOrderByFechaDesc(pageable: Pageable): Page<MovimientoCredito>
}

@Repository
interface TarifaCreditoRepository : JpaRepository<TarifaCredito, AccionCredito>

@Repository
interface AvisoLicenciaRepository : JpaRepository<AvisoLicencia, Int> {
    fun findAllByEmpresaIdOrderByFechaDesc(empresaId: Int): List<AvisoLicencia>
    fun findAllByEmpresaIdAndLeidoFalseOrderByFechaDesc(empresaId: Int): List<AvisoLicencia>
    fun countByEmpresaIdAndLeidoFalse(empresaId: Int): Long
}
