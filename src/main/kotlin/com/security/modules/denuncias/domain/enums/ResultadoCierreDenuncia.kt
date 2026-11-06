package com.security.modules.denuncias.domain.enums

/** Resultado al cerrar una denuncia. Las cerradas antes de existir este dato quedan sin resultado (null). */
enum class ResultadoCierreDenuncia(val etiqueta: String, val pideMotivo: Boolean) {
    FUNDADA("Fundada", false),
    INFUNDADA("Infundada", false),
    ARCHIVADA("Archivada", true)
}
