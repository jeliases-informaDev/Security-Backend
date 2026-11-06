package com.security.modules.operaciones.dto

import com.tuempresa.operaciones.dto.*
import java.time.LocalDate

/** Edicion (RO 6): el formulario completo mas el motivo (obligatorio, hasta 300 caracteres). */
data class ActualizarOperacionRequest(
    val registro: RegistroEditableDto = RegistroEditableDto(),
    val personaFisica: PersonaDto = PersonaDto(),
    val personaRepresentada: PersonaDto? = null,
    val operacion: OperacionDto = OperacionDto(),
    val beneficiarios: List<BeneficiarioDto> = emptyList(),
    val motivo: String? = null
)

/** fechaRegistro se ignora: la fecha de registro no se edita. */
data class RegistroEditableDto(
    val oficina: String? = null,
    val fechaRegistro: LocalDate? = null
)
