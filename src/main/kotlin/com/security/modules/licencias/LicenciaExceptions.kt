package com.security.modules.licencias

// Errores de licencia que ve la persona. Los resuelve GlobalExceptionHandler con el JSON de siempre más
// code, enlace y, según el caso, estado o activos y usuarios_max (contrato C2 con la web).

/** 402: la empresa usó todos los tokens del mes, incluida la tolerancia. */
class LicenciaBolsaAgotadaException(
    message: String = LicenciaReglas.MENSAJE_BOLSA_AGOTADA,
    val enlace: String = LicenciaReglas.ENLACE_MI_LICENCIA
) : RuntimeException(message) {
    val code: String = LicenciaReglas.CODE_BOLSA_AGOTADA
}

/** 403: la licencia no permite registrar cambios (SOLO_LECTURA, SUSPENDIDA o CANCELADA). */
class LicenciaBloqueadaException(
    val estado: String,
    val code: String,
    message: String = LicenciaReglas.mensajeDeEstado(estado),
    val enlace: String = LicenciaReglas.ENLACE_MI_LICENCIA
) : RuntimeException(message)

/** 409: el plan llegó al máximo de usuarios activos. */
class LicenciaSinCupoException(
    val activos: Long?,
    val usuariosMax: Long?,
    val enlace: String,
    message: String = LicenciaReglas.textoCupo(activos, usuariosMax)
) : RuntimeException(message) {
    val code: String = LicenciaReglas.CODE_SIN_CUPO
}
