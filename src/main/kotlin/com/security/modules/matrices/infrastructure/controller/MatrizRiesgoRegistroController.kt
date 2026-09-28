package com.security.modules.matrices.infrastructure.controller

import com.security.modules.matrices.application.dto.GuardarMatrizRiesgoRequest
import com.security.modules.matrices.application.dto.MatrizRiesgoDetalleResponse
import com.security.modules.matrices.application.dto.MatrizRiesgoRegistroResponse
import com.security.modules.matrices.application.dto.MatrizRiesgoResumenResponse
import com.security.modules.matrices.application.service.MatrizRiesgoConsultaService
import com.security.modules.matrices.application.service.MatrizRiesgoRegistroService
import com.security.modules.matrices.application.validation.RegistroAnalisisValidation
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/matrices/analisis")
class MatrizRiesgoRegistroController(
    private val matrizRiesgoRegistroService: MatrizRiesgoRegistroService,
    private val matrizRiesgoConsultaService: MatrizRiesgoConsultaService
) {

    @PostMapping("/guardar")
    fun guardar(
        principal: Principal,
        @RequestBody
        request: GuardarMatrizRiesgoRequest
    ): ResponseEntity<MatrizRiesgoRegistroResponse> {

        val resultado =
            matrizRiesgoRegistroService.guardar(
                username = principal.name,
                request = request
            )

        return ResponseEntity.ok(resultado)
    }

    @PostMapping("/registrar")
    fun registrar(
        principal: Principal,

        @Validated(RegistroAnalisisValidation::class)
        @RequestBody
        request: GuardarMatrizRiesgoRequest
    ): ResponseEntity<MatrizRiesgoRegistroResponse> {

        val resultado =
            matrizRiesgoRegistroService.registrar(
                username = principal.name,
                request = request
            )

        return ResponseEntity.ok(resultado)
    }

    @GetMapping
    fun listar(
        principal: Principal
    ): ResponseEntity<List<MatrizRiesgoResumenResponse>> {

        val resultado =
            matrizRiesgoConsultaService.listar(
                principal.name
            )

        return ResponseEntity.ok(resultado)
    }

    @GetMapping("/{id}")
    fun obtenerDetalle(
        principal: Principal,
        @PathVariable id: Int
    ): ResponseEntity<MatrizRiesgoDetalleResponse> {

        val resultado =
            matrizRiesgoConsultaService.obtenerDetalle(
                username = principal.name,
                id = id
            )

        return ResponseEntity.ok(resultado)
    }

    @PutMapping("/{id}")
    fun actualizar(
        principal: Principal,
        @PathVariable id: Int,

        @Validated(RegistroAnalisisValidation::class)
        @RequestBody
        request: GuardarMatrizRiesgoRequest

    ): ResponseEntity<MatrizRiesgoRegistroResponse> {

        val resultado =
            matrizRiesgoRegistroService.actualizar(
                username = principal.name,
                id = id,
                request = request
            )

        return ResponseEntity.ok(resultado)
    }
}