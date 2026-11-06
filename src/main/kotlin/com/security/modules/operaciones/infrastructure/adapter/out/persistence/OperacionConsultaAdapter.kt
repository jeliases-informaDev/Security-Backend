package com.security.modules.operaciones.infrastructure.adapter.out.persistence

import com.security.modules.operaciones.application.service.Catalogos
import com.security.modules.operaciones.application.service.CatalogosOperacion
import com.security.modules.operaciones.application.service.CatalogosRegistro
import com.security.modules.operaciones.application.service.FotoOperacion
import com.security.modules.operaciones.application.service.nombreCompleto
import com.security.modules.operaciones.domain.Persona
import com.security.modules.operaciones.domain.model.*
import com.security.modules.operaciones.domain.port.out.OperacionConsultaPort
import com.security.modules.operaciones.infraestructure.CatalogoExportacion
import com.security.modules.operaciones.infraestructure.CatalogoExportacion.*
import com.security.modules.operaciones.infraestructure.CatalogoRepository
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.repositories.UsuarioRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Arma los datos de un registro para el detalle y el PDF: los ids viejos de las tablas de siempre se resuelven a su
 * nombre (sin tocar lo guardado) y los codigos del formulario nuevo traen su nombre en los campos `…Nombre`.
 */
@Component
class OperacionConsultaAdapter(
    private val operacionRepository: OperacionRepository,
    private val catalogoRepository: CatalogoRepository,
    private val catalogosRegistro: CatalogosRegistro? = null,
    private val usuarioRepository: UsuarioRepository? = null
) : OperacionConsultaPort {

    @Transactional(readOnly = true)
    override fun obtenerParaExportacion(id: Long): OperacionExportacion? {
        val registro = operacionRepository.findById(id).orElse(null) ?: return null
        val cat = catalogosRegistro?.cargar() ?: catalogosSoloTablas()
        fun resolver(catalogo: CatalogoExportacion, valor: String?): String? = cat.resolverTabla(catalogo, valor) ?: valor

        val datos = registro.operacion
        val moneda = cat.moneda(datos.moneda)
        val duenio = usuarioRepository?.findById(registro.idUsuario)?.orElse(null)
        return OperacionExportacion(
            id = requireNotNull(registro.id),
            codigoOperacion = registro.numeroRegistro.orEmpty(),
            registro = RegistroExportacion(
                usuarioId = registro.idUsuario.toString(),
                usuario = operacionRepository.buscarNombreUsuarioPorId(registro.idUsuario).orEmpty(),
                empresa = registro.empresa, oficina = registro.oficina, fechaRegistro = registro.fechaRegistro,
                usuarioNombre = duenio?.let { nombreCompleto(it) },
                creadoEn = registro.fechaCreacion
            ),
            personaFisica = registro.personaFisica.exportacion(cat, ::resolver),
            personaRepresentada = registro.personaRepresentada?.exportacion(cat, ::resolver),
            operacion = DatosOperacionExportacion(
                fechaOperacion = datos.fechaOperacion, lugar = datos.lugar, modalidadPago = datos.modalidadPago,
                moneda = resolver(MONEDA, datos.moneda).orEmpty(), monto = datos.monto, tipoOperacion = datos.tipoOperacion,
                cuenta1 = datos.cuenta1, cuenta2 = datos.cuenta2, cuenta3 = datos.cuenta3,
                tipoOperacionNombre = cat.nombre(CatalogosOperacion.TIPO_OPERACION, datos.tipoOperacion),
                modalidadPagoNombre = cat.nombre(CatalogosOperacion.MODALIDAD_PAGO, datos.modalidadPago),
                modalidadPagoTexto = datos.modalidadPagoTexto,
                monedaCodigo = moneda?.codigo,
                monedaSimbolo = moneda?.simbolo,
                origenFondos = datos.origenFondos,
                origenFondosNombre = cat.nombre(CatalogosOperacion.ORIGEN_FONDOS, datos.origenFondos),
                origenFondosTexto = datos.origenFondosTexto,
                numeroComprobante = datos.numeroComprobante,
                descripcion = datos.descripcion
            ),
            beneficiarios = registro.beneficiarios.map {
                BeneficiarioExportacion(
                    it.nombre, resolver(DOCUMENTO, it.tipoDocumento).orEmpty(), it.numeroDocumento, it.fechaNacimiento,
                    cat.nombreDocumento(it.tipoDocumento)
                )
            },
            idUsuarioResponsable = registro.idUsuarioResponsable,
            huella = FotoOperacion.huella(registro, FotoOperacion.campos(registro, cat))
        )
    }

    /** Sin el servicio de catalogos (pruebas armadas a mano): solo los ids viejos de las tablas. */
    private fun catalogosSoloTablas(): Catalogos {
        val nombres = mutableMapOf<Pair<CatalogoExportacion, Long>, String?>()
        fun resolver(catalogo: CatalogoExportacion, valor: String?): String? {
            val id = valor?.trim()?.toLongOrNull() ?: return valor
            val clave = catalogo to id
            if (!nombres.containsKey(clave)) nombres[clave] = catalogoRepository.nombreParaExportacion(catalogo, id)
            return nombres[clave] ?: valor
        }
        return Catalogos(emptyList(), emptyList(), ::resolver)
    }

    private fun Persona.exportacion(cat: Catalogos, resolver: (CatalogoExportacion, String?) -> String?) = PersonaExportacion(
        tipoDocumento = resolver(DOCUMENTO, tipoDocumento).orEmpty(),
        numeroDocumento = numeroDocumento.orEmpty(),
        nombres = nombres.orEmpty(),
        apellidos = apellidos.orEmpty(),
        fechaNacimiento = fechaNacimiento,
        nacionalidad = resolver(PAIS, nacionalidad),
        telefono = telefono,
        ocupacion = resolver(OCUPACION, profesionOcupacion),
        domicilio = domicilio,
        codigoPostal = codigoPostal,
        departamento = resolver(DEPARTAMENTO, departamento),
        provincia = resolver(PROVINCIA, provincia),
        distrito = distrito,
        tipoDocumentoNombre = cat.nombreDocumento(tipoDocumento),
        motivoSinDocumento = motivoSinDocumento,
        estadoCivil = estadoCivil,
        estadoCivilNombre = cat.nombre(CatalogosOperacion.ESTADO_CIVIL, estadoCivil),
        tipoPersona = tipoPersona ?: CatalogosOperacion.NATURAL,
        razonSocial = razonSocial,
        representanteLegal = representanteLegal,
        representanteTipoDocumento = representanteTipoDocumento,
        representanteTipoDocumentoNombre = cat.nombreDocumento(representanteTipoDocumento),
        representanteNumeroDocumento = representanteNumeroDocumento,
        accionistasMayores25 = accionistasMayores25,
        domicilioFiscal = domicilioFiscal
    )
}
