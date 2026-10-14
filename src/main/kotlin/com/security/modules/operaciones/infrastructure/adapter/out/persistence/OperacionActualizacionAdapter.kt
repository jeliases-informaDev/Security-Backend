package com.security.modules.operaciones.infrastructure.adapter.out.persistence

import com.security.modules.operaciones.application.port.out.ActualizarOperacionPort
import com.security.modules.operaciones.domain.*
import com.security.modules.operaciones.dto.*
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.tuempresa.operaciones.dto.PersonaDto
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OperacionActualizacionAdapter(
    private val repository: OperacionRepository,
    private val em: EntityManager
) : ActualizarOperacionPort {
    @Transactional
    override fun actualizar(id: Long, request: ActualizarOperacionRequest): RegistrarOperacionResponse? {
        val registro = repository.findById(id).orElse(null) ?: return null
        registro.oficina = request.registro.oficina
        registro.fechaRegistro = request.registro.fechaRegistro
        registro.personaFisica.actualizar(request.personaFisica)
        val anterior = registro.personaRepresentada
        registro.personaRepresentada = request.personaRepresentada?.let { dto ->
            (anterior ?: Persona()).also {
                it.actualizar(dto)
                if (anterior == null) em.persist(it)
            }
        }
        val datos = request.operacion
        registro.operacion.apply {
            fechaOperacion = datos.fechaOperacion
            lugar = datos.lugar
            modalidadPago = datos.modalidadPago
            moneda = datos.moneda
            monto = datos.monto
            tipoOperacion = datos.tipoOperacion
            cuenta1 = datos.cuenta1
            cuenta2 = datos.cuenta2
            cuenta3 = datos.cuenta3
        }
        // PUT reemplaza la lista completa: orphanRemoval elimina los anteriores.
        registro.beneficiarios.clear()
        request.beneficiarios.forEach {
            val beneficiario = BeneficiarioEntity(nombre = it.nombre, tipoDocumento = it.tipoDocumento,
                numeroDocumento = it.numeroDocumento, fechaNacimiento = it.fechaNacimiento)
            em.persist(beneficiario)
            registro.beneficiarios.add(beneficiario)
        }
        repository.flush()
        if (anterior != null && request.personaRepresentada == null) {
            em.remove(anterior)
            repository.flush()
        }
        return RegistrarOperacionResponse(requireNotNull(registro.id), requireNotNull(registro.numeroRegistro),
            "Operacion actualizada correctamente")
    }

    private fun Persona.actualizar(dto: PersonaDto) {
        tipoDocumento = dto.tipoDocumento
        numeroDocumento = dto.numeroDocumento
        nombres = dto.nombres
        apellidos = dto.apellidos
        fechaNacimiento = dto.fechaNacimiento
        nacionalidad = dto.nacionalidad
        telefono = dto.telefono
        profesionOcupacion = dto.ocupacion
        domicilio = dto.domicilio
        codigoPostal = dto.codigoPostal
        departamento = dto.departamento
        provincia = dto.provincia
        distrito = dto.distrito
    }
}
