package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.domain.CambioCampo
import com.security.modules.operaciones.domain.Persona
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.infraestructure.CatalogoExportacion
import java.math.BigDecimal
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un campo del registro: ruta tecnica, etiqueta para la pantalla, valor guardado y valor legible. */
data class CampoFoto(val campo: String, val etiqueta: String, val valor: String?, val legible: String?)

/**
 * Foto campo por campo de un registro. Sirve para tres cosas:
 *  - detectar los cambios de una edicion («antes → después» ya legibles, con su etiqueta);
 *  - aceptar tal cual, al editar, los valores viejos que no cambian (ValidadorOperacion);
 *  - la huella SHA-256 del contenido que va en el PDF.
 */
object FotoOperacion {

    private val FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun fecha(valor: LocalDate?): String? = valor?.format(FECHA)

    fun monto(valor: BigDecimal?): String? =
        valor?.let { DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(it) }

    private class Def(val nombre: String, val etiqueta: String, val leer: (Persona) -> String?, val legible: (Catalogos, String?) -> String?)

    private val sinCambio: (Catalogos, String?) -> String? = { _, v -> v }

    private val PERSONA = listOf(
        Def("tipoPersona", "Tipo de persona", { it.tipoPersona }) { _, v ->
            when (v?.uppercase()) { CatalogosOperacion.JURIDICA -> "Jurídica"; CatalogosOperacion.NATURAL -> "Natural"; else -> v }
        },
        Def("tipoDocumento", "Tipo de documento", { it.tipoDocumento }) { c, v -> c.nombreDocumento(v) },
        Def("numeroDocumento", "N.º de documento", { it.numeroDocumento }, sinCambio),
        Def("motivoSinDocumento", "Motivo sin documento", { it.motivoSinDocumento }, sinCambio),
        Def("nombres", "Nombres", { it.nombres }, sinCambio),
        Def("apellidos", "Apellidos", { it.apellidos }, sinCambio),
        Def("razonSocial", "Razón social", { it.razonSocial }, sinCambio),
        Def("fechaNacimiento", "Fecha de nacimiento", { it.fechaNacimiento?.toString() }) { _, v -> v?.let { fecha(LocalDate.parse(it)) } },
        Def("nacionalidad", "Nacionalidad", { it.nacionalidad }) { c, v -> c.resolverTabla(CatalogoExportacion.PAIS, v) },
        Def("telefono", "Teléfono", { it.telefono }, sinCambio),
        Def("ocupacion", "Ocupación", { it.profesionOcupacion }) { c, v -> c.resolverTabla(CatalogoExportacion.OCUPACION, v) },
        Def("estadoCivil", "Estado civil", { it.estadoCivil }) { c, v -> c.nombre(CatalogosOperacion.ESTADO_CIVIL, v) },
        Def("domicilio", "Domicilio", { it.domicilio }, sinCambio),
        Def("codigoPostal", "Código postal", { it.codigoPostal }, sinCambio),
        Def("departamento", "Departamento", { it.departamento }) { c, v -> c.resolverTabla(CatalogoExportacion.DEPARTAMENTO, v) },
        Def("provincia", "Provincia", { it.provincia }) { c, v -> c.resolverTabla(CatalogoExportacion.PROVINCIA, v) },
        Def("distrito", "Distrito", { it.distrito }, sinCambio),
        Def("representanteLegal", "Representante legal", { it.representanteLegal }, sinCambio),
        Def("representanteTipoDocumento", "Tipo de documento del representante", { it.representanteTipoDocumento }) { c, v -> c.nombreDocumento(v) },
        Def("representanteNumeroDocumento", "N.º de documento del representante", { it.representanteNumeroDocumento }, sinCambio),
        Def("accionistasMayores25", "Accionistas con más del 25%", { it.accionistasMayores25 }, sinCambio),
        Def("domicilioFiscal", "Domicilio fiscal", { it.domicilioFiscal }, sinCambio)
    )

    fun campos(registro: RegistroOperacionesEntity, cat: Catalogos): List<CampoFoto> {
        val lista = mutableListOf<CampoFoto>()
        fun agregar(campo: String, etiqueta: String, valor: String?, legible: String?) {
            lista.add(CampoFoto(campo, etiqueta, limpio(valor), limpio(legible)))
        }
        agregar("registro.oficina", "Oficina", registro.oficina, registro.oficina)

        fun persona(prefijo: String, etiquetaPrefijo: String, p: Persona?, representada: Boolean) {
            PERSONA.forEach { d ->
                // tipoPersona solo tiene sentido en la representada (un registro viejo sin el dato es NATURAL)
                if (!representada && d.nombre == "tipoPersona") return@forEach
                val valor = p?.let { persona ->
                    if (d.nombre == "tipoPersona") persona.tipoPersona ?: CatalogosOperacion.NATURAL else d.leer(persona)
                }
                agregar("$prefijo.${d.nombre}", etiquetaPrefijo + d.etiqueta, valor, d.legible(cat, limpio(valor)))
            }
        }
        persona("personaFisica", "", registro.personaFisica, false)
        persona("personaRepresentada", "Representada · ", registro.personaRepresentada, true)

        val o = registro.operacion
        agregar("operacion.fechaOperacion", "Fecha de la operación", o.fechaOperacion.toString(), fecha(o.fechaOperacion))
        agregar("operacion.lugar", "Lugar", o.lugar, o.lugar)
        agregar("operacion.tipoOperacion", "Tipo de operación", o.tipoOperacion, cat.nombre(CatalogosOperacion.TIPO_OPERACION, o.tipoOperacion))
        agregar("operacion.modalidadPago", "Modalidad de pago", o.modalidadPago, cat.nombre(CatalogosOperacion.MODALIDAD_PAGO, o.modalidadPago))
        agregar("operacion.modalidadPagoTexto", "Modalidad de pago (otro)", o.modalidadPagoTexto, o.modalidadPagoTexto)
        agregar("operacion.moneda", "Moneda", o.moneda, cat.moneda(o.moneda)?.codigo ?: o.moneda)
        agregar("operacion.monto", "Monto", o.monto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), monto(o.monto))
        agregar("operacion.origenFondos", "Origen de los fondos", o.origenFondos, cat.nombre(CatalogosOperacion.ORIGEN_FONDOS, o.origenFondos))
        agregar("operacion.origenFondosTexto", "Origen de los fondos (otro)", o.origenFondosTexto, o.origenFondosTexto)
        agregar("operacion.numeroComprobante", "N.º de comprobante", o.numeroComprobante, o.numeroComprobante)
        agregar("operacion.cuenta1", "Cuenta involucrada", o.cuenta1, o.cuenta1)
        agregar("operacion.cuenta2", "Cuenta 2", o.cuenta2, o.cuenta2)
        agregar("operacion.cuenta3", "Cuenta 3", o.cuenta3, o.cuenta3)
        agregar("operacion.descripcion", "Descripción", o.descripcion, o.descripcion)

        registro.beneficiarios.forEachIndexed { i, b ->
            val e = "Beneficiario ${i + 1} · "
            agregar("beneficiarios[$i].nombre", e + "Nombre", b.nombre, b.nombre)
            agregar("beneficiarios[$i].tipoDocumento", e + "Tipo de documento", b.tipoDocumento, cat.nombreDocumento(b.tipoDocumento))
            agregar("beneficiarios[$i].numeroDocumento", e + "N.º de documento", b.numeroDocumento, b.numeroDocumento)
            agregar("beneficiarios[$i].fechaNacimiento", e + "Fecha de nacimiento", b.fechaNacimiento?.toString(), fecha(b.fechaNacimiento))
        }
        return lista
    }

    /** Valores guardados por campo (para aceptar tal cual lo que no cambia al editar). */
    fun valores(campos: List<CampoFoto>): Map<String, String?> = campos.associate { it.campo to it.valor }

    /**
     * Cambios entre dos fotos, comparando lo legible: un id viejo ("15") y su nombre ("Lima") no cuentan como cambio.
     * Un beneficiario agregado tiene `antes` vacio; uno quitado, `despues` vacio.
     */
    fun diferencias(antes: List<CampoFoto>, despues: List<CampoFoto>): List<CambioCampo> {
        val previo = antes.associateBy { it.campo }
        val nuevo = despues.associateBy { it.campo }
        val orden = (despues.map { it.campo } + antes.map { it.campo }).distinct()
        return orden.mapNotNull { campo ->
            val a = previo[campo]
            val d = nuevo[campo]
            if (a?.legible == d?.legible) null
            else CambioCampo(campo, (d ?: a)!!.etiqueta, a?.legible, d?.legible)
        }
    }

    /** SHA-256 (hexadecimal) del contenido completo del registro: numero, empresa, fecha y cada campo guardado. */
    fun huella(registro: RegistroOperacionesEntity, campos: List<CampoFoto>): String {
        val texto = buildString {
            append("numero=").append(registro.numeroRegistro.orEmpty()).append('\n')
            append("empresa=").append(registro.empresa).append('\n')
            append("fechaRegistro=").append(registro.fechaRegistro).append('\n')
            campos.forEach { append(it.campo).append('=').append(it.valor.orEmpty()).append('\n') }
        }
        return sha256(texto)
    }

    fun sha256(texto: String): String =
        MessageDigest.getInstance("SHA-256").digest(texto.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun limpio(v: String?): String? = v?.trim()?.takeIf { it.isNotEmpty() }
}
