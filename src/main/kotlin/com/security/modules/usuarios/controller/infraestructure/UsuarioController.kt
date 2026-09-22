package com.security.modules.usuarios.controller.infraestructure

import com.security.modules.usuarios.application.service.UsuarioService
import com.security.modules.usuarios.dto.CambiarRolRequest
import com.security.modules.usuarios.dto.UsuarioCreateRequest
import com.security.modules.usuarios.dto.UsuarioResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/usuarios")
class UsuarioController(
    private val usuarioService: UsuarioService
) {

    // Disponible para cualquier usuario autenticado (todos los roles la tienen asignada)
    @GetMapping
    fun listar(): ResponseEntity<List<UsuarioResponse>> {
        return ResponseEntity.ok(usuarioService.listar())
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping
    fun crear(@Valid @RequestBody request: UsuarioCreateRequest): ResponseEntity<UsuarioResponse> {
        val creado = usuarioService.crear(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(creado)
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/{id}/rol")
    fun cambiarRol(
        @PathVariable id: Int,
        @Valid @RequestBody request: CambiarRolRequest
    ): ResponseEntity<UsuarioResponse> {
        return ResponseEntity.ok(usuarioService.cambiarRol(id, request))
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PatchMapping("/{id}/estado")
    fun cambiarEstado(
        @PathVariable id: Int,
        @RequestParam activo: Boolean
    ): ResponseEntity<UsuarioResponse> {
        return ResponseEntity.ok(usuarioService.cambiarEstado(id, activo))
    }
}
