package com.security.modules.matrices.infrastructure.controller

import com.security.modules.matrices.application.dto.CalcularRiesgoInherenteRequest
import com.security.modules.matrices.application.dto.CalcularRiesgoInherenteResponse
import com.security.modules.matrices.application.dto.CalcularRiesgoResidualRequest
import com.security.modules.matrices.application.dto.CalcularRiesgoResidualResponse
import com.security.modules.matrices.application.usecase.CalcularRiesgoInherenteUseCase
import com.security.modules.matrices.application.usecase.CalcularRiesgoResidualUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/matrices")
class MatrizController(
    private val calcularRiesgoInherenteUseCase: CalcularRiesgoInherenteUseCase,
    private val calcularRiesgoResidualUseCase: CalcularRiesgoResidualUseCase
) {

    @PostMapping("/calcular-inherente")
    fun calcularRiesgoInherente(
        @RequestBody request: CalcularRiesgoInherenteRequest
    ): ResponseEntity<CalcularRiesgoInherenteResponse> {

        val resultado =
            calcularRiesgoInherenteUseCase.ejecutar(request)

        return ResponseEntity.ok(resultado)
    }

    @PostMapping("/calcular-residual")
    fun calcularRiesgoResidual(
        @RequestBody request: CalcularRiesgoResidualRequest
    ): ResponseEntity<CalcularRiesgoResidualResponse> {

        val resultado =
            calcularRiesgoResidualUseCase.ejecutar(request)

        return ResponseEntity.ok(resultado)
    }
}