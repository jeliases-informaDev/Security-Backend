package com.security.shared.exceptions

/** 404 de negocio. `code` (opcional) viaja en el JSON de error, p. ej. OPERACIONES_NO_ENCONTRADA. */
open class ResourceNotFoundException(message: String, val code: String? = null) : RuntimeException(message)
