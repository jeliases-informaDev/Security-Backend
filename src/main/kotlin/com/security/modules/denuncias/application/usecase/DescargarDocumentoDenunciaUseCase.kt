package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.port.ArchivoStorageService
import com.security.modules.denuncias.application.service.AutorizacionDenunciaService
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaArchivoRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.shared.datos.entities.Usuario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException


@Service
class DescargarDocumentoDenunciaUseCase(

    private val denunciaRepository:
    DenunciaRepository,

    private val denunciaArchivoRepository:
    DenunciaArchivoRepository,

    private val archivoStorageService:
    ArchivoStorageService,

    private val autorizacionDenunciaService:
    AutorizacionDenunciaService

) {

    @Transactional(readOnly = true)
    fun ejecutar(
        denunciaId: Int,
        archivoId: Int,
        usuarioResponsable: Usuario
    ): DocumentoDenunciaDescargado {

        // =====================================================
        // BUSCAR DENUNCIA
        // =====================================================

        val denuncia =
            denunciaRepository
                .findById(
                    denunciaId
                )
                .orElseThrow {

                    ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la denuncia solicitada."
                    )
                }


        // =====================================================
        // VALIDAR AUTORIZACIÓN
        // =====================================================

        autorizacionDenunciaService
            .validarAccesoDenuncia(
                usuario =
                    usuarioResponsable,

                denuncia =
                    denuncia
            )


        // =====================================================
        // BUSCAR ARCHIVO
        // Y VALIDAR QUE PERTENEZCA A LA DENUNCIA
        // =====================================================

        val archivo =
            denunciaArchivoRepository
                .findByIdAndDenuncia_Id(
                    id =
                        archivoId,

                    denunciaId =
                        denunciaId
                )
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No se encontró el documento solicitado."
                )


        // =====================================================
        // RECUPERAR DESDE STORAGE
        // =====================================================

        val archivoDescargado =
            try {

                archivoStorageService
                    .obtener(
                        archivo.rutaArchivo
                    )

            } catch (exception: Exception) {

                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No fue posible recuperar el documento.",
                    exception
                )
            }


        // =====================================================
        // RESPUESTA
        // =====================================================

        return DocumentoDenunciaDescargado(

            nombreOriginal =
                archivo.nombreOriginal,

            tipoContenido =
                archivo.tipoContenido
                    ?: archivoDescargado.tipoContenido
                    ?: "application/octet-stream",

            contenido =
                archivoDescargado.contenido
        )
    }
}


data class DocumentoDenunciaDescargado(

    val nombreOriginal: String,

    val tipoContenido: String,

    val contenido: ByteArray
)