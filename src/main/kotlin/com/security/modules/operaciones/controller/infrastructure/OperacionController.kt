package com.security.modules.operaciones.controller.infrastructure
import com.security.modules.operaciones.dto.RegistrarOperacionResponse

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

@RestController
@RequestMapping("/api/operaciones")
class OperacionController(
    private val operacionService: OperacionService
) {


    @PatchMapping("/{id}/usuario")
    fun cambiarUsuarioOperacion(
        @PathVariable("id") id: Long,
        @Valid @RequestBody request: CambiarUsuarioOperacionRequest
    ): CambiarUsuarioOperacionResponse =
        operacionService.cambiarUsuarioOperacion(id, request.usuarioId)

    @GetMapping("/usuarios-por-empresa")
    fun listarUsuariosPorEmpresa(
        @RequestParam("empresa") empresa: String
    ): List<UsuarioEmpresaResponse> =
        operacionService.listarUsuariosPorEmpresa(empresa)

    @GetMapping("/resumen")
    fun listarResumen(
        @RequestParam("empresa") empresa: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ListarOperacionesResponse =
        operacionService.obtenerResumenPaginado(page, size, empresa)

    @GetMapping("/obtener-usuario")
    fun obtenerUsuario(@RequestParam("usuario") usuario: String): MostrarUsuarioResponse {
        return operacionService.obtenerUsuarioActual(usuario)
    }

    @PostMapping("/insertar-operacion")
    fun registrar(@Valid @RequestBody request: RegistrarOperacionRequest): ResponseEntity<RegistrarOperacionResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(operacionService.registrar(request))
    }






}
