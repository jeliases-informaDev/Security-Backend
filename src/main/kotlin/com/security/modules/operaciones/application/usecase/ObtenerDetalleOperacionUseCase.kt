package com.security.modules.operaciones.application.usecase

import com.security.modules.operaciones.domain.port.out.OperacionConsultaPort
import com.security.modules.operaciones.dto.DetalleOperacionResponse
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ObtenerDetalleOperacionUseCase(private val consulta: OperacionConsultaPort) {
    fun ejecutar(id: Long): DetalleOperacionResponse {
        if (id <= 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID debe ser mayor que cero")
        val datos = consulta.obtenerParaExportacion(id)
            ?: throw ResourceNotFoundException("No existe un registro de operación con ID $id")
        return DetalleOperacionResponse(
            datos.id, datos.codigoOperacion, datos.registro, datos.personaFisica,
            datos.personaRepresentada, datos.operacion, datos.beneficiarios
        )
    }
}
