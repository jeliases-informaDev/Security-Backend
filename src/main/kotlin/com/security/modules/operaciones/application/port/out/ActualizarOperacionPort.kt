package com.security.modules.operaciones.application.port.out

import com.security.modules.operaciones.dto.ActualizarOperacionRequest
import com.security.modules.operaciones.dto.RegistrarOperacionResponse

interface ActualizarOperacionPort {
    fun actualizar(id: Long, request: ActualizarOperacionRequest): RegistrarOperacionResponse?
}
