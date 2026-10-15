
package com.security.modules.operaciones.infrastructure.adapter.out.persistence

import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.modules.operaciones.infraestructure.CatalogoRepository
import com.security.modules.operaciones.infraestructure.CatalogoExportacion
import com.security.modules.operaciones.infraestructure.CatalogoExportacion.*
import com.security.modules.operaciones.domain.model.*
import com.security.modules.operaciones.domain.Persona
import com.security.modules.operaciones.domain.port.out.OperacionConsultaPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OperacionConsultaAdapter(
    private val operacionRepository: OperacionRepository,
    private val catalogoRepository: CatalogoRepository
) : OperacionConsultaPort {

    @Transactional(readOnly = true)
    override fun obtenerParaExportacion(
        id: Long
    ): OperacionExportacion? {

        // 1. Consultar la operación usando el ID.
        val registro = operacionRepository.findById(id).orElse(null) ?: return null

        // Cache local: no repite consultas para codigos usados por varias personas.
        val nombres = mutableMapOf<Pair<CatalogoExportacion, Long>, String?>()
        fun resolver(catalogo: CatalogoExportacion, valor: String?): String? {
            val idCatalogo = valor?.trim()?.toLongOrNull() ?: return valor
            val clave = catalogo to idCatalogo
            if (!nombres.containsKey(clave)) {
                nombres[clave] = catalogoRepository.nombreParaExportacion(catalogo, idCatalogo)
            }
            return nombres[clave]?.takeIf { it.isNotBlank() } ?: valor
        }

        val datos = registro.operacion
        return OperacionExportacion(
            id = requireNotNull(registro.id),
            codigoOperacion = registro.numeroRegistro.orEmpty(),
            registro = RegistroExportacion(
                registro.idUsuario.toString(),
                operacionRepository.buscarNombreUsuarioPorId(registro.idUsuario).orEmpty(),
                registro.empresa, registro.oficina, registro.fechaRegistro
            ),
            personaFisica = registro.personaFisica.exportacion(::resolver),
            personaRepresentada = registro.personaRepresentada?.exportacion(::resolver),
            operacion = DatosOperacionExportacion(
                datos.fechaOperacion, datos.lugar, datos.modalidadPago, resolver(MONEDA, datos.moneda).orEmpty(),
                datos.monto, datos.tipoOperacion, datos.cuenta1, datos.cuenta2, datos.cuenta3
            ),
            beneficiarios = registro.beneficiarios.map {
                BeneficiarioExportacion(it.nombre, resolver(DOCUMENTO, it.tipoDocumento).orEmpty(), it.numeroDocumento, it.fechaNacimiento)
            }
        )
    }

    private fun Persona.exportacion(resolver: (CatalogoExportacion, String?) -> String?) = PersonaExportacion(
        resolver(DOCUMENTO, tipoDocumento).orEmpty(), numeroDocumento.orEmpty(), nombres.orEmpty(), apellidos.orEmpty(),
        fechaNacimiento, resolver(PAIS, nacionalidad), telefono, resolver(OCUPACION, profesionOcupacion), domicilio,
        codigoPostal, resolver(DEPARTAMENTO, departamento), resolver(PROVINCIA, provincia), distrito
    )
}
