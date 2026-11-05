package com.security.modules.matrices.infrastructure.controller

import com.security.modules.licencias.CobroAcciones
import com.security.modules.licencias.dominio.TIPO_MATRIZ_PDF
import com.security.modules.matrices.application.service.MatrizRiesgoConsultaService
import com.security.modules.matrices.infrastructure.export.MatrizRiesgoPdfService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal



@RestController
@RequestMapping("/api/matrices/analisis")

class MatrizRiesgoPdfController(
    private val matrizRiesgoConsultaService: MatrizRiesgoConsultaService,
    private val matrizRiesgoPdfService: MatrizRiesgoPdfService,
    private val cobro: CobroAcciones
) {

    @GetMapping(
        "/{id}/pdf",
        produces = [MediaType.APPLICATION_PDF_VALUE]
    )
    fun exportarPdf(
        principal: Principal,
        @PathVariable id: Int
    ): ResponseEntity<ByteArray> {

        val analisis =
            matrizRiesgoConsultaService.obtenerDetalle(
                username = principal.name,
                id = id
            )

        // Se avisa a Sulotec (matriz_pdf) con el análisis ya encontrado; la misma persona y la misma
        // matriz en dos minutos dan la misma clave (un reintento de la descarga no cobra dos veces).
        val pdf =
            cobro.ejecutar(TIPO_MATRIZ_PDF, "PDF de la matriz $id", criterios = listOf(id.toString())) {
                matrizRiesgoPdfService.generar(
                    analisis = analisis,
                    username = principal.name
                )
            }

        val disposition =
            ContentDisposition
                .attachment()
                .filename("matriz-riesgo-$id.pdf")
                .build()

        return ResponseEntity
            .ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                disposition.toString()
            )
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdf.size.toLong())
            .body(pdf)
    }
}
