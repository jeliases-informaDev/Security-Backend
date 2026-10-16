package com.security.modules.empresas.controller.infraestructure

import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.empresas.dto.EmpresaCreateRequest
import com.security.modules.empresas.dto.EmpresaResponse
import com.security.modules.empresas.dto.EmpresaUpdateRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Lista de empresas clientes. Solo la administra un ADMINISTRADOR. */
@RestController
@RequestMapping("/api/empresas")
@PreAuthorize("hasRole('ADMINISTRADOR')")
class EmpresaController(
    private val empresaService: EmpresaService
) {

    @GetMapping
    fun listar(): ResponseEntity<List<EmpresaResponse>> =
        ResponseEntity.ok(empresaService.listar())

    @PostMapping
    fun crear(@Valid @RequestBody request: EmpresaCreateRequest): ResponseEntity<EmpresaResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(empresaService.crear(request))

    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: Int,
        @Valid @RequestBody request: EmpresaUpdateRequest
    ): ResponseEntity<EmpresaResponse> =
        ResponseEntity.ok(empresaService.actualizar(id, request))

    @PatchMapping("/{id}/estado")
    fun cambiarEstado(
        @PathVariable id: Int,
        @RequestParam activa: Boolean
    ): ResponseEntity<EmpresaResponse> =
        ResponseEntity.ok(empresaService.cambiarEstado(id, activa))
}
