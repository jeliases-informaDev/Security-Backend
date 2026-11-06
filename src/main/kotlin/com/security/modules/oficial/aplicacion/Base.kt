package com.security.modules.oficial.aplicacion

import com.fasterxml.jackson.annotation.JsonInclude
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Error del modulo con su codigo. El mensaje nunca lleva documento, correo, telefono ni codigos secretos. */
class OficialException(
    val status: HttpStatus,
    val code: String,
    message: String,
    val campos: Map<String, String>? = null
) : RuntimeException(message)

object Oc {
    const val DATOS_INVALIDOS = "OC_DATOS_INVALIDOS"
    const val SIN_CAMBIOS = "OC_SIN_CAMBIOS"
    const val FUNCION_PRONTO = "OC_FUNCION_PRONTO"
    const val ADJUNTO_INVALIDO = "OC_ADJUNTO_INVALIDO"
    const val EMPRESA_REQUERIDA = "OC_EMPRESA_REQUERIDA"
    const val SIN_EMPRESA = "OC_SIN_EMPRESA"
    const val SIN_PERMISO = "OC_SIN_PERMISO"
    const val NO_ENCONTRADO = "OC_NO_ENCONTRADO"
    const val TITULAR_ACTIVO = "OC_TITULAR_ACTIVO"
    const val ESTADO_INVALIDO = "OC_ESTADO_INVALIDO"
    const val CIFRADO_NO_CONFIGURADO = "OC_CIFRADO_NO_CONFIGURADO"
    const val ADJUNTO_EN_USO = "OC_ADJUNTO_EN_USO"
    const val YA_COMUNICADO = "OC_YA_COMUNICADO"
    const val FERIADO_DUPLICADO = "OC_FERIADO_DUPLICADO"

    fun datos(campos: Map<String, String>) =
        OficialException(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, campos.values.firstOrNull() ?: "Datos inválidos", campos)

    fun dato(campo: String, mensaje: String) = datos(mapOf(campo to mensaje))

    fun noEncontrado(mensaje: String = "No se encontró lo que buscas.") =
        OficialException(HttpStatus.NOT_FOUND, NO_ENCONTRADO, mensaje)

    fun sinPermiso(mensaje: String = "Tu rol no puede hacer esta acción: pídeselo al administrador de tu empresa.") =
        OficialException(HttpStatus.FORBIDDEN, SIN_PERMISO, mensaje)

    fun estado(mensaje: String) = OficialException(HttpStatus.CONFLICT, ESTADO_INVALIDO, mensaje)

    fun pronto(mensaje: String) = OficialException(HttpStatus.BAD_REQUEST, FUNCION_PRONTO, mensaje)
}

/** Respuesta de error: el formato comun del proyecto mas `campos` en los errores de datos. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OcErrorResponse(
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val code: String? = null,
    val campos: Map<String, String>? = null
)

/** Junta los errores de datos de un pedido para devolverlos todos juntos. */
class Validador {
    val campos = linkedMapOf<String, String>()

    fun error(campo: String, mensaje: String) {
        campos.putIfAbsent(campo, mensaje)
    }

    fun texto(campo: String, valor: String?, max: Int, obligatorio: Boolean, faltaMensaje: String): String? {
        val limpio = valor?.trim()?.takeIf { it.isNotEmpty() }
        if (limpio == null) {
            if (obligatorio) error(campo, faltaMensaje)
            return null
        }
        if (limpio.length > max) {
            error(campo, "Usa como máximo $max caracteres.")
            return null
        }
        return limpio
    }

    fun fechaNoFutura(campo: String, valor: LocalDate?, hoy: LocalDate, obligatorio: Boolean, faltaMensaje: String): LocalDate? {
        if (valor == null) {
            if (obligatorio) error(campo, faltaMensaje)
            return null
        }
        if (valor.isAfter(hoy)) {
            error(campo, "La fecha no puede ser futura.")
            return null
        }
        return valor
    }

    fun lanzarSiHay() {
        if (campos.isNotEmpty()) throw Oc.datos(campos)
    }
}

/** Hora de Lima. Se puede fijar en las pruebas. */
@Component
class RelojOficial {
    var clock: Clock = Clock.system(ZONA)

    fun hoy(): LocalDate = LocalDate.now(clock)
    fun ahora(): LocalDateTime = LocalDateTime.now(clock).withNano(0)

    companion object {
        val ZONA: ZoneId = ZoneId.of("America/Lima")
    }
}

data class ActorOc(
    val id: Int?,
    val usuario: String,
    val nombreCompleto: String,
    val esUniversal: Boolean,
    val puedeEscribir: Boolean
)

data class EmpresaOc(val id: Int, val uuid: String?, val nombre: String)

/** Quien pide y sobre que empresa trabaja. */
data class ContextoOc(val actor: ActorOc, val empresa: EmpresaOc) {
    val empresaId: Int get() = empresa.id

    fun exigirEscritura() {
        if (!actor.puedeEscribir) throw Oc.sinPermiso()
    }

    fun exigirUniversal() {
        if (!actor.esUniversal) throw Oc.sinPermiso("Solo el administrador de Sulotec puede cambiar esto.")
    }
}

const val ROL_ADMIN_UNIVERSAL = "ADMINISTRADOR"
const val ROL_ADMIN_EMPRESA = "ADMINISTRADOR_EMPRESA"

fun nombreCompletoDe(usuario: Usuario): String =
    listOfNotNull(usuario.nombres, usuario.apePat, usuario.apeMat)
        .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        .ifEmpty { usuario.usuario }

/**
 * Decide la empresa y los permisos. La empresa sale de la cuenta autenticada (token → usuarios.empresa → empresas);
 * solo el administrador universal puede pedir otra con ?empresa= (uuid o nombre). El rol sale del token.
 */
@Service
class AccesoOficialService(
    private val usuarioRepository: UsuarioRepository,
    private val empresaRepository: EmpresaRepository
) {

    @Transactional(readOnly = true)
    fun contexto(empresaSolicitada: String?): ContextoOc {
        val autenticacion = SecurityContextHolder.getContext().authentication
            ?: throw OficialException(HttpStatus.UNAUTHORIZED, "OC_SIN_SESION", "Se requiere autenticación")
        val cuenta = usuarioRepository.findByUsuario(autenticacion.name)
            ?: throw OficialException(HttpStatus.FORBIDDEN, Oc.SIN_PERMISO, "Usuario no disponible")
        if (!cuenta.activo) throw OficialException(HttpStatus.FORBIDDEN, Oc.SIN_PERMISO, "Usuario inactivo")

        val esUniversal = tieneRol(autenticacion, ROL_ADMIN_UNIVERSAL)
        val actor = ActorOc(
            id = cuenta.id,
            usuario = cuenta.usuario,
            nombreCompleto = nombreCompletoDe(cuenta),
            esUniversal = esUniversal,
            puedeEscribir = esUniversal || tieneRol(autenticacion, ROL_ADMIN_EMPRESA)
        )

        val pedida = empresaSolicitada?.trim()?.takeIf { it.isNotEmpty() }
        val empresa = if (esUniversal && pedida != null) {
            empresaRepository.findByUuid(pedida) ?: empresaRepository.findByNombreIgnoreCase(pedida)
                ?: throw Oc.noEncontrado("No existe esa empresa.")
        } else {
            val propia = cuenta.empresa?.trim()?.takeIf { it.isNotEmpty() }
            if (propia == null) {
                if (esUniversal) {
                    throw OficialException(HttpStatus.BAD_REQUEST, Oc.EMPRESA_REQUERIDA, "Indica la empresa.")
                }
                throw sinEmpresa()
            }
            empresaRepository.findByNombreIgnoreCase(propia) ?: throw sinEmpresa()
        }
        return ContextoOc(actor, EmpresaOc(requireNotNull(empresa.id), empresa.uuid, empresa.nombre))
    }

    private fun tieneRol(autenticacion: Authentication, rol: String) =
        autenticacion.authorities.any { it.authority == "ROLE_$rol" }

    private fun sinEmpresa() = OficialException(
        HttpStatus.FORBIDDEN, Oc.SIN_EMPRESA,
        "Tu usuario no tiene una empresa asignada: pídele a un administrador que la configure"
    )
}
