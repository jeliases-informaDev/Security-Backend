
package com.security.modules.operaciones.infrastructure.adapter.`in`.web

import com.security.modules.operaciones.application.service.AccesoEmpresaService
import com.security.modules.operaciones.application.usecase.ExportarOperacionPdfUseCase
import com.security.modules.operaciones.application.usecase.OperacionNoEncontradaException
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/operaciones", "/api/pdf")
class OperacionPdfController(
    private val exportarOperacionPdfUseCase: ExportarOperacionPdfUseCase,
    private val acceso: AccesoEmpresaService
) {

    @GetMapping("/{id}/exportar-pdf")
    fun exportarPdf(
        @PathVariable("id") id: Long
    ): ResponseEntity<ByteArray> {
        // Solo el PDF de un registro de la propia empresa (un administrador, cualquiera).
        acceso.comprobarRegistro(id)
        val pdf = exportarOperacionPdfUseCase.ejecutar(id)

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_PDF
            contentDisposition = ContentDisposition.attachment()
                .filename("operacion_$id.pdf")
                .build()
            contentLength = pdf.size.toLong()
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(pdf)
    }
    // El cliente pide "Accept: application/pdf": el error se devuelve con su tipo fijado (JSON) para que
    // no termine en un 406 (y, por el reenvio a /error, en un 401 confuso).
    @ExceptionHandler(OperacionNoEncontradaException::class)
    fun operacionNoEncontrada(
        ex: OperacionNoEncontradaException
    ): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("mensaje" to (ex.message ?: "Operación no encontrada")))

    // Registro de otra empresa: mismo 404 que si no existiera.
    @ExceptionHandler(ResourceNotFoundException::class)
    fun registroAjeno(
        ex: ResourceNotFoundException
    ): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("mensaje" to (ex.message ?: "Operación no encontrada")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun idInvalido(
        ex: IllegalArgumentException
    ): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("mensaje" to (ex.message ?: "ID inválido")))
}
