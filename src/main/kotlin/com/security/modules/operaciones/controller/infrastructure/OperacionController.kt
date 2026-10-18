package com.security.modules.operaciones.controller.infrastructure
import com.security.modules.operaciones.dto.RegistrarOperacionResponse

import com.security.modules.operaciones.application.service.AccesoEmpresaService
import com.security.modules.operaciones.application.service.OperacionService
import com.security.modules.operaciones.dto.ListarOperacionesResponse
import com.security.modules.operaciones.dto.MostrarUsuarioResponse
import com.tuempresa.operaciones.dto.RegistrarOperacionRequest
import com.security.modules.operaciones.dto.UsuarioEmpresaResponse
import com.security.modules.operaciones.dto.CambiarUsuarioOperacionRequest
import com.security.modules.operaciones.dto.CambiarUsuarioOperacionResponse
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * La empresa y el usuario con los que se trabaja los decide AccesoEmpresaService a partir de la cuenta
 * autenticada: los parametros "empresa" y "usuario" solo los puede usar un ADMINISTRADOR.
 */
@RestController
@RequestMapping("/api/operaciones")
class OperacionController(
    private val operacionService: OperacionService,
    private val acceso: AccesoEmpresaService
) {

    @PatchMapping("/{id}/usuario")
    fun cambiarUsuarioOperacion(
        @PathVariable("id") id: Long,
        @Valid @RequestBody request: CambiarUsuarioOperacionRequest
    ): CambiarUsuarioOperacionResponse {
        acceso.comprobarRegistro(id)
        return operacionService.cambiarUsuarioOperacion(id, request.usuarioId)
    }

    @GetMapping("/usuarios-por-empresa")
    fun listarUsuariosPorEmpresa(
        @RequestParam("empresa", required = false) empresa: String?
    ): List<UsuarioEmpresaResponse> =
        operacionService.listarUsuariosPorEmpresa(acceso.empresaPermitida(empresa))

    @GetMapping("/resumen")
    fun listarResumen(
        @RequestParam("empresa", required = false) empresa: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ListarOperacionesResponse =
        operacionService.obtenerResumenPaginado(page, size, acceso.empresaPermitida(empresa))

    @GetMapping("/obtener-usuario")
    fun obtenerUsuario(@RequestParam("usuario", required = false) usuario: String?): MostrarUsuarioResponse {
        return operacionService.obtenerUsuarioActual(acceso.usuarioPermitido(usuario))
    }

    @PostMapping("/insertar-operacion")
    fun registrar(@Valid @RequestBody request: RegistrarOperacionRequest): ResponseEntity<RegistrarOperacionResponse> {
        val propio = request.copy(registro = acceso.registroDelActor(request.registro))
        return ResponseEntity.status(HttpStatus.CREATED).body(operacionService.registrar(propio))
    }
}
