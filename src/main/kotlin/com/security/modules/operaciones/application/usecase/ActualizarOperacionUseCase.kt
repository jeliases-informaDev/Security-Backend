package com.security.modules.operaciones.application.usecase

import com.security.modules.operaciones.application.port.out.ActualizarOperacionPort
import com.security.modules.operaciones.dto.*
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.RoundingMode

@Service
class ActualizarOperacionUseCase(private val actualizacion: ActualizarOperacionPort) {
    fun ejecutar(id: Long, request: ActualizarOperacionRequest): RegistrarOperacionResponse {
        if (id <= 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID debe ser mayor que cero")
        val monto = try { request.operacion.monto.setScale(2, RoundingMode.UNNECESSARY) }
        catch (_: ArithmeticException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto admite hasta dos decimales")
        }
        if (monto.signum() < 0 || monto.precision() > 15) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Monto fuera del rango permitido")
        }
        return actualizacion.actualizar(id, request.copy(operacion = request.operacion.copy(monto = monto)))
            ?: throw ResourceNotFoundException("No existe un registro de operación con ID $id")
    }
}
