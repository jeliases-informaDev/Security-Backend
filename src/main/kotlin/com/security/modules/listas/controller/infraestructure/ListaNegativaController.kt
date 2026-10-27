package com.security.modules.listas.controller.infraestructure

import com.security.modules.listas.application.service.ListaNegativaService
import com.security.modules.listas.dto.HistorialConsultaResponse
import com.security.modules.listas.dto.ResultadoBusquedaResponse
import com.security.shared.config.CabecerasPaginacion
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

    // page empieza en 0. El cuerpo sigue siendo la lista de la pagina (como antes); el total va en cabeceras.
    @GetMapping("/buscar")
    fun buscar(
        authentication: Authentication,
        @RequestParam(required = false) documento: String?,
        @RequestParam(required = false) tipoDocumento: String?,
        @RequestParam(required = false) nombres: String?,
        @RequestParam(required = false) apellidoPaterno: String?,
        @RequestParam(required = false) apellidoMaterno: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<List<ResultadoBusquedaResponse>> {

        val resultados = listaNegativaService.buscar(
            username = authentication.name,
            documento = documento,
            tipoDocumento = tipoDocumento,
            nombres = nombres,
            apellidoPaterno = apellidoPaterno,
            apellidoMaterno = apellidoMaterno,
            pagina = page,
            tamano = size
        )

        return ResponseEntity.ok()
            .header(CabecerasPaginacion.TOTAL, resultados.totalElements.toString())
            .header(CabecerasPaginacion.TOTAL_PAGINAS, resultados.totalPages.toString())
            .header(CabecerasPaginacion.PAGINA, resultados.number.toString())
            .header(CabecerasPaginacion.TAMANO, resultados.size.toString())
            .body(resultados.content)
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
