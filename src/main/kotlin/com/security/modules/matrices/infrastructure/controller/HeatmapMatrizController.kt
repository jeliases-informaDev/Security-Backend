package com.security.modules.matrices.infrastructure.controller

import com.security.modules.matrices.application.dto.HeatmapMatrizResponse
import com.security.modules.matrices.application.service.HeatmapMatrizService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/matrices")
class HeatmapMatrizController(
    private val heatmapMatrizService: HeatmapMatrizService
) {

    @GetMapping("/heatmap")
    fun obtenerHeatmap(): ResponseEntity<HeatmapMatrizResponse> {

        return ResponseEntity.ok(
            heatmapMatrizService.obtenerHeatmap()
        )
    }
}