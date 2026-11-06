package com.security.modules.inicio

import com.security.shared.exceptions.ApiErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Tablero de la pagina principal. Ver docs/INICIO.md. Ninguna ruta consume tokens. */
@RestController
@RequestMapping("/api/inicio")
class InicioController(private val service: InicioService, private val demo: DemoInicioService) {

    @GetMapping("/resumen")
    fun resumen(authentication: Authentication, @RequestParam(required = false) periodo: String?): ResumenInicioResponse =
        service.resumen(authentication.name, periodo)

    // Solo el administrador universal; en la empresa de su cuenta. Idempotente.
    @PostMapping("/demo")
    fun cargarDemo(authentication: Authentication): DemoCargaResponse = demo.cargar(authentication.name)

    @DeleteMapping("/demo")
    fun quitarDemo(authentication: Authentication): DemoBorradoResponse = demo.quitar(authentication.name)

    @ExceptionHandler(InicioException::class)
    fun error(ex: InicioException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(ex.status).contentType(MediaType.APPLICATION_JSON).body(
            ApiErrorResponse(
                status = ex.status.value(), error = ex.status.reasonPhrase, message = ex.message ?: "Solicitud inválida",
                path = request.requestURI, code = ex.code
            )
        )
}
