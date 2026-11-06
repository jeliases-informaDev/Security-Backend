package com.security.modules.operaciones.infrastructure.adapter.`in`.web

import com.security.modules.licencias.CobroAcciones
import com.security.modules.licencias.conCobro
import com.security.modules.licencias.dominio.TIPO_OPERACION_PDF
import com.security.modules.operaciones.application.service.AccesoEmpresaService
import com.security.modules.operaciones.application.service.CodigosOperaciones
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
    private val acceso: AccesoEmpresaService,
    private val cobro: CobroAcciones? = null
) {

    @GetMapping("/{id}/exportar-pdf")
    fun exportarPdf(
        @PathVariable("id") id: Long
    ): ResponseEntity<ByteArray> {
        // Solo el PDF de un registro de la propia empresa (un administrador, cualquiera).
        acceso.comprobarRegistro(id)
        val autor = acceso.actor().autor()
        // Se avisa a Sulotec (operacion_pdf); la misma persona y el mismo registro en dos minutos dan la misma clave.
        // La descarga queda en el historial dentro de la misma accion: si no se pudo anotar, se anula el cobro.
        val generado = cobro.conCobro(TIPO_OPERACION_PDF, "PDF de la operacion $id", criterios = listOf(id.toString())) {
            exportarOperacionPdfUseCase.ejecutar(id, autor)
        }
        val pdf = generado.bytes

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_PDF
            contentDisposition = ContentDisposition.attachment()
                .filename("operacion_$id.pdf")
                .build()
            contentLength = pdf.size.toLong()
            set("X-Codigo-Verificacion", generado.codigoVerificacion)
            set("X-Huella-Sha256", generado.huella)
            set("X-Paginas", "1")
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
            .body(mapOf("mensaje" to (ex.message ?: "Operación no encontrada"), "code" to CodigosOperaciones.NO_ENCONTRADA))

    // Registro de otra empresa: mismo 404 que si no existiera.
    @ExceptionHandler(ResourceNotFoundException::class)
    fun registroAjeno(
        ex: ResourceNotFoundException
    ): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("mensaje" to (ex.message ?: "Operación no encontrada"), "code" to CodigosOperaciones.NO_ENCONTRADA))

    @ExceptionHandler(IllegalArgumentException::class)
    fun idInvalido(
        ex: IllegalArgumentException
    ): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("mensaje" to (ex.message ?: "ID inválido")))
}
