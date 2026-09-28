package com.security.modules.scoring.controller.infraestructure

import com.security.modules.scoring.application.service.ScoringService
import com.security.modules.scoring.dto.CatalogosScoringResponse
import com.security.modules.scoring.dto.EvaluarScoringRequest
import com.security.modules.scoring.dto.ScoringResultResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/scoring")
class ScoringController(
    private val scoringService: ScoringService
) {

    @GetMapping("/catalogos")
    fun catalogos(): ResponseEntity<CatalogosScoringResponse> {
        return ResponseEntity.ok(scoringService.obtenerCatalogos())
    }

    @PostMapping("/evaluar")
    fun evaluar(
        authentication: Authentication,
        @Valid @RequestBody request: EvaluarScoringRequest
    ): ResponseEntity<ScoringResultResponse> {
        return ResponseEntity.ok(scoringService.evaluar(authentication.name, request))
    }

    @GetMapping("/historial")
    fun historial(
        authentication: Authentication,
        pageable: Pageable
    ): ResponseEntity<Page<ScoringResultResponse>> {
        return ResponseEntity.ok(scoringService.obtenerHistorial(authentication.name, pageable))
    }
}
