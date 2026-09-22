package com.security.modules.listas.controller.infraestructure

import com.security.modules.listas.application.service.ListaNegativaService
import com.security.modules.listas.dto.HistorialConsultaResponse
import com.security.modules.listas.dto.ResultadoBusquedaResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/listas-negativas")
class ListaNegativaController(
    private val listaNegativaService: ListaNegativaService
) {

    @GetMapping("/buscar")
    fun buscar(
        authentication: Authentication,
        @RequestParam(required = false) documento: String?,
        @RequestParam(required = false) nombres: String?,
        @RequestParam(required = false) apellidoPaterno: String?,
        @RequestParam(required = false) apellidoMaterno: String?
    ): ResponseEntity<List<ResultadoBusquedaResponse>> {

        val resultados = listaNegativaService.buscar(
            username = authentication.name,
            documento = documento,
            nombres = nombres,
            apellidoPaterno = apellidoPaterno,
            apellidoMaterno = apellidoMaterno
        )

        return ResponseEntity.ok(resultados)
    }

    @GetMapping("/historial")
    fun historial(
        authentication: Authentication,
        pageable: Pageable
    ): ResponseEntity<Page<HistorialConsultaResponse>> {

        return ResponseEntity.ok(listaNegativaService.obtenerHistorial(authentication.name, pageable))
    }

    @GetMapping("/{id}")
    fun detalle(@PathVariable id: Int): ResponseEntity<ResultadoBusquedaResponse> {
        return ResponseEntity.ok(listaNegativaService.obtenerDetalle(id))
    }
}
