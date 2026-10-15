package com.tuempresa.operaciones.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

data class RegistrarOperacionRequest(
    @field:Valid @field:NotNull val registro: RegistroDto,
    @field:Valid @field:NotNull val personaFisica: PersonaDto,
    @field:Valid val personaRepresentada: PersonaDto?,
    @field:Valid @field:NotNull val operacion: OperacionDto,
    @field:Valid @field:NotEmpty val beneficiarios: List<BeneficiarioDto>,
    @field:NotBlank @field:Size(max = 50) val numeroRegistro: String
)

data class RegistroDto(
    @field:NotBlank val usuarioId: String,
    @field:NotBlank val usuario: String,
    @field:NotBlank val empresa: String,
    @field:NotBlank val oficina: String,
    @field:NotNull val fechaRegistro: LocalDate
)

data class PersonaDto(
    @field:NotBlank val tipoDocumento: String,
    @field:NotBlank val numeroDocumento: String,
    @field:NotBlank val nombres: String,
    @field:NotBlank val apellidos: String,
    @field:NotNull val fechaNacimiento: LocalDate,
    val nacionalidad: String?,
    val telefono: String?,
    val ocupacion: String?,
    val domicilio: String?,
    val codigoPostal: String?,
    val departamento: String?,
    val provincia: String?,
    val distrito: String?
)

data class OperacionDto(
    @field:NotNull val fechaOperacion: LocalDate,
    val lugar: String?,
    @field:NotBlank val modalidadPago: String,
    @field:NotBlank val moneda: String,          // "PEN — Soles"
    @field:NotNull val monto: BigDecimal,        // Jackson convierte "234" -> BigDecimal
    @field:NotBlank val tipoOperacion: String,
    val cuenta1: String?,
    val cuenta2: String?,
    val cuenta3: String?
)

data class BeneficiarioDto(
    @field:NotBlank val nombre: String,
    @field:NotBlank val tipoDocumento: String,
    @field:NotBlank val numeroDocumento: String,
    @field:NotNull val fechaNacimiento: LocalDate
)