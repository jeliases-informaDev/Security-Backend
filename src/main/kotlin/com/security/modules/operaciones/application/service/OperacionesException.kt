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
    const val SOLO_ADMINISTRADOR = "OPERACIONES_SOLO_ADMINISTRADOR"
    const val ANALISIS_CERRADO = "OPERACIONES_ANALISIS_CERRADO"
    const val SIN_ANALISIS = "OPERACIONES_SIN_ANALISIS"
    const val SIN_ROS = "OPERACIONES_SIN_ROS"
    const val ROS_YA_REGISTRADO = "OPERACIONES_ROS_YA_REGISTRADO"
    const val FORMATO_PRONTO = "OPERACIONES_FORMATO_PRONTO"
    const val SIN_REGISTROS = "OPERACIONES_SIN_REGISTROS"
    const val CONSTANCIA_YA_REGISTRADA = "OPERACIONES_CONSTANCIA_YA_REGISTRADA"
    const val EXPORTACION_NO_ENCONTRADA = "OPERACIONES_EXPORTACION_NO_ENCONTRADA"
    const val ADJUNTO_NO_ENCONTRADO = "OPERACIONES_ADJUNTO_NO_ENCONTRADO"

    fun invalido(campo: String, mensaje: String) =
        OperacionesException(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, mensaje, mapOf(campo to mensaje))

    fun conflicto(codigo: String, mensaje: String) = OperacionesException(HttpStatus.CONFLICT, codigo, mensaje)

    fun noEncontrada(id: Long) = ResourceNotFoundException("No existe un registro de operación con ID $id", NO_ENCONTRADA)
}

/** Estado de un registro: INUSUAL (en analisis o sospechosa), si no ENVIADO (con constancia), si no PENDIENTE. */
const val ESTADO_PENDIENTE = "PENDIENTE"
const val ESTADO_ENVIADO = "ENVIADO"
const val ESTADO_INUSUAL = "INUSUAL"

fun estadoRegistro(estadoAnalisis: String?, fechaEnvio: java.time.LocalDate?): String = when {
    estadoAnalisis == "EN_ANALISIS" || estadoAnalisis == "SOSPECHOSA" -> ESTADO_INUSUAL
    fechaEnvio != null -> ESTADO_ENVIADO
    else -> ESTADO_PENDIENTE
}

/** Las fechas y horas que pone el servidor son de Lima, aunque el servidor corra en otra zona. */
val ZONA_LIMA: ZoneId = ZoneId.of("America/Lima")

/** «Nombres ApePat ApeMat»; si no hay nombres, el usuario. */
fun nombreCompleto(u: Usuario): String =
    listOfNotNull(u.nombres, u.apePat, u.apeMat)
        .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ").ifBlank { u.usuario }
