package com.security.modules.operaciones.controller.infrastructure

import com.security.modules.operaciones.application.service.AccesoEmpresaService
import com.security.modules.operaciones.application.service.AltaOperacionService
import com.security.modules.operaciones.application.service.ListaOperacionesService
import com.security.modules.operaciones.application.service.OperacionService
import com.security.modules.operaciones.application.service.ParametrosLista
import com.security.modules.operaciones.application.service.ResponsableOperacionService
import com.security.modules.operaciones.dto.AsignarResponsableRequest
import com.security.modules.operaciones.dto.AsignarResponsableResponse
import com.security.modules.operaciones.dto.CambiarUsuarioOperacionRequest
import com.security.modules.operaciones.dto.CambiarUsuarioOperacionResponse
import com.security.modules.operaciones.dto.IndicadoresOperacionesResponse
import com.security.modules.operaciones.dto.ListarOperacionesResponse
import com.security.modules.operaciones.dto.MostrarUsuarioResponse
import com.security.modules.operaciones.dto.RegistroCreadoResponse
import com.security.modules.operaciones.dto.ResponsablePosibleResponse
import com.security.modules.operaciones.dto.UsuarioEmpresaResponse
import com.tuempresa.operaciones.dto.RegistrarOperacionRequest
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

/**
 * La empresa y el usuario con los que se trabaja los decide AccesoEmpresaService a partir de la cuenta
 * autenticada: los parametros "empresa" y "usuario" solo los puede usar un ADMINISTRADOR.
 * Contrato: docs/REGISTRO-OPERACIONES.md.
 */
@RestController
@RequestMapping("/api/operaciones")
class OperacionController(
    private val operacionService: OperacionService,
    private val acceso: AccesoEmpresaService,
    private val alta: AltaOperacionService,
    private val lista: ListaOperacionesService,
    private val responsables: ResponsableOperacionService
) {

    // Cambia la cuenta duena del registro (no el responsable): queda como estaba, para el administrador.
    @PatchMapping("/{id}/usuario")
    fun cambiarUsuarioOperacion(
        @PathVariable("id") id: Long,
        @Valid @RequestBody request: CambiarUsuarioOperacionRequest
    ): CambiarUsuarioOperacionResponse {
        acceso.comprobarRegistro(id)
        return operacionService.cambiarUsuarioOperacion(id, request.usuarioId)
    }

    @GetMapping("/usuarios-por-empresa")
    fun listarUsuariosPorEmpresa(
        @RequestParam("empresa", required = false) empresa: String?,
        @RequestParam("soloActivos", defaultValue = "false") soloActivos: Boolean = false
    ): List<UsuarioEmpresaResponse> =
        operacionService.listarUsuariosPorEmpresa(acceso.empresaPermitida(empresa))
            .filter { !soloActivos || it.activo }

    @GetMapping("/resumen")
    fun listarResumen(
        @RequestParam("empresa", required = false) empresa: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) q: String? = null,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) desde: LocalDate? = null,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) hasta: LocalDate? = null,
        @RequestParam(required = false) tipoOperacion: String? = null,
        @RequestParam(required = false) modalidadPago: String? = null,
        @RequestParam(required = false) moneda: String? = null,
        @RequestParam(required = false) responsableId: Int? = null,
        @RequestParam(required = false) sinResponsable: Boolean? = null,
        @RequestParam(required = false) estado: List<String>? = null,
        @RequestParam(required = false) oficina: String? = null,
        @RequestParam(required = false) montoMinUsd: java.math.BigDecimal? = null,
        @RequestParam(required = false) montoMaxUsd: java.math.BigDecimal? = null
    ): ListarOperacionesResponse =
        lista.listar(
            acceso.empresaPermitida(empresa),
            ParametrosLista(q, desde, hasta, tipoOperacion, modalidadPago, moneda, responsableId, sinResponsable, null,
                estado.orEmpty(), oficina, montoMinUsd, montoMaxUsd),
            page, size
        )

    @GetMapping("/indicadores")
    fun indicadores(
        @RequestParam("empresa", required = false) empresa: String?,
        @RequestParam(required = false) periodo: String?
    ): IndicadoresOperacionesResponse = lista.indicadores(acceso.empresaPermitida(empresa), periodo)

    @GetMapping("/obtener-usuario")
    fun obtenerUsuario(@RequestParam("usuario", required = false) usuario: String?): MostrarUsuarioResponse {
        return operacionService.obtenerUsuarioActual(acceso.usuarioPermitido(usuario))
    }

    // Consume operacion_registrar. El numero, la fecha, la empresa y el responsable los pone el servidor.
    @PostMapping("/insertar-operacion")
    fun registrar(@RequestBody request: RegistrarOperacionRequest): ResponseEntity<RegistroCreadoResponse> {
        val propio = request.copy(registro = acceso.registroDelActor(request.registro))
        return ResponseEntity.status(HttpStatus.CREATED).body(alta.registrar(propio, acceso.actor().autor()))
    }

    @GetMapping("/{id}/responsables")
    fun responsablesPosibles(@PathVariable("id") id: Long): List<ResponsablePosibleResponse> {
        acceso.comprobarRegistro(id)
        return responsables.posibles(id)
    }

    // No consume tokens; queda en el historial.
    @PutMapping("/{id}/responsable")
    fun cambiarResponsable(
        @PathVariable("id") id: Long,
        @Valid @RequestBody request: AsignarResponsableRequest
    ): AsignarResponsableResponse {
        acceso.comprobarRegistro(id)
        return responsables.cambiar(id, request.usuarioId, acceso.actor().autor())
    }
}
