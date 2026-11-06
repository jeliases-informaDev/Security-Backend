package com.security.modules.oficial.web

import com.security.modules.oficial.aplicacion.AccesoOficialService
import com.security.modules.oficial.aplicacion.AdjuntoResponse
import com.security.modules.oficial.aplicacion.AdjuntosOficialService
import com.security.modules.oficial.aplicacion.CambioDatosRequest
import com.security.modules.oficial.aplicacion.CambioDatosResponse
import com.security.modules.oficial.aplicacion.CicloOficialService
import com.security.modules.oficial.aplicacion.ComunicacionOficialService
import com.security.modules.oficial.aplicacion.ComunicacionPendienteRequest
import com.security.modules.oficial.aplicacion.ComunicacionRequest
import com.security.modules.oficial.aplicacion.DeclaracionPdfService
import com.security.modules.oficial.aplicacion.DesignacionOficialService
import com.security.modules.oficial.aplicacion.DesignacionRequest
import com.security.modules.oficial.aplicacion.DjDesignanteVerResponse
import com.security.modules.oficial.aplicacion.EstadoResponse
import com.security.modules.oficial.aplicacion.FichaOficialService
import com.security.modules.oficial.aplicacion.FichaResponse
import com.security.modules.oficial.aplicacion.HistorialResponse
import com.security.modules.oficial.aplicacion.HojaDeclaracionResponse
import com.security.modules.oficial.aplicacion.Oc
import com.security.modules.oficial.aplicacion.OcErrorResponse
import com.security.modules.oficial.aplicacion.OficialException
import com.security.modules.oficial.aplicacion.PendienteResponse
import com.security.modules.oficial.aplicacion.RemocionCreadaResponse
import com.security.modules.oficial.aplicacion.RemocionRequest
import com.security.modules.oficial.aplicacion.ResumenDesignacion
import com.security.modules.oficial.aplicacion.VistaPreviaRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets

/**
 * Designacion, ficha, historial, adjuntos, declaraciones, comunicacion a la UIF, cambio de datos, remocion y pendientes.
 * Todo lo de otra empresa responde 404 OC_NO_ENCONTRADO. Contrato: docs/OFICIAL-CUMPLIMIENTO.md.
 */
@RestController
@RequestMapping("/api/oficial-cumplimiento")
class DesignacionController(
    private val acceso: AccesoOficialService,
    private val fichas: FichaOficialService,
    private val designaciones: DesignacionOficialService,
    private val comunicaciones: ComunicacionOficialService,
    private val ciclo: CicloOficialService,
    private val adjuntos: AdjuntosOficialService,
    private val pdf: DeclaracionPdfService
) {

    @GetMapping("/estado")
    fun estado(@RequestParam(required = false) empresa: String?): EstadoResponse = fichas.estado(acceso.contexto(empresa))

    // ---------------------------------------------------------------- adjuntos

    @PostMapping("/adjuntos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun subirAdjunto(
        @RequestParam(required = false) ambito: String?,
        @RequestParam(name = "archivo", required = false) archivo: MultipartFile?,
        @RequestParam(required = false) empresa: String?
    ): ResponseEntity<AdjuntoResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(adjuntos.subir(acceso.contexto(empresa), ambito, archivo))

    @GetMapping("/adjuntos/{id}")
    fun descargarAdjunto(@PathVariable id: String, @RequestParam(required = false) empresa: String?): ResponseEntity<ByteArray> {
        val archivo = adjuntos.descargar(acceso.contexto(empresa), id)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre, StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(archivo.tipoContenido))
            .body(archivo.contenido)
    }

    @DeleteMapping("/adjuntos/{id}")
    fun borrarAdjunto(@PathVariable id: String, @RequestParam(required = false) empresa: String?): ResponseEntity<Void> {
        adjuntos.borrar(acceso.contexto(empresa), id)
        return ResponseEntity.noContent().build()
    }

    // ---------------------------------------------------------------- declaraciones

    @PostMapping("/declaraciones/vista-previa")
    fun vistaPrevia(@RequestBody pedido: VistaPreviaRequest, @RequestParam(required = false) empresa: String?): HojaDeclaracionResponse =
        designaciones.vistaPrevia(acceso.contexto(empresa), pedido)

    @PostMapping("/declaraciones/vista-previa/pdf")
    fun vistaPreviaPdf(@RequestBody pedido: VistaPreviaRequest, @RequestParam(required = false) empresa: String?): ResponseEntity<ByteArray> =
        respuestaPdf(pdf.generar(designaciones.vistaPrevia(acceso.contexto(empresa), pedido)), "declaracion-jurada-requisitos.pdf")

    @GetMapping("/designaciones/{id}/declaraciones/requisitos")
    fun declaracionRequisitos(@PathVariable id: String, @RequestParam(required = false) empresa: String?): HojaDeclaracionResponse =
        designaciones.declaracionRequisitos(acceso.contexto(empresa), id)

    @GetMapping("/designaciones/{id}/declaraciones/requisitos/pdf")
    fun declaracionRequisitosPdf(@PathVariable id: String, @RequestParam(required = false) empresa: String?): ResponseEntity<ByteArray> =
        respuestaPdf(pdf.generar(designaciones.declaracionRequisitos(acceso.contexto(empresa), id)), "declaracion-jurada-requisitos.pdf")

    @GetMapping("/designaciones/{id}/declaraciones/designante")
    fun declaracionDesignante(@PathVariable id: String, @RequestParam(required = false) empresa: String?): DjDesignanteVerResponse =
        designaciones.declaracionDesignante(acceso.contexto(empresa), id)

    @GetMapping("/designaciones/{id}/declaraciones/designante/pdf")
    fun declaracionDesignantePdf(@PathVariable id: String, @RequestParam(required = false) empresa: String?): ResponseEntity<ByteArray> {
        designaciones.declaracionDesignante(acceso.contexto(empresa), id)
        throw Oc.pronto("El PDF de la declaración de quién designó estará disponible pronto.")
    }

    // ---------------------------------------------------------------- designaciones

    @PostMapping("/designaciones")
    fun designar(@RequestBody pedido: DesignacionRequest, @RequestParam(required = false) empresa: String?): ResponseEntity<FichaResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(designaciones.crear(acceso.contexto(empresa), pedido))

    @GetMapping("/designaciones")
    fun listar(@RequestParam(required = false) empresa: String?): List<ResumenDesignacion> =
        designaciones.listar(acceso.contexto(empresa))

    @GetMapping("/designaciones/{id}")
    fun ficha(@PathVariable id: String, @RequestParam(required = false) empresa: String?): FichaResponse =
        designaciones.ficha(acceso.contexto(empresa), id)

    @PutMapping("/designaciones/{id}")
    fun corregir(
        @PathVariable id: String, @RequestBody pedido: DesignacionRequest, @RequestParam(required = false) empresa: String?
    ): FichaResponse = designaciones.corregir(acceso.contexto(empresa), id, pedido)

    @GetMapping("/designaciones/{id}/historial")
    fun historial(@PathVariable id: String, @RequestParam(required = false) empresa: String?): HistorialResponse =
        designaciones.historial(acceso.contexto(empresa), id)

    @PostMapping("/designaciones/{id}/comunicacion")
    fun comunicacion(
        @PathVariable id: String, @RequestBody pedido: ComunicacionRequest, @RequestParam(required = false) empresa: String?
    ): FichaResponse = comunicaciones.registrar(acceso.contexto(empresa), id, pedido)

    @PatchMapping("/designaciones/{id}/datos")
    fun cambiarDatos(
        @PathVariable id: String, @RequestBody pedido: CambioDatosRequest, @RequestParam(required = false) empresa: String?
    ): CambioDatosResponse = ciclo.cambiarDatos(acceso.contexto(empresa), id, pedido)

    @PostMapping("/designaciones/{id}/remocion")
    fun remover(
        @PathVariable id: String, @RequestBody pedido: RemocionRequest, @RequestParam(required = false) empresa: String?
    ): ResponseEntity<RemocionCreadaResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(ciclo.remover(acceso.contexto(empresa), id, pedido))

    // ---------------------------------------------------------------- pendientes

    @GetMapping("/pendientes")
    fun pendientes(
        @RequestParam(required = false, defaultValue = "false") todos: Boolean,
        @RequestParam(required = false) empresa: String?
    ): List<PendienteResponse> = fichas.pendientes(acceso.contexto(empresa), todos)

    @PostMapping("/pendientes/{id}/comunicacion")
    fun comunicarPendiente(
        @PathVariable id: String, @RequestBody pedido: ComunicacionPendienteRequest, @RequestParam(required = false) empresa: String?
    ): PendienteResponse = ciclo.comunicarPendiente(acceso.contexto(empresa), id, pedido)

    @ExceptionHandler(OficialException::class)
    fun error(ex: OficialException, request: HttpServletRequest): ResponseEntity<OcErrorResponse> = respuestaError(ex, request)

    private fun respuestaPdf(bytes: ByteArray, nombre: String): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
        .contentType(MediaType.APPLICATION_PDF)
        .body(bytes)
}
