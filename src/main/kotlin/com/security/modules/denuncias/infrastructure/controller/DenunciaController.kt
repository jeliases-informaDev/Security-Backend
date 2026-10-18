package com.security.modules.denuncias.infrastructure.controller

import com.security.modules.denuncias.application.dto.ActualizarEstadoDenunciaRequest
import com.security.modules.denuncias.application.dto.ConsultarDenunciaRequest
import com.security.modules.denuncias.application.dto.ConsultarDenunciaResponse
import com.security.modules.denuncias.application.dto.CrearDenunciaRequest
import com.security.modules.denuncias.application.dto.CrearDenunciaResponse
import com.security.modules.denuncias.application.dto.DenunciaArchivoResponse
import com.security.modules.denuncias.application.dto.DenunciaDetalleResponse
import com.security.modules.denuncias.application.dto.DenunciaResumenResponse
import com.security.modules.denuncias.application.dto.ReceptorDenunciaResponse

import com.security.modules.denuncias.application.usecase.ActualizarEstadoDenunciaUseCase
import com.security.modules.denuncias.application.usecase.ConsultarDenunciaUseCase
import com.security.modules.denuncias.application.usecase.CrearDenunciaUseCase
import com.security.modules.denuncias.application.usecase.ListarDenunciasUseCase
import com.security.modules.denuncias.application.usecase.ListarReceptoresDenunciaUseCase
import com.security.modules.denuncias.application.usecase.ObtenerDenunciaUseCase
import com.security.modules.denuncias.application.usecase.SubirDocumentosDenunciaUseCase

import com.security.modules.denuncias.domain.enums.EstadoDenuncia

import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository

import jakarta.validation.Valid

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import com.security.modules.denuncias.application.usecase.DescargarDocumentoDenunciaUseCase

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders

import java.nio.charset.StandardCharsets

import org.springframework.web.multipart.MultipartFile

import org.springframework.web.server.ResponseStatusException

import java.security.Principal


@RestController
@RequestMapping("/api/denuncias")
class DenunciaController(

    private val crearDenunciaUseCase:
    CrearDenunciaUseCase,

    private val consultarDenunciaUseCase:
    ConsultarDenunciaUseCase,

    private val subirDocumentosDenunciaUseCase:
    SubirDocumentosDenunciaUseCase,

    private val listarDenunciasUseCase:
    ListarDenunciasUseCase,

    private val obtenerDenunciaUseCase:
    ObtenerDenunciaUseCase,

    private val actualizarEstadoDenunciaUseCase:
    ActualizarEstadoDenunciaUseCase,

    private val listarReceptoresDenunciaUseCase:
    ListarReceptoresDenunciaUseCase,

    private val descargarDocumentoDenunciaUseCase:
    DescargarDocumentoDenunciaUseCase,

    private val usuarioRepository:
    UsuarioRepository

) {

    // =========================================================
    // REGISTRAR DENUNCIA
    // =========================================================

    @PostMapping
    fun crear(
        principal: Principal?,

        @Valid
        @RequestBody
        request: CrearDenunciaRequest

    ): ResponseEntity<CrearDenunciaResponse> {

        val usuarioCreador =
            principal
                ?.name
                ?.let { usuario ->

                    usuarioRepository
                        .findByUsuario(
                            usuario
                        )
                }

        val resultado =
            crearDenunciaUseCase
                .ejecutar(
                    request =
                        request,

                    usuarioCreador =
                        usuarioCreador
                )

        return ResponseEntity
            .status(
                HttpStatus.CREATED
            )
            .body(
                resultado
            )
    }


    // =========================================================
    // CONSULTAR SEGUIMIENTO
    // =========================================================

    @PostMapping("/seguimiento")
    fun consultarSeguimiento(

        @Valid
        @RequestBody
        request: ConsultarDenunciaRequest

    ): ResponseEntity<ConsultarDenunciaResponse> {

        val resultado =
            consultarDenunciaUseCase
                .ejecutar(
                    request
                )

        return ResponseEntity.ok(
            resultado
        )
    }


    // =========================================================
    // SUBIR DOCUMENTOS
    // =========================================================

    @PostMapping(
        value = [
            "/{codigoSeguimiento}/archivos"
        ],
        consumes = [
            MediaType.MULTIPART_FORM_DATA_VALUE
        ]
    )
    fun subirDocumentos(

        @PathVariable
        codigoSeguimiento: String,

        @RequestParam("pin")
        pin: String,

        @RequestParam("archivos")
        archivos: List<MultipartFile>

    ): ResponseEntity<List<DenunciaArchivoResponse>> {

        val resultado =
            subirDocumentosDenunciaUseCase
                .ejecutar(
                    codigoSeguimiento =
                        codigoSeguimiento,

                    pin =
                        pin,

                    archivos =
                        archivos
                )

        return ResponseEntity
            .status(
                HttpStatus.CREATED
            )
            .body(
                resultado
            )
    }

    // =========================================================
// DESCARGAR DOCUMENTO
// =========================================================

    @GetMapping(
        "/{denunciaId}/archivos/{archivoId}/descargar"
    )
    fun descargarDocumento(

        principal: Principal,

        @PathVariable
        denunciaId: Int,

        @PathVariable
        archivoId: Int

    ): ResponseEntity<ByteArray> {

        // =====================================================
        // USUARIO AUTENTICADO
        // =====================================================

        val usuarioResponsable =
            obtenerUsuarioAutenticado(
                principal
            )


        // =====================================================
        // OBTENER DOCUMENTO
        // =====================================================

        val documento =
            descargarDocumentoDenunciaUseCase
                .ejecutar(
                    denunciaId =
                        denunciaId,

                    archivoId =
                        archivoId,

                    usuarioResponsable =
                        usuarioResponsable
                )


        // =====================================================
        // NOMBRE ORIGINAL DEL ARCHIVO
        // =====================================================

        val contentDisposition =
            ContentDisposition
                .attachment()
                .filename(
                    documento.nombreOriginal,
                    StandardCharsets.UTF_8
                )
                .build()


        // =====================================================
        // RESPUESTA
        // =====================================================

        return ResponseEntity
            .ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                contentDisposition.toString()
            )
            .header(
                HttpHeaders.CONTENT_TYPE,
                documento.tipoContenido
            )
            .header(
                HttpHeaders.CONTENT_LENGTH,
                documento.contenido.size.toString()
            )
            .body(
                documento.contenido
            )
    }


    // =========================================================
    // LISTAR DENUNCIAS
    // =========================================================

    @GetMapping
    fun listar(

        principal: Principal,

        @RequestParam(required = false)
        estado: EstadoDenuncia?

    ): ResponseEntity<List<DenunciaResumenResponse>> {

        val usuarioResponsable =
            obtenerUsuarioAutenticado(
                principal
            )


        val resultado =
            listarDenunciasUseCase
                .ejecutar(
                    estado =
                        estado,

                    usuarioResponsable =
                        usuarioResponsable
                )


        return ResponseEntity.ok(
            resultado
        )
    }

    // =========================================================
    // LISTAR RECEPTORES
    // =========================================================

    @GetMapping("/receptores")
    fun listarReceptores(
        principal: Principal
    ): ResponseEntity<List<ReceptorDenunciaResponse>> {

        val resultado =
            listarReceptoresDenunciaUseCase
                .ejecutar(
                    obtenerUsuarioAutenticado(
                        principal
                    )
                )

        return ResponseEntity.ok(
            resultado
        )
    }


    // =========================================================
    // OBTENER DETALLE
    // =========================================================

    @GetMapping("/{id}")
    fun obtenerDetalle(

        principal: Principal,

        @PathVariable
        id: Int

    ): ResponseEntity<DenunciaDetalleResponse> {

        val usuarioResponsable =
            obtenerUsuarioAutenticado(
                principal
            )


        val resultado =
            obtenerDenunciaUseCase
                .ejecutar(
                    id =
                        id,

                    usuarioResponsable =
                        usuarioResponsable
                )


        return ResponseEntity.ok(
            resultado
        )
    }

    // =========================================================
    // ACTUALIZAR ESTADO
    // =========================================================

    @PatchMapping("/{id}/estado")
    fun actualizarEstado(

        principal: Principal,

        @PathVariable
        id: Int,

        @Valid
        @RequestBody
        request: ActualizarEstadoDenunciaRequest

    ): ResponseEntity<DenunciaDetalleResponse> {

        val usuarioResponsable =
            obtenerUsuarioAutenticado(
                principal
            )


        val resultado =
            actualizarEstadoDenunciaUseCase
                .ejecutar(
                    id =
                        id,

                    request =
                        request,

                    usuarioResponsable =
                        usuarioResponsable
                )


        return ResponseEntity.ok(
            resultado
        )
    }


    // =========================================================
    // USUARIO AUTENTICADO
    // =========================================================

    private fun obtenerUsuarioAutenticado(
        principal: Principal
    ): Usuario {

        return usuarioRepository
            .findByUsuario(
                principal.name
            )
            ?: throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "No se encontró el usuario autenticado."
            )
    }
}