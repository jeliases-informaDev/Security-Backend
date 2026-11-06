package com.security.modules.oficial.web

import com.security.modules.oficial.aplicacion.AccesoOficialService
import com.security.modules.oficial.aplicacion.CatalogoOficialService
import com.security.modules.oficial.aplicacion.CatalogosOcResponse
import com.security.modules.oficial.aplicacion.FeriadoRequest
import com.security.modules.oficial.aplicacion.FeriadoResponse
import com.security.modules.oficial.aplicacion.OcErrorResponse
import com.security.modules.oficial.aplicacion.OficialException
import com.security.modules.oficial.aplicacion.ReglaRequest
import com.security.modules.oficial.aplicacion.ReglaResponse
import com.security.modules.oficial.aplicacion.ReglasOficialService
import com.security.modules.oficial.aplicacion.ReglasPorDefecto
import com.security.modules.oficial.dominio.Plazo
import jakarta.servlet.http.HttpServletRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Catalogos, plazos, reglas y feriados del modulo Oficial de Cumplimiento. Contrato: docs/OFICIAL-CUMPLIMIENTO.md.
 * La empresa siempre sale de la sesion (solo el administrador universal puede pasar ?empresa=).
 */
@RestController
@RequestMapping("/api/oficial-cumplimiento")
class OficialController(
    private val acceso: AccesoOficialService,
    private val catalogos: CatalogoOficialService,
    private val reglas: ReglasOficialService
) {

    @GetMapping("/catalogos")
    fun catalogos(@RequestParam(required = false) empresa: String?): CatalogosOcResponse {
        acceso.contexto(empresa)
        return catalogos.catalogos()
    }

    @GetMapping("/plazos")
    fun plazo(
        @RequestParam(required = false) regla: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) desde: LocalDate?,
        @RequestParam(required = false) empresa: String?
    ): Plazo {
        acceso.contexto(empresa)
        return reglas.plazo(regla, desde)
    }

    @GetMapping("/plazos/remocion")
    fun plazosRemocion(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fechaDecision: LocalDate?,
        @RequestParam(required = false) empresa: String?
    ): PlazosRemocionResponse {
        acceso.contexto(empresa)
        if (fechaDecision == null) {
            throw com.security.modules.oficial.aplicacion.Oc.dato("fechaDecision", "Indica la fecha de la decisión.")
        }
        val motor = reglas.motor()
        return PlazosRemocionResponse(
            comunicarRemocion = motor.plazo(ReglasPorDefecto.PLAZO_COMUNICAR_REMOCION, fechaDecision),
            vacancia = motor.plazo(ReglasPorDefecto.VACANCIA_MAXIMA, fechaDecision),
            alternoMaximoMeses = motor.valor(ReglasPorDefecto.ALTERNO_MAXIMO)
        )
    }

    @PutMapping("/reglas/{codigo}")
    fun cambiarRegla(
        @PathVariable codigo: String,
        @RequestBody pedido: ReglaRequest,
        @RequestParam(required = false) empresa: String?
    ): ReglaResponse = reglas.cambiarRegla(acceso.contexto(empresa), codigo, pedido)

    @GetMapping("/feriados")
    fun feriados(
        @RequestParam(required = false) anio: Int?,
        @RequestParam(required = false) empresa: String?
    ): List<FeriadoResponse> {
        acceso.contexto(empresa)
        return reglas.feriados(anio)
    }

    @PostMapping("/feriados")
    fun agregarFeriado(
        @RequestBody pedido: FeriadoRequest,
        @RequestParam(required = false) empresa: String?
    ): ResponseEntity<FeriadoResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(reglas.agregarFeriado(acceso.contexto(empresa), pedido))

    @DeleteMapping("/feriados/{fecha}")
    fun quitarFeriado(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fecha: LocalDate,
        @RequestParam(required = false) empresa: String?
    ): ResponseEntity<Void> {
        reglas.quitarFeriado(acceso.contexto(empresa), fecha)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(OficialException::class)
    fun error(ex: OficialException, request: HttpServletRequest): ResponseEntity<OcErrorResponse> = respuestaError(ex, request)
}

data class PlazosRemocionResponse(val comunicarRemocion: Plazo, val vacancia: Plazo, val alternoMaximoMeses: Int)

/** JSON fijo: el error tambien llega a quien pidio un PDF o un archivo. */
fun respuestaError(ex: OficialException, request: HttpServletRequest): ResponseEntity<OcErrorResponse> =
    ResponseEntity.status(ex.status).contentType(MediaType.APPLICATION_JSON).body(
        OcErrorResponse(
            status = ex.status.value(),
            error = ex.status.reasonPhrase,
            message = ex.message ?: "Solicitud inválida",
            path = request.requestURI,
            code = ex.code,
            campos = ex.campos
        )
    )
