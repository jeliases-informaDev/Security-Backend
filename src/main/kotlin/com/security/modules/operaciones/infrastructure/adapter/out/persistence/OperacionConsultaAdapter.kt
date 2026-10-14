
package com.security.modules.operaciones.infrastructure.adapter.out.persistence

import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.modules.operaciones.domain.model.*
import com.security.modules.operaciones.domain.Persona
import com.security.modules.operaciones.domain.port.out.OperacionConsultaPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OperacionConsultaAdapter(
    private val operacionRepository: OperacionRepository
) : OperacionConsultaPort {

    @Transactional(readOnly = true)
    override fun obtenerParaExportacion(
        id: Long
    ): OperacionExportacion? {

        // 1. Consultar la operación usando el ID.
        val registro = operacionRepository.findById(id).orElse(null) ?: return null

        // 2. Aquí se debe construir OperacionExportacion con
        // todos los datos del registro, las personas, la operación
        // y los beneficiarios, usando tus entidades reales.

        val datos = registro.operacion
        return OperacionExportacion(
            id = requireNotNull(registro.id),
            codigoOperacion = registro.numeroRegistro.orEmpty(),
            registro = RegistroExportacion(
                registro.idUsuario.toString(),
                operacionRepository.buscarNombreUsuarioPorId(registro.idUsuario).orEmpty(),
                registro.empresa, registro.oficina, registro.fechaRegistro
            ),
            personaFisica = registro.personaFisica.exportacion(),
            personaRepresentada = registro.personaRepresentada?.exportacion(),
            operacion = DatosOperacionExportacion(
                datos.fechaOperacion, datos.lugar, datos.modalidadPago, datos.moneda,
                datos.monto, datos.tipoOperacion, datos.cuenta1, datos.cuenta2, datos.cuenta3
            ),
            beneficiarios = registro.beneficiarios.map {
                BeneficiarioExportacion(it.nombre, it.tipoDocumento, it.numeroDocumento, it.fechaNacimiento)
            }
        )
    }

    private fun Persona.exportacion() = PersonaExportacion(
        tipoDocumento.orEmpty(), numeroDocumento.orEmpty(), nombres.orEmpty(), apellidos.orEmpty(),
        fechaNacimiento, nacionalidad, telefono, profesionOcupacion, domicilio,
        codigoPostal, departamento, provincia, distrito
    )
}
