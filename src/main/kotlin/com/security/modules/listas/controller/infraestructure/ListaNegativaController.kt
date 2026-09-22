package com.security.modules.listas.controller.infraestructure

import com.security.modules.listas.application.service.ListaNegativaService
import com.security.modules.listas.dto.ResultadoBusquedaResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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
        @RequestParam(required = false) documento: String?,
        @RequestParam(required = false) nombres: String?,
        @RequestParam(required = false) apellidoPaterno: String?,
        @RequestParam(required = false) apellidoMaterno: String?
    ): ResponseEntity<List<ResultadoBusquedaResponse>> {

        val resultados = listaNegativaService.buscar(
            documento = documento,
            nombres = nombres,
            apellidoPaterno = apellidoPaterno,
            apellidoMaterno = apellidoMaterno
        )

        return ResponseEntity.ok(resultados)
    }
}
