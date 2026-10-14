package com.security.modules.operaciones.controller.infrastructure
import com.security.modules.operaciones.dto.RegistrarOperacionResponse

import com.security.modules.operaciones.application.service.OperacionService
import com.security.modules.operaciones.dto.ListarOperacionesResponse
import com.security.modules.operaciones.dto.MostrarUsuarioResponse
import com.tuempresa.operaciones.dto.RegistrarOperacionRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
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


    @GetMapping("/resumen")
    fun listarResumen(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        authentication: Authentication // Inyecta la autenticación actual de Spring Security
    ): ListarOperacionesResponse {
        // authentication.name contiene el username (ej. "lavado_pru3")
        val username = authentication.name

        return operacionService.obtenerResumenPaginado(page, size, username)
    }

    @GetMapping("/obtener-usuario")
    fun obtenerUsuario(@RequestParam("usuario") usuario: String): MostrarUsuarioResponse {
        return operacionService.obtenerUsuarioActual(usuario)
    }

    @PostMapping("/insertar-operacion")
    fun registrar(@Valid @RequestBody request: RegistrarOperacionRequest): ResponseEntity<RegistrarOperacionResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(operacionService.registrar(request))
    }






}
