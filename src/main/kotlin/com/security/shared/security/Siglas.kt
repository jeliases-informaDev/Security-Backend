package com.security.shared.security

import java.text.Normalizer

/**
 * Sigla de una empresa: 2 a 8 letras o digitos en mayusculas. Va fija delante del usuario de cada
 * cuenta (p. ej. `hcyo.jperez`), de modo que el identificador es unico entre empresas sin que la
 * persona tenga que saberlo (en pantalla se ve su nombre). El formato lo exige `SIGLA_VALIDA`.
 */
val SIGLA_VALIDA = Regex("^[A-Z0-9]{2,8}$")

fun normalizarSigla(valor: String?): String? =
    valor?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

/** Propone una sigla a partir del nombre: iniciales de las palabras significativas o las primeras letras. */
fun siglaSugerida(nombre: String): String {
    val sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    val palabras = sinTildes.uppercase().replace(".", "").split(Regex("[^A-Z0-9]+"))
        .filter { it.isNotEmpty() && it !in PALABRAS_VACIAS }
    val base = when {
        palabras.size >= 2 -> palabras.joinToString("") { it.take(1) }
        palabras.size == 1 -> palabras[0].take(4)
        else -> "EMP"
    }
    return base.take(8).padEnd(2, 'X')
}

/** Sigla que no choca con las existentes: agrega un numero si hace falta. */
fun siglaLibre(propuesta: String, existe: (String) -> Boolean): String {
    if (!existe(propuesta)) return propuesta
    for (n in 2..999) {
        val candidata = (propuesta.take(8 - n.toString().length) + n)
        if (!existe(candidata)) return candidata
    }
    throw IllegalStateException("No se pudo generar una sigla libre para '$propuesta'")
}

/** `sigla.usuario` en minusculas; si ya viene con ese prefijo, se deja igual. */
fun usuarioConSigla(sigla: String, usuario: String): String {
    val prefijo = sigla.trim().lowercase() + "."
    val limpio = usuario.trim().lowercase()
    return if (limpio.startsWith(prefijo)) limpio else prefijo + limpio
}

private val PALABRAS_VACIAS = setOf("SA", "SAC", "SRL", "EIRL", "SAA", "DE", "DEL", "LA", "EL", "LOS", "LAS", "Y", "E", "CIA", "LTDA", "INC")
