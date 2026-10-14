package com.security.modules.operaciones.infrastructure.adapter.`in`.web

import com.security.modules.operaciones.application.usecase.ObtenerDetalleOperacionUseCase
import com.security.modules.operaciones.application.usecase.ActualizarOperacionUseCase
import com.security.modules.operaciones.dto.ActualizarOperacionRequest
import com.security.modules.operaciones.dto.RegistrarOperacionResponse
import jakarta.validation.Valid
import com.security.modules.operaciones.dto.DetalleOperacionResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/operaciones")
class OperacionDetalleController(
    private val obtenerDetalle: ObtenerDetalleOperacionUseCase,
    private val actualizarOperacion: ActualizarOperacionUseCase
) {
    @GetMapping("/{id}")
    fun obtener(@PathVariable("id") id: Long): DetalleOperacionResponse = obtenerDetalle.ejecutar(id)

    @PutMapping("/{id}")
    fun actualizar(@PathVariable("id") id: Long, @Valid @RequestBody request: ActualizarOperacionRequest): RegistrarOperacionResponse =
        actualizarOperacion.ejecutar(id, request)
}
