package com.security.modules.oficial.aplicacion

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifra los codigos secretos de la UIF con AES-256-GCM. La clave sale de OC_CLAVE_CIFRADO (32 bytes en base64, p. ej.
 * `openssl rand -base64 32`). Sin clave valida no se cifra ni se guarda nada (409). Ni la clave ni los codigos van al log.
 * Formato guardado: "v1:" + base64(iv de 12 bytes + texto cifrado con su etiqueta de 16 bytes). El contexto (uuid de la
 * designacion y nombre del campo) va como dato asociado: un valor copiado a otra fila o a otro campo no descifra.
 */
@Component
class CifradoCodigosOficial(@Value("\${OC_CLAVE_CIFRADO:}") claveTexto: String) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val aleatorio = SecureRandom()
    private val clave: SecretKey? = leerClave(claveTexto)

    val disponible: Boolean get() = clave != null

    fun cifrar(texto: String, contexto: String): String {
        val k = clave ?: throw sinClave()
        val iv = ByteArray(12).also { aleatorio.nextBytes(it) }
        val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
        cifrador.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        cifrador.updateAAD(contexto.toByteArray(StandardCharsets.UTF_8))
        val cifrado = cifrador.doFinal(texto.toByteArray(StandardCharsets.UTF_8))
        return PREFIJO + Base64.getEncoder().encodeToString(iv + cifrado)
    }

    /** Solo para pruebas y una eventual funcion de «revelar» (hoy ningun endpoint lo usa). */
    internal fun descifrar(guardado: String, contexto: String): String {
        val k = clave ?: throw sinClave()
        require(guardado.startsWith(PREFIJO)) { "Formato desconocido" }
        val bytes = Base64.getDecoder().decode(guardado.removePrefix(PREFIJO))
        val descifrador = Cipher.getInstance("AES/GCM/NoPadding")
        descifrador.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        descifrador.updateAAD(contexto.toByteArray(StandardCharsets.UTF_8))
        return String(descifrador.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }

    private fun leerClave(texto: String): SecretKey? {
        val limpio = texto.trim()
        if (limpio.isEmpty()) {
            log.info("OC_CLAVE_CIFRADO no está configurada: los códigos secretos de la UIF no se podrán guardar.")
            return null
        }
        val bytes = try {
            if (limpio.length == 64 && limpio.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                limpio.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } else Base64.getDecoder().decode(limpio)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (bytes == null || bytes.size != 32) {
            log.warn("OC_CLAVE_CIFRADO no es válida (deben ser 32 bytes en base64): los códigos secretos no se podrán guardar.")
            return null
        }
        return SecretKeySpec(bytes, "AES")
    }

    private fun sinClave() = OficialException(
        HttpStatus.CONFLICT, Oc.CIFRADO_NO_CONFIGURADO,
        "El servidor no tiene configurada la clave para guardar los códigos secretos: no se guardó nada. Avísale al administrador de Sulotec."
    )

    fun exigirDisponible() {
        if (clave == null) throw sinClave()
    }

    companion object {
        const val PREFIJO = "v1:"
    }
}
