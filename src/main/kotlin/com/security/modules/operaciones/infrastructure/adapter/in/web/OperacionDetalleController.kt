package com.security.modules.operaciones.infrastructure.adapter.`in`.web

import com.security.modules.operaciones.application.service.AccesoEmpresaService
import com.security.modules.operaciones.application.service.CodigosOperaciones
import com.security.modules.operaciones.application.service.EdicionOperacionService
import com.security.modules.operaciones.application.service.HistorialOperacionService
import com.security.modules.operaciones.application.usecase.ObtenerDetalleOperacionUseCase
import com.security.modules.operaciones.dto.ActualizarOperacionRequest
import com.security.modules.operaciones.dto.DetalleOperacionResponse
import com.security.modules.operaciones.dto.EdicionOperacionResponse
import com.security.modules.operaciones.dto.HistorialOperacionResponse
import com.security.modules.operaciones.infraestructure.OperacionRepository
import org.springframework.web.bind.annotation.*

/** Detalle (RO 5), edicion con motivo (RO 6) e historial (RO 7). Solo registros de la propia empresa (el administrador universal, cualquiera). */
@RestController
@RequestMapping("/api/operaciones")
class OperacionDetalleController(
    private val obtenerDetalle: ObtenerDetalleOperacionUseCase,
    private val edicion: EdicionOperacionService,
    private val acceso: AccesoEmpresaService,
    private val historial: HistorialOperacionService,
    private val operaciones: OperacionRepository
) {

    @GetMapping("/{id}")
    fun obtener(@PathVariable("id") id: Long): DetalleOperacionResponse {
        acceso.comprobarRegistro(id)
        return obtenerDetalle.ejecutar(id)
    }

    // No consume tokens. Motivo obligatorio; los cambios quedan en el historial.
    @PutMapping("/{id}")
    fun actualizar(@PathVariable("id") id: Long, @RequestBody request: ActualizarOperacionRequest): EdicionOperacionResponse {
        acceso.comprobarRegistro(id)
        return edicion.editar(id, request, acceso.actor().autor())
    }

    @GetMapping("/{id}/historial")
    fun historial(@PathVariable("id") id: Long, @RequestParam(required = false) tipo: String?): HistorialOperacionResponse {
        acceso.comprobarRegistro(id)
        val registro = operaciones.findById(id).orElse(null) ?: throw CodigosOperaciones.noEncontrada(id)
        return historial.historial(registro, tipo)
    }
}
