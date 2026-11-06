package com.tuempresa.operaciones.dto

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Alta de un registro (formulario nuevo, RO 2/RO 3). Los datos minimos los valida ValidadorOperacion y responde
 * OPERACIONES_DATOS_INVALIDOS con el mensaje de cada campo; por eso aqui todo es opcional.
 * `numeroRegistro` ya no se usa: el numero RO-AAAA-NNNNNN lo pone el servidor (si llega, se ignora).
 */
data class RegistrarOperacionRequest(
    val registro: RegistroDto = RegistroDto(),
    val personaFisica: PersonaDto = PersonaDto(),
    val personaRepresentada: PersonaDto? = null,
    val operacion: OperacionDto = OperacionDto(),
    val beneficiarios: List<BeneficiarioDto> = emptyList(),
    val numeroRegistro: String? = null
)

/**
 * usuarioId, usuario y empresa los pone el servidor (solo el administrador universal puede indicar otra cuenta).
 * fechaRegistro se ignora: la fecha y la hora de registro las pone el servidor.
 */
data class RegistroDto(
    val usuarioId: String? = null,
    val usuario: String? = null,
    val empresa: String? = null,
    val oficina: String? = null,
    val fechaRegistro: LocalDate? = null
)

/** Persona que realiza (natural) o representada (natural o juridica, segun tipoPersona). */
data class PersonaDto(
    val tipoDocumento: String? = null,
    val numeroDocumento: String? = null,
    val nombres: String? = null,
    val apellidos: String? = null,
    val fechaNacimiento: LocalDate? = null,
    val nacionalidad: String? = null,
    val telefono: String? = null,
    val ocupacion: String? = null,
    val domicilio: String? = null,
    val codigoPostal: String? = null,
    val departamento: String? = null,
    val provincia: String? = null,
    val distrito: String? = null,
    val motivoSinDocumento: String? = null,
    val estadoCivil: String? = null,
    val tipoPersona: String? = null,
    val razonSocial: String? = null,
    val representanteLegal: String? = null,
    val representanteTipoDocumento: String? = null,
    val representanteNumeroDocumento: String? = null,
    val accionistasMayores25: String? = null,
    val domicilioFiscal: String? = null
)

data class OperacionDto(
    val fechaOperacion: LocalDate? = null,
    val lugar: String? = null,
    val modalidadPago: String? = null,
    val moneda: String? = null,
    val monto: BigDecimal? = null,
    val tipoOperacion: String? = null,
    val cuenta1: String? = null,
    val cuenta2: String? = null,
    val cuenta3: String? = null,
    val modalidadPagoTexto: String? = null,
    val origenFondos: String? = null,
    val origenFondosTexto: String? = null,
    val numeroComprobante: String? = null,
    val descripcion: String? = null
)

data class BeneficiarioDto(
    val nombre: String? = null,
    val tipoDocumento: String? = null,
    val numeroDocumento: String? = null,
    val fechaNacimiento: LocalDate? = null
)
