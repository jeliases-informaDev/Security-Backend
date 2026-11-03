package com.security.modules.creditos.controller

import com.security.modules.creditos.application.service.CreditoService
import com.security.modules.creditos.dto.*
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.VisibilidadEmpresaService
import com.security.shared.security.esAdministrador
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/licencias")
class CreditoController(
    private val creditoService: CreditoService,
    private val visibilidadService: VisibilidadEmpresaService,
    private val empresaRepository: EmpresaRepository,
    private val usuarioRepository: UsuarioRepository
) {

    /**
     * Resumen de la bolsa y estado de licencia asignada a la empresa del usuario actual.
     */
    @GetMapping("/mi-bolsa")
    fun miBolsa(authentication: Authentication): ResponseEntity<ResumenBolsaResponse> {
        return ResponseEntity.ok(creditoService.obtenerMiResumen(authentication.name))
    }

    /**
     * Resumen de una empresa específica.
     * ADMINISTRADOR universal puede ver cualquiera; ADMINISTRADOR_EMPRESA solo la suya.
     */
    @GetMapping("/empresas/{empresaId}")
    fun obtenerBolsaEmpresa(
        @PathVariable empresaId: Int,
        authentication: Authentication
    ): ResponseEntity<ResumenBolsaResponse> {
        validarAccesoEmpresa(authentication, empresaId)
        return ResponseEntity.ok(creditoService.obtenerResumenEmpresa(empresaId))
    }

    /**
     * Endpoint de sincronización de créditos asignados desde Sulotec Central (BPMN P1 / P4).
     */
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/sincronizar")
    fun sincronizar(
        @Valid @RequestBody request: SincronizarAsignacionCreditosRequest,
        authentication: Authentication
    ): ResponseEntity<ResumenBolsaResponse> {
        return ResponseEntity.ok(creditoService.sincronizarAsignacion(request, authentication.name))
    }

    /**
     * Listar movimientos de consumo según alcance de la empresa.
     */
    @GetMapping("/movimientos")
    fun listarMovimientos(
        @RequestParam(required = false) empresaId: Int?,
        pageable: Pageable,
        authentication: Authentication
    ): ResponseEntity<Page<MovimientoCreditoResponse>> {
        val idFiltrado = if (authentication.esAdministrador()) {
            empresaId
        } else {
            val vista = visibilidadService.de(authentication.name)
            val empresaNombre = vista.empresa ?: throw ResourceNotFoundException("Usuario sin empresa")
            val emp = empresaRepository.findByNombreIgnoreCase(empresaNombre)
                ?: throw ResourceNotFoundException("Empresa no encontrada")
            emp.id
        }
        return ResponseEntity.ok(creditoService.listarMovimientos(idFiltrado, pageable))
    }

    /**
     * Listar avisos en la app para la empresa del usuario (80% alcanzado, exceso, vencimiento).
     */
    @GetMapping("/avisos")
    fun listarAvisos(authentication: Authentication): ResponseEntity<List<AvisoLicenciaResponse>> {
        val vista = visibilidadService.de(authentication.name)
        val empresaNombre = vista.empresa ?: return ResponseEntity.ok(emptyList())
        val emp = empresaRepository.findByNombreIgnoreCase(empresaNombre)
            ?: return ResponseEntity.ok(emptyList())
        return ResponseEntity.ok(creditoService.obtenerAvisos(emp.id!!))
    }

    /**
     * Marcar aviso como leído.
     */
    @PatchMapping("/avisos/{avisoId}/leido")
    fun marcarAvisoLeido(@PathVariable avisoId: Int): ResponseEntity<Void> {
        creditoService.marcarAvisoLeido(avisoId)
        return ResponseEntity.noContent().build()
    }

    private fun validarAccesoEmpresa(authentication: Authentication, empresaId: Int) {
        if (authentication.esAdministrador()) return
        val vista = visibilidadService.de(authentication.name)
        val empresaNombre = vista.empresa ?: throw ResourceNotFoundException("Usuario sin empresa")
        val emp = empresaRepository.findByNombreIgnoreCase(empresaNombre)
            ?: throw ResourceNotFoundException("Empresa no encontrada")
        if (emp.id != empresaId) {
            throw ResourceNotFoundException("Empresa no encontrada")
        }
    }
}
