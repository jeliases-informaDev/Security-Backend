package com.security.modules.operaciones.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Datos de un registro listos para mostrar (detalle) y para el PDF. Los campos de valor traen lo guardado (codigo nuevo
 * o texto viejo); los `…Nombre`, lo legible. Los campos nuevos van al final con valor por defecto.
 */
data class OperacionExportacion(
    val id: Long,
    val codigoOperacion: String,
    val registro: RegistroExportacion,
    val personaFisica: PersonaExportacion,
    val personaRepresentada: PersonaExportacion?,
    val operacion: DatosOperacionExportacion,
    val beneficiarios: List<BeneficiarioExportacion>,
    val idUsuarioResponsable: Int? = null,
    // SHA-256 del contenido completo del registro (ver FotoOperacion.huella)
    val huella: String = ""
)

data class RegistroExportacion(
    val usuarioId: String,
    val usuario: String,
    val empresa: String,
    val oficina: String,
    val fechaRegistro: LocalDate,
    val usuarioNombre: String? = null,
    val creadoEn: LocalDateTime? = null
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
    val distrito: String?,
    val tipoDocumentoNombre: String? = null,
    val motivoSinDocumento: String? = null,
    val estadoCivil: String? = null,
    val estadoCivilNombre: String? = null,
    val tipoPersona: String = "NATURAL",
    val razonSocial: String? = null,
    val representanteLegal: String? = null,
    val representanteTipoDocumento: String? = null,
    val representanteTipoDocumentoNombre: String? = null,
    val representanteNumeroDocumento: String? = null,
    val accionistasMayores25: String? = null,
    val domicilioFiscal: String? = null
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
    val cuenta3: String?,
    val tipoOperacionNombre: String? = null,
    val modalidadPagoNombre: String? = null,
    val modalidadPagoTexto: String? = null,
    val monedaCodigo: String? = null,
    val monedaSimbolo: String? = null,
    val origenFondos: String? = null,
    val origenFondosNombre: String? = null,
    val origenFondosTexto: String? = null,
    val numeroComprobante: String? = null,
    val descripcion: String? = null
)

data class BeneficiarioExportacion(
    val nombre: String,
    val tipoDocumento: String,
    val numeroDocumento: String,
    val fechaNacimiento: LocalDate?,
    val tipoDocumentoNombre: String? = null
)
