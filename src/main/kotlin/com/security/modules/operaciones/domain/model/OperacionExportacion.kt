
package com.security.modules.operaciones.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class OperacionExportacion(
    val id: Long,
    val codigoOperacion: String,
    val registro: RegistroExportacion,
    val personaFisica: PersonaExportacion,
    val personaRepresentada: PersonaExportacion?,
    val operacion: DatosOperacionExportacion,
    val beneficiarios: List<BeneficiarioExportacion>
)

data class RegistroExportacion(
    val usuarioId: String,
    val usuario: String,
    val empresa: String,
    val oficina: String,
    val fechaRegistro: LocalDate
)

data class PersonaExportacion(
    val tipoDocumento: String,
    val numeroDocumento: String,
    val nombres: String,
    val apellidos: String,
    val fechaNacimiento: LocalDate?,
    val nacionalidad: String?,
    val telefono: String?,
    val ocupacion: String?,
    val domicilio: String?,
    val codigoPostal: String?,
    val departamento: String?,
    val provincia: String?,
    val distrito: String?
)

data class DatosOperacionExportacion(
    val fechaOperacion: LocalDate,
    val lugar: String?,
    val modalidadPago: String,
    val moneda: String,
    val monto: BigDecimal,
    val tipoOperacion: String,
    val cuenta1: String?,
    val cuenta2: String?,
    val cuenta3: String?
)

data class BeneficiarioExportacion(
    val nombre: String,
    val tipoDocumento: String,
    val numeroDocumento: String,
    val fechaNacimiento: LocalDate
)
