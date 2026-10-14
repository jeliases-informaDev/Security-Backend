package com.security.modules.operaciones.dto

import com.tuempresa.operaciones.dto.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate

data class ActualizarOperacionRequest(
    @field:Valid val registro: RegistroEditableDto,
    @field:Valid val personaFisica: PersonaDto,
    @field:Valid val personaRepresentada: PersonaDto?,
    @field:Valid val operacion: OperacionDto,
    @field:Valid @field:NotEmpty val beneficiarios: List<BeneficiarioDto>
)

data class RegistroEditableDto(
    @field:NotBlank val oficina: String,
    val fechaRegistro: LocalDate
)
