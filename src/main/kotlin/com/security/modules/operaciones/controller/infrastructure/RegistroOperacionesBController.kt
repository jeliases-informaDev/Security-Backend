package com.security.modules.operaciones.controller.infrastructure

import com.security.modules.operaciones.application.service.*
import com.security.modules.operaciones.dto.*
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.Year

/** Descarga de un archivo con su nombre. */
internal fun descarga(a: ArchivoOperacion): ResponseEntity<ByteArray> =
    ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(a.nombre, Charsets.UTF_8).build().toString())
        .contentType(a.tipo?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() } ?: MediaType.APPLICATION_OCTET_STREAM)
        .body(a.contenido)

/**
 * Sector y umbrales (RO 16), tipo de cambio y verificacion de umbral. Leer: cualquiera de la empresa; guardar: el administrador.
 * La empresa sale del token (el universal puede indicar `empresa`). No consume tokens.
 */
@RestController
@RequestMapping("/api/operaciones")
class ConfiguracionOperacionesController(
    private val acceso: AccesoEmpresaService,
    private val configuracion: ConfiguracionOperacionesService,
    private val tiposCambio: TipoCambioService,
    private val umbrales: UmbralService
) {

    @GetMapping("/configuracion")
    fun configuracion(@RequestParam(required = false) empresa: String?): ConfiguracionResponse =
        configuracion.ver(acceso.empresaPermitida(empresa), acceso.actor().puedeAdministrar)

    @PutMapping("/configuracion")
    fun guardarConfiguracion(@RequestParam(required = false) empresa: String?, @RequestBody request: ConfiguracionRequest): ConfiguracionResponse {
        val actor = acceso.administrador()
        return configuracion.guardar(acceso.empresaPermitida(empresa), request, actor.autor())
    }

    @GetMapping("/tipos-cambio")
    fun tiposCambio(@RequestParam(required = false) empresa: String?, @RequestParam(required = false) anio: Int?): TiposCambioResponse {
        val actor = acceso.actor()
        // El universal sin empresa ve el de Sulotec
        val deEmpresa = if (actor.esAdministrador && empresa.isNullOrBlank()) actor.empresa else acceso.empresaPermitida(empresa)
        return tiposCambio.listar(deEmpresa, anio ?: Year.now(ZONA_LIMA).value, actor.puedeAdministrar)
    }

    @PutMapping("/tipos-cambio")
    fun guardarTipoCambio(@RequestParam(required = false) empresa: String?, @RequestBody request: TipoCambioRequest): TipoCambioResponse {
        val actor = acceso.administrador()
        // El universal sin empresa guarda el de Sulotec (vale para todas); con empresa, el de esa empresa.
        val ambito = if (actor.esAdministrador && empresa.isNullOrBlank()) null else acceso.empresaPermitida(empresa)
        return tiposCambio.guardar(ambito, request, actor.autor())
    }

    @PostMapping("/verificar-umbral")
    fun verificarUmbral(@RequestParam(required = false) empresa: String?, @RequestBody request: VerificarUmbralRequest): VerificacionUmbralResponse =
        umbrales.evaluar(acceso.empresaPermitida(empresa), request.tipoOperacion, request.moneda, request.monto, request.fechaOperacion)
}

/** Marcar como inusual (RO 12) y ROS (RO 13). Solo registros de la propia empresa. No consume tokens. */
@RestController
@RequestMapping("/api/operaciones")
class AnalisisOperacionController(
    private val acceso: AccesoEmpresaService,
    private val analisis: AnalisisOperacionService
) {

    @GetMapping("/{id}/inusualidad")
    fun ver(@PathVariable id: Long): InusualidadResponse {
        acceso.comprobarRegistro(id)
        return analisis.ver(id, acceso.actor().puedeAdministrar)
    }

    @PutMapping("/{id}/inusualidad")
    fun guardar(@PathVariable id: Long, @RequestBody request: InusualidadRequest): InusualidadResponse {
        acceso.comprobarRegistro(id)
        val actor = acceso.actor()
        return analisis.guardar(id, request, actor.autor(), actor.puedeAdministrar)
    }

    @PostMapping("/{id}/inusualidad/adjuntos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun adjuntar(@PathVariable id: Long, @RequestParam("archivo") archivo: MultipartFile): ResponseEntity<AdjuntoResponse> {
        acceso.comprobarRegistro(id)
        return ResponseEntity.status(HttpStatus.CREATED).body(analisis.adjuntar(id, archivo, acceso.actor().autor()))
    }

    @GetMapping("/{id}/inusualidad/adjuntos/{adjuntoId}")
    fun adjunto(@PathVariable id: Long, @PathVariable adjuntoId: Long): ResponseEntity<ByteArray> {
        acceso.comprobarRegistro(id)
        return descarga(analisis.descargarAdjunto(id, adjuntoId))
    }

    @PostMapping("/{id}/inusualidad/decision")
    fun decidir(@PathVariable id: Long, @RequestBody request: DecisionRequest): InusualidadResponse {
        acceso.comprobarRegistro(id)
        return analisis.decidir(id, request, acceso.administrador().autor())
    }

    @GetMapping("/{id}/ros")
    fun ros(@PathVariable id: Long): RosResponse {
        acceso.comprobarRegistro(id)
        return analisis.ros(id, acceso.actor().puedeAdministrar)
    }

    @PutMapping("/{id}/ros")
    fun registrarRos(@PathVariable id: Long, @RequestBody request: RosRequest): RosResponse {
        acceso.comprobarRegistro(id)
        return analisis.registrarRos(id, request, acceso.administrador().autor())
    }
}

/** Exportar (RO 14) y constancia de envio (RO 15). Ver: cualquiera de la empresa; generar y registrar: el administrador. */
@RestController
@RequestMapping("/api/operaciones/exportaciones")
class ExportacionOperacionesController(
    private val acceso: AccesoEmpresaService,
    private val exportaciones: ExportacionOperacionesService
) {

    @GetMapping("/validacion")
    fun validacion(
        @RequestParam(required = false) empresa: String?,
        @RequestParam(required = false) periodo: String?,
        @RequestParam(required = false) formato: String?
    ): ValidacionExportacionResponse =
        exportaciones.validacion(acceso.empresaPermitida(empresa), periodo, formato, acceso.actor().puedeAdministrar)

    @PostMapping
    fun generar(@RequestParam(required = false) empresa: String?, @RequestBody request: ExportacionRequest): ResponseEntity<ExportacionResponse> {
        val actor = acceso.administrador()
        return ResponseEntity.status(HttpStatus.CREATED).body(exportaciones.generar(acceso.empresaPermitida(empresa), request, actor.autor()))
    }

    @GetMapping
    fun listar(
        @RequestParam(required = false) empresa: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PaginaExportacionesResponse =
        exportaciones.listar(acceso.empresaPermitida(empresa), page, size, acceso.actor().puedeAdministrar)

    @GetMapping("/{id}")
    fun ver(@PathVariable id: Long, @RequestParam(required = false) empresa: String?): ExportacionResponse =
        exportaciones.ver(acceso.empresaPermitida(empresa), id, acceso.actor().puedeAdministrar)

    @GetMapping("/{id}/archivo")
    fun archivo(@PathVariable id: Long, @RequestParam(required = false) empresa: String?, @RequestParam(required = false) tipo: String?) =
        descarga(exportaciones.archivo(acceso.empresaPermitida(empresa), id, tipo))

    @PostMapping("/{id}/constancia", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun constancia(
        @PathVariable id: Long,
        @RequestParam(required = false) empresa: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fechaEnvio: LocalDate?,
        @RequestParam(required = false) numeroAcuse: String?,
        @RequestParam(required = false) notas: String?,
        @RequestParam(required = false) archivo: MultipartFile?
    ): ConstanciaRegistradaResponse {
        val actor = acceso.administrador()
        return exportaciones.constancia(acceso.empresaPermitida(empresa), id, fechaEnvio, numeroAcuse, notas, archivo, actor.autor())
    }

    @GetMapping("/{id}/constancia/archivo")
    fun archivoConstancia(@PathVariable id: Long, @RequestParam(required = false) empresa: String?) =
        descarga(exportaciones.archivoConstancia(acceso.empresaPermitida(empresa), id))
}
