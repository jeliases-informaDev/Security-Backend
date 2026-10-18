package com.security.modules.usuarios.controller.infraestructure

import com.security.modules.usuarios.application.service.CargaMasivaService
import com.security.modules.usuarios.application.service.UsuarioService
import com.security.modules.usuarios.dto.CargaMasivaRequest
import com.security.modules.usuarios.dto.CargaMasivaResponse
import com.security.modules.usuarios.dto.InvitacionResponse
import com.security.modules.usuarios.dto.CambiarEmpresaRequest
import com.security.modules.usuarios.dto.CambiarRolRequest
import com.security.modules.usuarios.dto.RolResponse
import com.security.modules.usuarios.dto.UsuarioCreateRequest
import com.security.modules.usuarios.dto.UsuarioResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import com.security.shared.security.esAdministrador

@RestController
@RequestMapping("/api/usuarios")
class UsuarioController(
    private val usuarioService: UsuarioService,
    private val cargaMasivaService: CargaMasivaService
) {

    // Un ADMINISTRADOR ve todas las cuentas; cualquier otra cuenta solo las de su propia empresa
    // (los clientes no deben saber unos de otros).
    @GetMapping
    fun listar(authentication: Authentication): ResponseEntity<List<UsuarioResponse>> {
        val cuentas = if (authentication.esAdministrador()) usuarioService.listar()
        else usuarioService.listarDeMiEmpresa(authentication.name)
        return ResponseEntity.ok(cuentas)
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
    @PutMapping("/{id}/empresa")
    fun cambiarEmpresa(
        @PathVariable id: Int,
        @Valid @RequestBody request: CambiarEmpresaRequest
    ): ResponseEntity<UsuarioResponse> {
        return ResponseEntity.ok(usuarioService.cambiarEmpresa(id, request))
    }

    // Alta por lotes para una empresa (soloValidar = true revisa sin crear). Todo o nada.
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/carga-masiva")
    fun cargaMasiva(@Valid @RequestBody request: CargaMasivaRequest): ResponseEntity<CargaMasivaResponse> {
        return ResponseEntity.ok(cargaMasivaService.cargar(request))
    }

    // Manda (o vuelve a mandar) el enlace para que la persona elija su clave.
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/{id}/invitar")
    fun invitar(@PathVariable id: Int): ResponseEntity<InvitacionResponse> {
        return ResponseEntity.ok(cargaMasivaService.invitar(id))
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @GetMapping("/roles")
    fun listarRoles(): ResponseEntity<List<RolResponse>> {
        return ResponseEntity.ok(usuarioService.listarRoles())
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
