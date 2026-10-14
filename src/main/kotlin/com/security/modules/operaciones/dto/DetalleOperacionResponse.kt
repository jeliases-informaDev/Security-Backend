package com.security.modules.operaciones.dto

import com.security.modules.operaciones.domain.model.*

/** Datos del formulario; no expone entidades JPA ni sus relaciones. */
data class DetalleOperacionResponse(
    val id: Long,
    val numeroRegistro: String,
    val registro: RegistroExportacion,
    val personaFisica: PersonaExportacion,
    val personaRepresentada: PersonaExportacion?,
    val operacion: DatosOperacionExportacion,
    val beneficiarios: List<BeneficiarioExportacion>
)
