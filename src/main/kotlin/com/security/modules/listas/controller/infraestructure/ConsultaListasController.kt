package com.security.modules.listas.controller.infraestructure

import com.security.modules.listas.application.service.ConsultaListasService
import com.security.modules.listas.application.service.ListasException
import com.security.modules.listas.dto.*
import com.security.shared.exceptions.ApiErrorResponse
import com.security.shared.exceptions.ResourceNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * Consultas a Listas Negativas como unidad (diseño nuevo). Las rutas viejas de ListaNegativaController (/buscar,
 * /historial, /{id}, /{id}/pdf) siguen igual. Contrato con ejemplos: docs/LISTAS-NEGATIVAS.md.
 */
@RestController
@RequestMapping("/api/listas-negativas/consultas")
class ConsultaListasController(private val service: ConsultaListasService) {

    @GetMapping("/catalogo")
    fun catalogo(): ResponseEntity<CatalogoConsultaResponse> = ResponseEntity.ok(service.catalogo())

    // Consume busqueda_listas (1 token). 201 con la consulta; 200 con los candidatos para elegir.
    @PostMapping
    fun consultar(authentication: Authentication, @RequestBody request: ConsultaListasRequest): ResponseEntity<ConsultaCreadaResponse> {
        val r = service.consultar(authentication.name, request)
        return ResponseEntity.status(if (r.consulta != null) HttpStatus.CREATED else HttpStatus.OK).body(r)
    }

    @GetMapping("/busquedas/{busquedaId}/candidatos")
    fun candidatos(
        authentication: Authentication,
        @PathVariable busquedaId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<BusquedaCandidatosResponse> =
        ResponseEntity.ok(service.candidatos(authentication.name, busquedaId, page, size))

    // No consume. 201 si se creo la consulta; 200 si ese candidato ya se habia elegido en esta busqueda.
    @PostMapping("/busquedas/{busquedaId}/elegir")
    fun elegir(
        authentication: Authentication,
        @PathVariable busquedaId: Long,
        @RequestBody request: ElegirCandidatoRequest
    ): ResponseEntity<ConsultaResponse> {
        val (consulta, creada) = service.elegir(authentication.name, busquedaId, request.entidadId)
        return ResponseEntity.status(if (creada) HttpStatus.CREATED else HttpStatus.OK).body(consulta)
    }

    @GetMapping
    fun historial(
        authentication: Authentication,
        @RequestParam(required = false) filtro: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<PaginaResponse<ConsultaHistorialResponse>> =
        ResponseEntity.ok(service.historial(authentication.name, filtro, q, page, size))

    @GetMapping("/resumen")
    fun resumen(authentication: Authentication, @RequestParam(required = false) mes: String?): ResponseEntity<ResumenConsultasResponse> =
        ResponseEntity.ok(service.resumen(authentication.name, mes))

    @GetMapping("/inicio")
    fun inicio(authentication: Authentication): ResponseEntity<InicioListasResponse> =
        ResponseEntity.ok(service.inicio(authentication.name))

    @GetMapping("/{id}")
    fun obtener(authentication: Authentication, @PathVariable id: Long): ResponseEntity<ConsultaResponse> =
        ResponseEntity.ok(service.obtener(authentication.name, id))

    @PostMapping("/{id}/homonimo")
    fun marcarHomonimo(
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestBody(required = false) request: ComentarioRequest?
    ): ResponseEntity<ConsultaResponse> =
        ResponseEntity.ok(service.marcarHomonimo(authentication.name, id, request?.comentario))

    @PostMapping("/{id}/homonimo/deshacer")
    fun deshacerHomonimo(
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestBody(required = false) request: ComentarioRequest?
    ): ResponseEntity<ConsultaResponse> =
        ResponseEntity.ok(service.deshacerHomonimo(authentication.name, id, request?.comentario))

    // Consume lista_pdf (1 token por descarga; un reintento en dos minutos no cobra otra vez).
    @GetMapping("/{id}/pdf")
    fun pdf(authentication: Authentication, @PathVariable id: Long): ResponseEntity<ByteArray> {
        val pdf = service.pdf(authentication.name, id)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"listas-negativas-consulta-$id.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }

    @ExceptionHandler(ListasException::class)
    fun errorListas(ex: ListasException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        error(ex.status, ex.message ?: "Solicitud inválida", request, ex.code)

    // Una consulta de otra empresa: el mismo 404 que si no existiera.
    @ExceptionHandler(ResourceNotFoundException::class)
    fun noEncontrado(ex: ResourceNotFoundException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.NOT_FOUND, ex.message ?: "No encontrado", request, "LISTAS_NO_ENCONTRADA")

    private fun error(status: HttpStatus, mensaje: String, request: HttpServletRequest, code: String): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(
            ApiErrorResponse(status = status.value(), error = status.reasonPhrase, message = mensaje, path = request.requestURI, code = code)
        )
}
