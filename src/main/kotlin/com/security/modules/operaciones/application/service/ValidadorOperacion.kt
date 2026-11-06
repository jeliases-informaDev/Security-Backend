package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.domain.BeneficiarioEntity
import com.security.modules.operaciones.domain.Operacion
import com.security.modules.operaciones.domain.Persona
import com.tuempresa.operaciones.dto.BeneficiarioDto
import com.tuempresa.operaciones.dto.OperacionDto
import com.tuempresa.operaciones.dto.PersonaDto
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Formulario ya validado y limpio (textos sin espacios sobrantes, vacios = null, codigos de catalogo canonicos). */
data class Formulario(
    val oficina: String,
    val personaFisica: PersonaDto,
    val personaRepresentada: PersonaDto?,
    val operacion: OperacionDto,
    val fechaOperacion: LocalDate,
    val monto: BigDecimal,
    val beneficiarios: List<BeneficiarioDto>
)

/**
 * Datos minimos para guardar (los 5 de RO 2) y formato de cada campo. Junta todos los errores y responde
 * OPERACIONES_DATOS_INVALIDOS con el mensaje de cada campo.
 *
 * Al editar se pasa `anterior` (valores guardados por campo, de FotoOperacion): un valor que no cambia se acepta tal cual,
 * para que un registro anterior al diseño nuevo (tipo de operacion escrito a mano, sin origen de fondos…) se pueda corregir
 * sin obligar a rehacer lo que no se toca.
 */
class ValidadorOperacion(
    private val cat: Catalogos,
    private val anterior: Map<String, String?>? = null,
    // Oficinas configuradas por la empresa (RO 16): si hay, la oficina debe ser una de ellas
    private val oficinas: List<String> = emptyList()
) {

    private val errores = linkedMapOf<String, String>()

    fun validar(
        oficina: String?,
        personaFisica: PersonaDto,
        personaRepresentada: PersonaDto?,
        operacion: OperacionDto,
        beneficiarios: List<BeneficiarioDto>
    ): Formulario {
        val ofi = texto("registro.oficina", oficina, 255, true, "Indica la oficina.")?.let { o ->
            if (oficinas.isEmpty() || igual("registro.oficina", o)) o
            else oficinas.firstOrNull { it.equals(o, ignoreCase = true) } ?: o.also { error("registro.oficina", "Elige una oficina de la lista.") }
        }
        val fisica = bloquePersona("personaFisica", personaFisica, representada = false)
        val representada = personaRepresentada?.let { bloquePersona("personaRepresentada", it, representada = true) }
        val (op, fecha, monto) = bloqueOperacion(operacion)
        if (beneficiarios.isEmpty()) error("beneficiarios", "Agrega al menos un beneficiario.")
        val bens = beneficiarios.mapIndexed { i, b -> bloqueBeneficiario("beneficiarios[$i]", b) }
        if (errores.isNotEmpty()) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.DATOS_INVALIDOS, errores.values.first(), errores.toMap())
        }
        return Formulario(requireNotNull(ofi), fisica, representada, op, requireNotNull(fecha), requireNotNull(monto), bens)
    }

    // ---------- Bloques ----------

    private fun bloquePersona(p: String, dto: PersonaDto, representada: Boolean): PersonaDto {
        val tipoPersona = if (!representada) null else {
            val t = limpio(dto.tipoPersona)?.uppercase() ?: CatalogosOperacion.NATURAL
            if (t != CatalogosOperacion.NATURAL && t != CatalogosOperacion.JURIDICA) {
                error("$p.tipoPersona", "Elige si es persona natural o jurídica.")
            }
            t
        }
        val juridica = tipoPersona == CatalogosOperacion.JURIDICA
        val tipoDoc = codigo("$p.tipoDocumento", CatalogosOperacion.TIPO_DOCUMENTO, dto.tipoDocumento, true, "Elige el tipo de documento.")
        val sinDoc = tipoDoc.equals(CatalogosOperacion.SIN_DOCUMENTO, ignoreCase = true)
        return PersonaDto(
            tipoDocumento = tipoDoc,
            numeroDocumento = texto("$p.numeroDocumento", dto.numeroDocumento, 50, !sinDoc, "Escribe el número de documento."),
            motivoSinDocumento = if (sinDoc) texto("$p.motivoSinDocumento", dto.motivoSinDocumento, 300, true, "Indica por qué no tiene documento.") else null,
            nombres = texto("$p.nombres", dto.nombres, 150, !juridica, "Escribe los nombres."),
            apellidos = texto("$p.apellidos", dto.apellidos, 150, !juridica, "Escribe los apellidos."),
            fechaNacimiento = dto.fechaNacimiento,
            nacionalidad = texto("$p.nacionalidad", dto.nacionalidad, 100),
            telefono = texto("$p.telefono", dto.telefono, 50),
            ocupacion = texto("$p.ocupacion", dto.ocupacion, 150),
            estadoCivil = codigo("$p.estadoCivil", CatalogosOperacion.ESTADO_CIVIL, dto.estadoCivil, false, ""),
            domicilio = texto("$p.domicilio", dto.domicilio, 500),
            codigoPostal = texto("$p.codigoPostal", dto.codigoPostal, 20),
            departamento = texto("$p.departamento", dto.departamento, 100),
            provincia = texto("$p.provincia", dto.provincia, 100),
            distrito = texto("$p.distrito", dto.distrito, 100),
            tipoPersona = tipoPersona,
            razonSocial = if (juridica) texto("$p.razonSocial", dto.razonSocial, 255, true, "Escribe la razón social.") else null,
            representanteLegal = if (juridica) texto("$p.representanteLegal", dto.representanteLegal, 255) else null,
            representanteTipoDocumento = if (juridica) codigo("$p.representanteTipoDocumento", CatalogosOperacion.TIPO_DOCUMENTO, dto.representanteTipoDocumento, false, "") else null,
            representanteNumeroDocumento = if (juridica) texto("$p.representanteNumeroDocumento", dto.representanteNumeroDocumento, 50) else null,
            accionistasMayores25 = if (juridica) texto("$p.accionistasMayores25", dto.accionistasMayores25, 1000) else null,
            domicilioFiscal = if (juridica) texto("$p.domicilioFiscal", dto.domicilioFiscal, 500) else null
        )
    }

    private fun bloqueOperacion(dto: OperacionDto): Triple<OperacionDto, LocalDate?, BigDecimal?> {
        val p = "operacion"
        if (dto.fechaOperacion == null) error("$p.fechaOperacion", "Indica la fecha de la operación.")
        val tipo = codigo("$p.tipoOperacion", CatalogosOperacion.TIPO_OPERACION, dto.tipoOperacion, true, "Elige el tipo de operación.")
        val monto = validarMonto("$p.monto", dto.monto)
        val modalidad = codigo("$p.modalidadPago", CatalogosOperacion.MODALIDAD_PAGO, dto.modalidadPago, true, "Elige la modalidad de pago.")
        val modalidadTexto = if (cat.opcion(CatalogosOperacion.MODALIDAD_PAGO, modalidad)?.pideTexto == true && !igual("$p.modalidadPago", modalidad)) {
            texto("$p.modalidadPagoTexto", dto.modalidadPagoTexto, 255, true, "Especifica la modalidad de pago.")
        } else texto("$p.modalidadPagoTexto", dto.modalidadPagoTexto, 255)
        val moneda = validarMoneda("$p.moneda", dto.moneda)
        val origen = codigo("$p.origenFondos", CatalogosOperacion.ORIGEN_FONDOS, dto.origenFondos, true, "Elige el origen de los fondos.")
        val origenTexto = if (cat.opcion(CatalogosOperacion.ORIGEN_FONDOS, origen)?.pideTexto == true && !igual("$p.origenFondos", origen)) {
            texto("$p.origenFondosTexto", dto.origenFondosTexto, 255, true, "Describe el origen de los fondos.")
        } else texto("$p.origenFondosTexto", dto.origenFondosTexto, 255)
        val limpio = OperacionDto(
            fechaOperacion = dto.fechaOperacion,
            lugar = texto("$p.lugar", dto.lugar, 255),
            modalidadPago = modalidad,
            moneda = moneda,
            monto = monto,
            tipoOperacion = tipo,
            cuenta1 = texto("$p.cuenta1", dto.cuenta1, 255),
            cuenta2 = texto("$p.cuenta2", dto.cuenta2, 255),
            cuenta3 = texto("$p.cuenta3", dto.cuenta3, 255),
            modalidadPagoTexto = modalidadTexto,
            origenFondos = origen,
            origenFondosTexto = origenTexto,
            numeroComprobante = texto("$p.numeroComprobante", dto.numeroComprobante, 100),
            descripcion = texto("$p.descripcion", dto.descripcion, 500)
        )
        return Triple(limpio, dto.fechaOperacion, monto)
    }

    private fun bloqueBeneficiario(p: String, dto: BeneficiarioDto): BeneficiarioDto {
        val tipo = codigo("$p.tipoDocumento", CatalogosOperacion.TIPO_DOCUMENTO, dto.tipoDocumento, true, "Elige el tipo de documento del beneficiario.")
        val sinDoc = tipo.equals(CatalogosOperacion.SIN_DOCUMENTO, ignoreCase = true)
        return BeneficiarioDto(
            nombre = texto("$p.nombre", dto.nombre, 255, true, "Escribe el nombre o la razón social del beneficiario."),
            tipoDocumento = tipo,
            numeroDocumento = texto("$p.numeroDocumento", dto.numeroDocumento, 255, !sinDoc, "Escribe el número de documento del beneficiario."),
            fechaNacimiento = dto.fechaNacimiento
        )
    }

    // ---------- Campos ----------

    private fun limpio(v: String?): String? = v?.trim()?.takeIf { it.isNotEmpty() }

    /** Al editar: el valor es el mismo que ya estaba guardado. */
    private fun igual(campo: String, valor: String?): Boolean =
        anterior != null && limpio(anterior[campo]) == limpio(valor)

    private fun error(campo: String, mensaje: String) {
        errores.putIfAbsent(campo, mensaje)
    }

    private fun texto(campo: String, valor: String?, max: Int, obligatorio: Boolean = false, mensaje: String = ""): String? {
        val t = limpio(valor)
        if (t == null) {
            if (obligatorio && !igual(campo, null)) error(campo, mensaje)
            return null
        }
        if (t.length > max && !igual(campo, t)) error(campo, "Máximo $max caracteres.")
        return t
    }

    private fun codigo(campo: String, catalogo: String, valor: String?, obligatorio: Boolean, mensaje: String): String? {
        val t = limpio(valor)
        if (t == null) {
            if (obligatorio && !igual(campo, null)) error(campo, mensaje)
            return null
        }
        if (igual(campo, t)) return t
        val opcion = cat.opcion(catalogo, t)
        if (opcion == null) {
            error(campo, "Elige una opción de la lista.")
            return t
        }
        return opcion.codigo
    }

    private fun validarMoneda(campo: String, valor: String?): String? {
        val t = limpio(valor)
        if (t == null) {
            if (!igual(campo, null)) error(campo, "Elige la moneda.")
            return null
        }
        if (igual(campo, t)) return t
        val m = cat.monedas.firstOrNull { it.codigo.equals(t, ignoreCase = true) }
        if (m == null) {
            error(campo, "Elige una opción de la lista.")
            return t
        }
        return m.codigo
    }

    private fun validarMonto(campo: String, valor: BigDecimal?): BigDecimal? {
        if (valor == null) {
            error(campo, "Escribe el monto.")
            return null
        }
        val m = try {
            valor.setScale(2, RoundingMode.UNNECESSARY)
        } catch (_: ArithmeticException) {
            error(campo, "El monto admite hasta dos decimales.")
            return null
        }
        if (m.precision() > 15) error(campo, "Monto fuera del rango permitido.")
        else if (m.signum() <= 0 && !igual(campo, m.toPlainString())) error(campo, "El monto debe ser mayor que cero.")
        return m
    }
}

/** Pasa un formulario validado a las entidades (alta y edicion). */
object EscritorOperacion {

    fun persona(destino: Persona, p: PersonaDto): Persona = destino.also {
        it.tipoDocumento = p.tipoDocumento
        it.numeroDocumento = p.numeroDocumento
        it.motivoSinDocumento = p.motivoSinDocumento
        it.nombres = p.nombres
        it.apellidos = p.apellidos
        it.fechaNacimiento = p.fechaNacimiento
        it.nacionalidad = p.nacionalidad
        it.telefono = p.telefono
        it.profesionOcupacion = p.ocupacion
        it.estadoCivil = p.estadoCivil
        it.domicilio = p.domicilio
        it.codigoPostal = p.codigoPostal
        it.departamento = p.departamento
        it.provincia = p.provincia
        it.distrito = p.distrito
        it.tipoPersona = p.tipoPersona
        it.razonSocial = p.razonSocial
        it.representanteLegal = p.representanteLegal
        it.representanteTipoDocumento = p.representanteTipoDocumento
        it.representanteNumeroDocumento = p.representanteNumeroDocumento
        it.accionistasMayores25 = p.accionistasMayores25
        it.domicilioFiscal = p.domicilioFiscal
    }

    fun nuevaOperacion(f: Formulario): Operacion = Operacion(
        fechaOperacion = f.fechaOperacion, lugar = null, modalidadPago = "", moneda = "", monto = f.monto,
        tipoOperacion = "", cuenta1 = null, cuenta2 = null, cuenta3 = null
    ).also { aplicar(it, f) }

    fun aplicar(o: Operacion, f: Formulario) {
        val d = f.operacion
        o.fechaOperacion = f.fechaOperacion
        o.lugar = d.lugar
        o.modalidadPago = requireNotNull(d.modalidadPago)
        o.modalidadPagoTexto = d.modalidadPagoTexto
        o.moneda = requireNotNull(d.moneda)
        o.monto = f.monto
        o.tipoOperacion = requireNotNull(d.tipoOperacion)
        o.origenFondos = d.origenFondos
        o.origenFondosTexto = d.origenFondosTexto
        o.numeroComprobante = d.numeroComprobante
        o.cuenta1 = d.cuenta1
        o.cuenta2 = d.cuenta2
        o.cuenta3 = d.cuenta3
        o.descripcion = d.descripcion
    }

    fun beneficiarios(f: Formulario): List<BeneficiarioEntity> = f.beneficiarios.map {
        BeneficiarioEntity(
            nombre = requireNotNull(it.nombre), tipoDocumento = requireNotNull(it.tipoDocumento),
            numeroDocumento = it.numeroDocumento.orEmpty(), fechaNacimiento = it.fechaNacimiento
        )
    }
}
