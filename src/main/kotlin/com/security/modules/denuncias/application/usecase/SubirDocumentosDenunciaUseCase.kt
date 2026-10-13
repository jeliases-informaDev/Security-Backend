package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.dto.DenunciaArchivoResponse
import com.security.modules.denuncias.application.port.ArchivoStorageService
import com.security.modules.denuncias.application.service.SeguimientoDenunciaService
import com.security.modules.denuncias.application.service.ValidadorDocumentoDenunciaService
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaArchivoEntity
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaArchivoRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@Service
class SubirDocumentosDenunciaUseCase(

    private val denunciaRepository:
    DenunciaRepository,

    private val denunciaArchivoRepository:
    DenunciaArchivoRepository,

    private val seguimientoDenunciaService:
    SeguimientoDenunciaService,

    private val validadorDocumentoDenunciaService:
    ValidadorDocumentoDenunciaService,

    private val archivoStorageService:
    ArchivoStorageService
) {

    companion object {

        private const val MAX_DOCUMENTOS =
            5
    }


    // =========================================================
    // EJECUTAR
    // =========================================================

    @Transactional
    fun ejecutar(
        codigoSeguimiento: String,
        pin: String,
        archivos: List<MultipartFile>
    ): List<DenunciaArchivoResponse> {

        val codigoNormalizado =
            codigoSeguimiento
                .trim()
                .uppercase()

        val pinNormalizado =
            pin
                .trim()
                .uppercase()

        if (codigoNormalizado.isBlank()) {
            throw badRequest(
                "El identificador de la denuncia es obligatorio."
            )
        }

        if (pinNormalizado.isBlank()) {
            throw badRequest(
                "El PIN es obligatorio."
            )
        }

        if (archivos.isEmpty()) {
            throw badRequest(
                "Debes seleccionar al menos un documento."
            )
        }

        // =====================================================
        // DENUNCIA
        // =====================================================

        val denuncia =
            denunciaRepository
                .findByCodigoSeguimiento(
                    codigoNormalizado
                )
                ?: throw credencialesInvalidas()

        val pinHash =
            denuncia.pinHash
                ?: throw credencialesInvalidas()

        val pinValido =
            seguimientoDenunciaService
                .validarPin(
                    pin = pinNormalizado,
                    pinHash = pinHash
                )

        if (!pinValido) {
            throw credencialesInvalidas()
        }

        val denunciaId =
            denuncia.id
                ?: throw IllegalStateException(
                    "La denuncia no posee un identificador válido."
                )

        // =====================================================
        // CANTIDAD MÁXIMA
        // =====================================================

        val documentosExistentes =
            denunciaArchivoRepository
                .countByDenuncia_Id(
                    denunciaId
                )

        val totalDocumentos =
            documentosExistentes +
                    archivos.size

        if (
            totalDocumentos >
            MAX_DOCUMENTOS
        ) {

            val disponibles =
                (
                        MAX_DOCUMENTOS -
                                documentosExistentes
                        )
                    .coerceAtLeast(0)

            throw badRequest(
                if (disponibles == 0L) {
                    "La denuncia ya alcanzó el máximo de 5 documentos."
                } else {
                    "Solo puedes agregar $disponibles documento(s) adicional(es)."
                }
            )
        }

        // =====================================================
        // GUARDAR
        // =====================================================

        val rutasGuardadas =
            mutableListOf<String>()

        val respuestas =
            mutableListOf<DenunciaArchivoResponse>()

        try {

            archivos.forEach { archivo ->

                // ---------------------------------------------
                // VALIDAR DOCUMENTO
                // ---------------------------------------------

                val documento =
                    validadorDocumentoDenunciaService
                        .validar(
                            archivo
                        )

                // ---------------------------------------------
                // OBJECT KEY / RUTA LÓGICA
                // ---------------------------------------------

                val rutaArchivo =
                    construirRutaArchivo(
                        codigoSeguimiento =
                            codigoNormalizado,

                        nombreArchivo =
                            documento.nombreArchivo
                    )

                // ---------------------------------------------
                // STORAGE
                // ---------------------------------------------

                val archivoGuardado =
                    archivoStorageService
                        .guardar(
                            rutaArchivo =
                                rutaArchivo,

                            contenido =
                                documento.contenido,

                            tipoContenido =
                                documento.tipoContenido
                        )

                rutasGuardadas.add(
                    archivoGuardado.rutaArchivo
                )

                // ---------------------------------------------
                // MYSQL
                // ---------------------------------------------

                val entidad =
                    DenunciaArchivoEntity(

                        denuncia =
                            denuncia,

                        nombreOriginal =
                            documento.nombreOriginal,

                        nombreArchivo =
                            documento.nombreArchivo,

                        tipoContenido =
                            documento.tipoContenido,

                        rutaArchivo =
                            archivoGuardado.rutaArchivo,

                        tamanoBytes =
                            documento.tamanoBytes,

                        proveedorAlmacenamiento =
                            archivoGuardado.proveedor,

                        bucket =
                            archivoGuardado.bucket,

                        sha256 =
                            documento.sha256
                    )

                val guardado =
                    denunciaArchivoRepository
                        .save(
                            entidad
                        )

                respuestas.add(
                    convertirResponse(
                        guardado
                    )
                )
            }

            /*
             * Fuerza la ejecución de los INSERT antes de
             * considerar finalizada la operación.
             */
            denunciaArchivoRepository
                .flush()

            return respuestas

        } catch (exception: Exception) {

            /*
             * La transacción de BD se revertirá.
             *
             * Como el filesystem / Oracle no participan
             * de la transacción de MySQL, eliminamos los
             * objetos que ya hubieran sido almacenados.
             */
            rutasGuardadas
                .asReversed()
                .forEach { ruta ->

                    try {

                        archivoStorageService
                            .eliminar(
                                ruta
                            )

                    } catch (_: Exception) {

                        /*
                         * No reemplazamos la excepción
                         * original por un error de limpieza.
                         */
                    }
                }

            throw exception
        }
    }


    // =========================================================
    // RUTA LÓGICA
    // =========================================================

    private fun construirRutaArchivo(
        codigoSeguimiento: String,
        nombreArchivo: String
    ): String {

        return "denuncias/" +
                codigoSeguimiento +
                "/" +
                nombreArchivo
    }


    // =========================================================
    // RESPONSE
    // =========================================================

    private fun convertirResponse(
        archivo: DenunciaArchivoEntity
    ): DenunciaArchivoResponse {

        return DenunciaArchivoResponse(

            id =
                archivo.id
                    ?: throw IllegalStateException(
                        "El documento almacenado no posee identificador."
                    ),

            nombreOriginal =
                archivo.nombreOriginal,

            tipoContenido =
                archivo.tipoContenido,

            tamanoBytes =
                archivo.tamanoBytes,

            fechaCreacion =
                archivo.fechaCreacion
        )
    }


    // =========================================================
    // ERRORES
    // =========================================================

    private fun credencialesInvalidas():
            ResponseStatusException {

        /*
         * Mismo mensaje tanto si:
         *
         * - no existe el código
         * - no existe PIN para la denuncia
         * - el PIN es incorrecto
         *
         * De esta forma no revelamos qué dato fue válido.
         */
        return ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "No fue posible acceder a la denuncia con los datos proporcionados."
        )
    }


    private fun badRequest(
        mensaje: String
    ): ResponseStatusException {

        return ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            mensaje
        )
    }
}