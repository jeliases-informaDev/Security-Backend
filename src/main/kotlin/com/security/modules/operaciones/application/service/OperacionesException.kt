package com.security.modules.operaciones.application.service

import com.security.shared.datos.entities.Usuario
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneId

/**
 * Error de negocio del Registro de Operaciones con su codigo (contrato: CONTRATO-OPERACIONES.md y docs/REGISTRO-OPERACIONES.md).
 * Hereda de ResponseStatusException para que lo que ya lo esperaba siga funcionando; GlobalExceptionHandler agrega `code` y `campos`.
 */
class OperacionesException(
    status: HttpStatus,
    val code: String,
    mensaje: String,
    val campos: Map<String, String>? = null
) : ResponseStatusException(status, mensaje)

object CodigosOperaciones {
    const val DATOS_INVALIDOS = "OPERACIONES_DATOS_INVALIDOS"
    const val MOTIVO_INVALIDO = "OPERACIONES_MOTIVO_INVALIDO"
    const val SIN_CAMBIOS = "OPERACIONES_SIN_CAMBIOS"
    const val RESPONSABLE_INVALIDO = "OPERACIONES_RESPONSABLE_INVALIDO"
    const val FILTRO_INVALIDO = "OPERACIONES_FILTRO_INVALIDO"
    const val FILTRO_PRONTO = "OPERACIONES_FILTRO_PRONTO"
    const val PERIODO_INVALIDO = "OPERACIONES_PERIODO_INVALIDO"
    const val PAGINA_INVALIDA = "OPERACIONES_PAGINA_INVALIDA"
    const val NO_ENCONTRADA = "OPERACIONES_NO_ENCONTRADA"

    fun noEncontrada(id: Long) = ResourceNotFoundException("No existe un registro de operación con ID $id", NO_ENCONTRADA)
}

/** Estado de un registro: en la entrega A siempre pendiente de envio (Enviado e Inusual llegan despues). */
const val ESTADO_PENDIENTE = "PENDIENTE"

/** Las fechas y horas que pone el servidor son de Lima, aunque el servidor corra en otra zona. */
val ZONA_LIMA: ZoneId = ZoneId.of("America/Lima")

/** «Nombres ApePat ApeMat»; si no hay nombres, el usuario. */
fun nombreCompleto(u: Usuario): String =
    listOfNotNull(u.nombres, u.apePat, u.apeMat)
        .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ").ifBlank { u.usuario }
