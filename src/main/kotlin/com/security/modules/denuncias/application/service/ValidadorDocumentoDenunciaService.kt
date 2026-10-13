package com.security.modules.denuncias.application.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

@Service
class ValidadorDocumentoDenunciaService {

    companion object {

        private const val MAX_TAMANO_BYTES =
            10L * 1024L * 1024L

        private val EXTENSIONES_PERMITIDAS =
            setOf(
                "pdf",
                "doc",
                "docx"
            )

        private val MIME_PDF =
            setOf(
                "application/pdf",
                "application/octet-stream"
            )

        private val MIME_DOC =
            setOf(
                "application/msword",
                "application/octet-stream"
            )

        private val MIME_DOCX =
            setOf(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/zip",
                "application/octet-stream"
            )

        /*
         * Firma de archivo DOC antiguo:
         *
         * D0 CF 11 E0 A1 B1 1A E1
         */
        private val FIRMA_DOC =
            byteArrayOf(
                0xD0.toByte(),
                0xCF.toByte(),
                0x11.toByte(),
                0xE0.toByte(),
                0xA1.toByte(),
                0xB1.toByte(),
                0x1A.toByte(),
                0xE1.toByte()
            )
    }


    // =========================================================
    // VALIDAR
    // =========================================================

    fun validar(
        archivo: MultipartFile
    ): DocumentoDenunciaValidado {

        if (archivo.isEmpty) {
            throw badRequest(
                "El documento está vacío."
            )
        }

        if (
            archivo.size >
            MAX_TAMANO_BYTES
        ) {
            throw badRequest(
                "El documento supera el tamaño máximo permitido de 10 MB."
            )
        }

        val nombreOriginal =
            obtenerNombreSeguro(
                archivo.originalFilename
            )

        val extension =
            obtenerExtension(
                nombreOriginal
            )

        if (
            extension !in
            EXTENSIONES_PERMITIDAS
        ) {
            throw badRequest(
                "Solo se permiten documentos PDF, DOC y DOCX."
            )
        }

        val contenido =
            try {
                archivo.bytes
            } catch (exception: Exception) {
                throw badRequest(
                    "No fue posible leer el documento."
                )
            }

        val tipoContenido =
            normalizarMime(
                archivo.contentType
            )

        validarMime(
            extension = extension,
            mime = tipoContenido
        )

        validarContenidoReal(
            extension = extension,
            contenido = contenido
        )

        val sha256 =
            calcularSha256(
                contenido
            )

        val nombreArchivo =
            generarNombreInterno(
                extension
            )

        return DocumentoDenunciaValidado(
            nombreOriginal =
                nombreOriginal,

            nombreArchivo =
                nombreArchivo,

            extension =
                extension,

            tipoContenido =
                obtenerMimeFinal(
                    extension
                ),

            tamanoBytes =
                archivo.size,

            sha256 =
                sha256,

            contenido =
                contenido
        )
    }


    // =========================================================
    // NOMBRE ORIGINAL SEGURO
    // =========================================================

    private fun obtenerNombreSeguro(
        nombreOriginal: String?
    ): String {

        if (
            nombreOriginal
                .isNullOrBlank()
        ) {
            throw badRequest(
                "El documento no tiene un nombre válido."
            )
        }

        val nombre =
            try {
                Paths.get(
                    nombreOriginal
                )
                    .fileName
                    .toString()
                    .trim()
            } catch (exception: Exception) {
                throw badRequest(
                    "El nombre del documento no es válido."
                )
            }

        if (nombre.isBlank()) {
            throw badRequest(
                "El nombre del documento no es válido."
            )
        }

        if (nombre.length > 255) {
            throw badRequest(
                "El nombre del documento es demasiado largo."
            )
        }

        /*
         * Eliminamos caracteres de control.
         */
        return nombre
            .filterNot {
                it.isISOControl()
            }
    }


    // =========================================================
    // EXTENSIÓN
    // =========================================================

    private fun obtenerExtension(
        nombreArchivo: String
    ): String {

        val posicion =
            nombreArchivo
                .lastIndexOf('.')

        if (
            posicion <= 0 ||
            posicion ==
            nombreArchivo.length - 1
        ) {
            throw badRequest(
                "El documento debe tener extensión PDF, DOC o DOCX."
            )
        }

        return nombreArchivo
            .substring(
                posicion + 1
            )
            .lowercase()
    }


    // =========================================================
    // MIME
    // =========================================================

    private fun normalizarMime(
        mime: String?
    ): String {

        return mime
            ?.trim()
            ?.lowercase()
            ?: "application/octet-stream"
    }


    private fun validarMime(
        extension: String,
        mime: String
    ) {

        val permitido =
            when (extension) {

                "pdf" ->
                    mime in MIME_PDF

                "doc" ->
                    mime in MIME_DOC

                "docx" ->
                    mime in MIME_DOCX

                else ->
                    false
            }

        if (!permitido) {
            throw badRequest(
                "El tipo de contenido del documento no es válido."
            )
        }
    }


    // =========================================================
    // FIRMA REAL DEL DOCUMENTO
    // =========================================================

    private fun validarContenidoReal(
        extension: String,
        contenido: ByteArray
    ) {

        when (extension) {

            "pdf" ->
                validarPdf(
                    contenido
                )

            "doc" ->
                validarDoc(
                    contenido
                )

            "docx" ->
                validarDocx(
                    contenido
                )

            else ->
                throw badRequest(
                    "Tipo de documento no permitido."
                )
        }
    }


    // =========================================================
    // PDF
    // =========================================================

    private fun validarPdf(
        contenido: ByteArray
    ) {

        if (contenido.size < 5) {
            throw badRequest(
                "El archivo PDF no es válido."
            )
        }

        val firmaPdf =
            byteArrayOf(
                '%'.code.toByte(),
                'P'.code.toByte(),
                'D'.code.toByte(),
                'F'.code.toByte(),
                '-'.code.toByte()
            )

        if (
            !empiezaCon(
                contenido,
                firmaPdf
            )
        ) {
            throw badRequest(
                "El archivo no corresponde a un PDF válido."
            )
        }
    }


    // =========================================================
    // DOC
    // =========================================================

    private fun validarDoc(
        contenido: ByteArray
    ) {

        if (
            contenido.size <
            FIRMA_DOC.size
        ) {
            throw badRequest(
                "El archivo DOC no es válido."
            )
        }

        if (
            !empiezaCon(
                contenido,
                FIRMA_DOC
            )
        ) {
            throw badRequest(
                "El archivo no corresponde a un documento DOC válido."
            )
        }
    }


    // =========================================================
    // DOCX
    // =========================================================

    private fun validarDocx(
        contenido: ByteArray
    ) {

        /*
         * DOCX es un archivo ZIP, pero no debemos
         * aceptar cualquier ZIP renombrado a .docx.
         *
         * Por eso buscamos elementos propios de
         * un documento de Microsoft Word.
         */

        var tieneContentTypes =
            false

        var tieneDocumentoWord =
            false

        try {

            ZipInputStream(
                ByteArrayInputStream(
                    contenido
                )
            ).use { zip ->

                var entrada =
                    zip.nextEntry

                while (
                    entrada != null
                ) {

                    when (
                        entrada.name
                            .replace(
                                "\\",
                                "/"
                            )
                    ) {

                        "[Content_Types].xml" ->
                            tieneContentTypes =
                                true

                        "word/document.xml" ->
                            tieneDocumentoWord =
                                true
                    }

                    if (
                        tieneContentTypes &&
                        tieneDocumentoWord
                    ) {
                        break
                    }

                    entrada =
                        zip.nextEntry
                }
            }

        } catch (exception: Exception) {

            throw badRequest(
                "El archivo DOCX no es válido."
            )
        }

        if (
            !tieneContentTypes ||
            !tieneDocumentoWord
        ) {
            throw badRequest(
                "El archivo no corresponde a un documento DOCX válido."
            )
        }
    }


    // =========================================================
    // FIRMA
    // =========================================================

    private fun empiezaCon(
        contenido: ByteArray,
        firma: ByteArray
    ): Boolean {

        if (
            contenido.size <
            firma.size
        ) {
            return false
        }

        return firma.indices.all {
            contenido[it] ==
                    firma[it]
        }
    }


    // =========================================================
    // HASH SHA-256
    // =========================================================

    private fun calcularSha256(
        contenido: ByteArray
    ): String {

        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )
                .digest(
                    contenido
                )

        return digest
            .joinToString(
                separator = ""
            ) {
                "%02x".format(
                    it.toInt() and 0xff
                )
            }
    }


    // =========================================================
    // NOMBRE INTERNO
    // =========================================================

    private fun generarNombreInterno(
        extension: String
    ): String {

        return "${UUID.randomUUID()}.$extension"
    }


    // =========================================================
    // MIME FINAL
    // =========================================================

    private fun obtenerMimeFinal(
        extension: String
    ): String {

        return when (extension) {

            "pdf" ->
                "application/pdf"

            "doc" ->
                "application/msword"

            "docx" ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

            else ->
                "application/octet-stream"
        }
    }


    // =========================================================
    // ERROR
    // =========================================================

    private fun badRequest(
        mensaje: String
    ): ResponseStatusException {

        return ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            mensaje
        )
    }
}


// ============================================================
// RESULTADO VALIDADO
// ============================================================

data class DocumentoDenunciaValidado(

    val nombreOriginal: String,

    val nombreArchivo: String,

    val extension: String,

    val tipoContenido: String,

    val tamanoBytes: Long,

    val sha256: String,

    val contenido: ByteArray
)