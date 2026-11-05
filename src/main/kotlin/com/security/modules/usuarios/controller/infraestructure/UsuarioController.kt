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
import com.security.shared.security.VisibilidadEmpresaService
import org.springframework.web.server.ResponseStatusException



@RestController
@RequestMapping("/api/usuarios")

class UsuarioController(
    private val visibilidadService: VisibilidadEmpresaService,
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

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'ADMINISTRADOR_EMPRESA')")
    @PostMapping
    fun crear(@Valid @RequestBody request: UsuarioCreateRequest, authentication: Authentication): ResponseEntity<UsuarioResponse> {
        val requestAjustado = if (authentication.esAdministrador()) {
            request
        } else {
            val vista = visibilidadService.de(authentication.name)
            val miEmpresa = vista.empresa ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario sin empresa asignada")
            if (request.rolCodigo.trim().uppercase() == "ADMINISTRADOR") {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "No puede crear usuarios con rol ADMINISTRADOR universal")
            }
            request.copy(empresa = miEmpresa)
        }
        val creado = usuarioService.crear(requestAjustado, authentication.esAdministrador())
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
        @Valid @RequestBody request: CambiarEmpresaRequest,
        authentication: Authentication
    ): ResponseEntity<UsuarioResponse> {
        return ResponseEntity.ok(usuarioService.cambiarEmpresa(id, request, authentication.esAdministrador()))
    }

    // Alta por lotes para una empresa (soloValidar = true revisa sin crear). Todo o nada.
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/carga-masiva")
    fun cargaMasiva(
        @Valid @RequestBody request: CargaMasivaRequest,
        authentication: Authentication
    ): ResponseEntity<CargaMasivaResponse> {
        return ResponseEntity.ok(cargaMasivaService.cargar(request, authentication.esAdministrador()))
    }

    // Manda (o vuelve a mandar) el enlace para que la persona elija su clave.
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'ADMINISTRADOR_EMPRESA')")
    @PostMapping("/{id}/invitar")
    fun invitar(@PathVariable id: Int): ResponseEntity<InvitacionResponse> {
        return ResponseEntity.ok(cargaMasivaService.invitar(id))
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'ADMINISTRADOR_EMPRESA')")
    @GetMapping("/roles")
    fun listarRoles(authentication: Authentication): ResponseEntity<List<RolResponse>> {
        val roles = usuarioService.listarRoles()
        return if (authentication.esAdministrador()) {
            ResponseEntity.ok(roles)
        } else {
            ResponseEntity.ok(roles.filter { it.codigo != "ADMINISTRADOR" })
        }
    }

    // Un administrador de empresa solo cambia el estado de las cuentas de su propia empresa (lo revisa el servicio).
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'ADMINISTRADOR_EMPRESA')")
    @PatchMapping("/{id}/estado")
    fun cambiarEstado(
        @PathVariable id: Int,
        @RequestParam activo: Boolean,
        authentication: Authentication
    ): ResponseEntity<UsuarioResponse> {
        return ResponseEntity.ok(usuarioService.cambiarEstado(id, activo, authentication))
    }
}
