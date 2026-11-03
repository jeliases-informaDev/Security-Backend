package com.security.modules.creditos.dto

import com.security.modules.creditos.domain.AccionCredito
import com.security.modules.creditos.domain.EstadoLicencia
import com.security.modules.creditos.domain.TipoAviso
import com.security.modules.creditos.domain.TipoMovimiento
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.LocalDateTime

data class ResumenBolsaResponse(
    val empresaId: Int,
    val empresaNombre: String,
    val creditosTotales: Long,
    val creditosConsumidos: Long,
    val creditosDisponibles: Long,
    val porcentajeConsumo: Int,
    val toleranciaPorcentaje: Int,
    val toleranciaCreditos: Long,
    val exceso: Long,
    val enExceso: Boolean,
    val cupoUsuarios: Int?,
    val usuariosActivos: Long,
    val fechaVencimiento: LocalDate?,
    val diasParaVencer: Long?,
    val estado: EstadoLicencia,
    val avisosPendientes: Long
)

data class MovimientoCreditoResponse(
    val id: Int,
    val empresaId: Int,
    val usuarioId: Int?,
    val usuario: String?,
    val tipo: TipoMovimiento,
    val accion: AccionCredito?,
    val cantidad: Long,
    val exceso: Boolean,
    val referencia: String?,
    val consumidosDespues: Long,
    val fecha: LocalDateTime
)

/**
 * Payload que envía Sulotec Central al sincronizar la asignación de créditos/licencia para una empresa (BPMN P1 / P4).
 */
data class SincronizarAsignacionCreditosRequest(
    @field:NotNull(message = "El id de la empresa es obligatorio")
    val empresaId: Int,
    @field:Min(value = 0, message = "Los créditos asignados no pueden ser negativos")
    val creditosTotales: Long,
    @field:Min(value = 0, message = "La tolerancia no puede ser negativa")
    val toleranciaPorcentaje: Int = 10,
    val cupoUsuarios: Int? = null,
    val fechaVencimiento: LocalDate? = null
)

data class AvisoLicenciaResponse(
    val id: Int,
    val tipo: TipoAviso,
    val asunto: String,
    val mensaje: String,
    val leido: Boolean,
    val fecha: LocalDateTime
)
