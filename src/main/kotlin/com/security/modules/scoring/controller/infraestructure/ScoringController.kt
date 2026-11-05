package com.security.modules.scoring.controller.infraestructure

import com.fasterxml.jackson.annotation.JsonInclude
import com.security.modules.scoring.application.service.ScoringException
import com.security.modules.scoring.application.service.ScoringService
import com.security.modules.scoring.application.service.ScoringSustentoService
import com.security.modules.scoring.dto.*
import com.security.modules.scoring.infrastructure.ScoringPdfService
import com.security.shared.exceptions.ResourceNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.OffsetDateTime

/** Formato de error del proyecto (message + code) con, si aplica, la lista de lo que falta. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScoringErrorResponse(
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val code: String? = null,
    val bloqueos: List<BloqueoResponse>? = null
)

/** Scoring de riesgo, metodologia version 2. Contrato en docs/SCORING.md. Todas las rutas exigen sesion. */
@RestController
@RequestMapping("/api/scoring")
class ScoringController(
    private val scoringService: ScoringService,
    private val sustentoService: ScoringSustentoService,
    private val pdfService: ScoringPdfService
) {

    @GetMapping("/catalogos")
    fun catalogos(): ResponseEntity<CatalogosScoringResponse> =
        ResponseEntity.ok(scoringService.obtenerCatalogos())

    // Consulta a Listas Negativas por documento: no consume tokens
    @GetMapping("/cliente")
    fun cliente(
        @RequestParam("documento", required = false) documento: String?,
        @RequestParam("tipoDocumento", required = false) tipoDocumento: String?
    ): ResponseEntity<ClienteScoringResponse> =
        ResponseEntity.ok(scoringService.consultarCliente(documento, tipoDocumento))

    // No guarda ni consume tokens; acepta datos a medias
    @PostMapping("/vista-previa")
    fun vistaPrevia(
        authentication: Authentication,
        @RequestBody request: DatosScoringRequest
    ): ResponseEntity<VistaPreviaResponse> =
        ResponseEntity.ok(scoringService.vistaPrevia(authentication.name, request))

    @PostMapping("/evaluar")
    fun evaluar(
        authentication: Authentication,
        @RequestBody request: DatosScoringRequest
    ): ResponseEntity<ResultadoScoringResponse> =
        ResponseEntity.ok(scoringService.evaluar(authentication.name, request))

    @GetMapping("/historial")
    fun historial(
        authentication: Authentication,
        pageable: Pageable
    ): ResponseEntity<Page<HistorialScoringResponse>> =
        ResponseEntity.ok(scoringService.obtenerHistorial(authentication.name, pageable))

    @PostMapping("/sustentos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun sustento(
        authentication: Authentication,
        @RequestParam("archivo", required = false) archivo: MultipartFile?
    ): ResponseEntity<SustentoResponse> =
        ResponseEntity.ok(sustentoService.guardar(authentication.name, archivo))

    @GetMapping("/{id}")
    fun obtener(
        authentication: Authentication,
        @PathVariable("id") id: Int
    ): ResponseEntity<ResultadoScoringResponse> =
        ResponseEntity.ok(scoringService.obtener(authentication.name, id))

    @PostMapping("/{id}/decision")
    fun decidir(
        authentication: Authentication,
        @PathVariable("id") id: Int,
        @RequestBody request: DecisionRequest
    ): ResponseEntity<ResultadoScoringResponse> =
        ResponseEntity.ok(scoringService.decidir(authentication.name, id, request))

    @GetMapping("/{id}/pdf")
    fun pdf(
        authentication: Authentication,
        @PathVariable("id") id: Int
    ): ResponseEntity<ByteArray> {
        val resultado = scoringService.obtener(authentication.name, id)
        val pdf = pdfService.generar(resultado, authentication.name)
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_PDF
            contentDisposition = ContentDisposition.attachment().filename("scoring_$id.pdf").build()
            contentLength = pdf.size.toLong()
        }
        return ResponseEntity.ok().headers(headers).body(pdf)
    }

    // Errores con su tipo fijado (JSON): asi tambien llegan bien a quien pidio un PDF.
    @ExceptionHandler(ScoringException::class)
    fun errorScoring(ex: ScoringException, request: HttpServletRequest): ResponseEntity<ScoringErrorResponse> =
        error(ex.status, ex.message ?: "Solicitud inválida", request, ex.code, ex.bloqueos)

    // Evaluacion de otra empresa: el mismo 404 que si no existiera.
    @ExceptionHandler(ResourceNotFoundException::class)
    fun noEncontrado(ex: ResourceNotFoundException, request: HttpServletRequest): ResponseEntity<ScoringErrorResponse> =
        error(HttpStatus.NOT_FOUND, ex.message ?: "No encontrado", request, "SCORING_NO_ENCONTRADO", null)

    private fun error(
        status: HttpStatus,
        mensaje: String,
        request: HttpServletRequest,
        code: String?,
        bloqueos: List<BloqueoResponse>?
    ): ResponseEntity<ScoringErrorResponse> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(
            ScoringErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = mensaje,
                path = request.requestURI,
                code = code,
                bloqueos = bloqueos
            )
        )
}
