package com.security.modules.oficial.persistencia

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.Repository
import java.time.LocalDate

/* Todas las busquedas por uuid van junto con la empresa: lo de otra empresa simplemente no aparece. */

interface OcDesignacionRepository : JpaRepository<OcDesignacion, Long> {
    fun findByUuidAndEmpresaId(uuid: String, empresaId: Int): OcDesignacion?
    fun findByIdAndEmpresaId(id: Long, empresaId: Int): OcDesignacion?
    fun findByEmpresaIdOrderByGuardadaEnDescIdDesc(empresaId: Int): List<OcDesignacion>
    fun findFirstByEmpresaIdAndTipoCargoAndEstadoNotOrderByGuardadaEnDescIdDesc(
        empresaId: Int, tipoCargo: String, estado: String
    ): OcDesignacion?
    fun findFirstByEmpresaIdAndTipoCargoAndEstadoOrderByRemovidaEnDescIdDesc(
        empresaId: Int, tipoCargo: String, estado: String
    ): OcDesignacion?
    fun findFirstByEmpresaIdOrderByGuardadaEnDescIdDesc(empresaId: Int): OcDesignacion?
}

interface OcAdjuntoRepository : JpaRepository<OcAdjunto, Long> {
    fun findByUuidAndEmpresaId(uuid: String, empresaId: Int): OcAdjunto?
    fun findByIdAndEmpresaId(id: Long, empresaId: Int): OcAdjunto?
}

interface OcRemocionRepository : JpaRepository<OcRemocion, Long> {
    fun findFirstByDesignacionIdAndEmpresaIdOrderByIdDesc(designacionId: Long, empresaId: Int): OcRemocion?
}

interface OcPendienteRepository : JpaRepository<OcPendiente, Long> {
    fun findByUuidAndEmpresaId(uuid: String, empresaId: Int): OcPendiente?
    fun findByEmpresaIdOrderByCreadoEnDescIdDesc(empresaId: Int): List<OcPendiente>
    fun findByEmpresaIdAndComunicadoFalseOrderByCreadoEnDescIdDesc(empresaId: Int): List<OcPendiente>
    fun findByDesignacionIdAndEmpresaIdOrderByCreadoEnDescIdDesc(designacionId: Long, empresaId: Int): List<OcPendiente>
    fun findFirstByRemocionIdAndEmpresaId(remocionId: Long, empresaId: Int): OcPendiente?
}

/** Historial: solo guardar y leer. No hay metodos para actualizar ni borrar (a proposito no extiende JpaRepository). */
interface OcEventoRepository : Repository<OcEvento, Long> {
    fun save(evento: OcEvento): OcEvento
    fun findByDesignacionIdAndEmpresaIdOrderByFechaHoraDescIdDesc(designacionId: Long, empresaId: Int): List<OcEvento>
}

interface OcEventoCambioRepository : Repository<OcEventoCambio, Long> {
    fun save(cambio: OcEventoCambio): OcEventoCambio
    fun findByEventoIdInOrderByEventoIdAscOrdenAsc(eventoIds: Collection<Long>): List<OcEventoCambio>
}

interface OcReglaRepository : JpaRepository<OcRegla, String>

interface OcFeriadoRepository : JpaRepository<OcFeriado, LocalDate> {
    fun findAllByOrderByFechaAsc(): List<OcFeriado>
    fun findByFechaBetweenOrderByFechaAsc(desde: LocalDate, hasta: LocalDate): List<OcFeriado>
}
