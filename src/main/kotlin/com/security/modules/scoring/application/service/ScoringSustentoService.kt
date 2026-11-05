package com.security.modules.scoring.application.service

import com.security.modules.denuncias.application.port.ArchivoStorageService
import com.security.modules.scoring.domain.ScoringSustento
import com.security.modules.scoring.dto.SustentoResponse
import com.security.modules.scoring.infrastructure.ScoringSustentoRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Sustento del origen de los fondos: un PDF o una imagen de hasta 10 MB, guardado con el mismo almacenamiento
 * del canal de denuncias (disco local o bucket de Oracle, segun STORAGE_PROVIDER). El tipo se decide por el
 * contenido del archivo, no por su nombre. En esta version nunca bloquea el calculo.
 */
@Service
class ScoringSustentoService(
    private val storage: ArchivoStorageService,
    private val sustentoRepository: ScoringSustentoRepository,
    private val usuarioRepository: UsuarioRepository
) {

    @Transactional
    fun guardar(username: String, archivo: MultipartFile?): SustentoResponse {
        val usuario = usuarioRepository.findByUsuario(username) ?: throw ResourceNotFoundException("Usuario no encontrado")
        if (archivo == null || archivo.isEmpty) {
            throw ScoringException(HttpStatus.BAD_REQUEST, "SCORING_SUSTENTO_VACIO", "Adjunta el archivo del sustento.")
        }
        if (archivo.size > MAXIMO_BYTES) {
            throw ScoringException(HttpStatus.BAD_REQUEST, "SCORING_SUSTENTO_GRANDE", "El sustento supera el tamaño máximo de 10 MB.")
        }
        val bytes = archivo.bytes
        val tipo = tipoDe(bytes)
            ?: throw ScoringException(HttpStatus.BAD_REQUEST, "SCORING_SUSTENTO_TIPO", "El sustento debe ser un PDF o una imagen (JPG o PNG).")

        val id = UUID.randomUUID().toString()
        val ruta = "scoring/sustentos/$id.${tipo.extension}"
        val guardado = storage.guardar(ruta, bytes, tipo.mime)
        try {
            sustentoRepository.save(
                ScoringSustento(
                    id = id,
                    usuario = usuario,
                    rutaArchivo = guardado.rutaArchivo,
                    proveedor = guardado.proveedor.name,
                    bucket = guardado.bucket,
                    tipoContenido = tipo.mime,
                    tamanoBytes = bytes.size.toLong()
                )
            )
        } catch (e: Exception) {
            runCatching { storage.eliminar(guardado.rutaArchivo) }
            throw e
        }
        return SustentoResponse(id)
    }

    internal data class TipoArchivo(val mime: String, val extension: String)

    companion object {
        const val MAXIMO_BYTES = 10L * 1024L * 1024L

        /** PDF, PNG o JPEG por su firma; null si es otra cosa. */
        internal fun tipoDe(b: ByteArray): TipoArchivo? = when {
            b.size >= 4 && b[0] == 0x25.toByte() && b[1] == 0x50.toByte() && b[2] == 0x44.toByte() && b[3] == 0x46.toByte() ->
                TipoArchivo("application/pdf", "pdf")
            b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() ->
                TipoArchivo("image/png", "png")
            b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() ->
                TipoArchivo("image/jpeg", "jpg")
            else -> null
        }
    }
}
