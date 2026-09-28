package com.security.modules.matrices.infrastructure.controller

import com.security.modules.matrices.application.dto.CatalogoMatrizResponse
import com.security.modules.matrices.application.dto.CrearCatalogoMatrizRequest
import com.security.modules.matrices.application.service.MatrizCatalogoService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/matrices")
class MatrizCatalogoController(
    private val matrizCatalogoService: MatrizCatalogoService
) {

    @PostMapping("/areas")
    fun crearArea(
        principal: Principal,
        @Valid
        @RequestBody
        request: CrearCatalogoMatrizRequest
    ): ResponseEntity<CatalogoMatrizResponse> {

        val resultado =
            matrizCatalogoService.crearArea(
                principal.name,
                request
            )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(resultado)
    }

    @GetMapping("/areas")
    fun listarAreas(
        principal: Principal
    ): ResponseEntity<List<CatalogoMatrizResponse>> {

        return ResponseEntity.ok(
            matrizCatalogoService.listarAreas(
                principal.name
            )
        )
    }

    @PostMapping("/procesos")
    fun crearProceso(
        principal: Principal,
        @Valid
        @RequestBody
        request: CrearCatalogoMatrizRequest
    ): ResponseEntity<CatalogoMatrizResponse> {

        val resultado =
            matrizCatalogoService.crearProceso(
                principal.name,
                request
            )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(resultado)
    }

    @GetMapping("/procesos")
    fun listarProcesos(
        principal: Principal
    ): ResponseEntity<List<CatalogoMatrizResponse>> {

        return ResponseEntity.ok(
            matrizCatalogoService.listarProcesos(
                principal.name
            )
        )
    }

    @PostMapping(
        "/areas/{areaId}/procesos/{procesoId}"
    )
    fun vincularProceso(
        principal: Principal,
        @PathVariable areaId: Int,
        @PathVariable procesoId: Int
    ): ResponseEntity<Void> {

        matrizCatalogoService.vincularProceso(
            username = principal.name,
            areaId = areaId,
            procesoId = procesoId
        )

        return ResponseEntity.noContent().build()
    }

    @GetMapping(
        "/areas/{areaId}/procesos"
    )
    fun listarProcesosPorArea(
        principal: Principal,
        @PathVariable areaId: Int
    ): ResponseEntity<List<CatalogoMatrizResponse>> {

        return ResponseEntity.ok(
            matrizCatalogoService.listarProcesosPorArea(
                username = principal.name,
                areaId = areaId
            )
        )
    }
}