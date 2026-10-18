package com.security.modules.usuarios.application.service

import com.security.modules.auth.application.service.NotificationService
import com.security.modules.auth.application.service.PasswordResetService
import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.usuarios.dto.CargaMasivaRequest
import com.security.modules.usuarios.dto.CargaMasivaResponse
import com.security.modules.usuarios.dto.CuentaMasivaRequest
import com.security.modules.usuarios.dto.FilaCargaMasivaResponse
import com.security.modules.usuarios.dto.InvitacionResponse
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.usuarioConSigla
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.LocalDateTime

/**
 * Alta de cuentas por lotes (desde un Excel o CSV) para una empresa, e invitaciones por correo.
 *
 * - Primero se revisa todo (correo valido y no repetido, rol existente) y se devuelve el resultado fila por fila.
 * - Se crea todo o nada: si alguna fila tiene error, no se crea ninguna cuenta.
 * - El usuario interno se arma solo: `sigla.parte-local-del-correo` (unico entre empresas); la persona entra con su correo.
 * - Si hay correo de salida configurado, cada cuenta recibe un enlace para elegir su clave; si no, se devuelve una
 *   clave temporal para entregarla a mano.
 */
@Service
class CargaMasivaService(
    private val usuarioRepository: UsuarioRepository,
    private val rolesRepository: RolesRepository,
    private val usuarioRolRepository: UsuarioRolRepository,
    private val passwordEncoder: PasswordEncoder,
    private val empresaService: EmpresaService,
    private val passwordResetService: PasswordResetService,
    private val notificationService: NotificationService
) {

    private val log = LoggerFactory.getLogger(CargaMasivaService::class.java)

    private class FilaPreparada(val fila: Int, val datos: CuentaMasivaRequest, val correo: String, val nombreCompleto: String,
                                var usuario: String?, var error: String?)

    @Transactional
    fun cargar(request: CargaMasivaRequest): CargaMasivaResponse {
        val empresa = empresaService.resolverEmpresaActiva(request.empresa)
        val sigla = empresa.sigla
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "La empresa ${empresa.nombre} no tiene sigla; vuelve a desplegar o corrígela en Empresas")
        val roles = rolesRepository.findAll().filter { it.activo }.associateBy { it.codigo.trim().uppercase() }

        val correosDelLote = mutableSetOf<String>()
        val usuariosDelLote = mutableSetOf<String>()
        val preparadas = request.cuentas.mapIndexed { indice, c ->
            val correo = c.correo.trim().lowercase()
            val nombreCompleto = listOf(c.nombres, c.apePat, c.apeMat.orEmpty()).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            val fila = FilaPreparada(indice + 1, c, correo, nombreCompleto, null, null)
            fila.error = when {
                c.nombres.isBlank() -> "Faltan los nombres"
                c.apePat.isBlank() -> "Falta el apellido paterno"
                !CORREO.matches(correo) -> "El correo no es válido"
                !correosDelLote.add(correo) -> "El correo se repite en el archivo"
                usuarioRepository.existsByCorreo(correo) -> "Ya existe una cuenta con ese correo"
                roles[c.rolCodigo.trim().uppercase()] == null -> "El rol '${c.rolCodigo}' no existe"
                else -> null
            }
            if (fila.error == null) {
                fila.usuario = usuarioLibre(sigla, correo, usuariosDelLote)
                usuariosDelLote.add(fila.usuario!!)
            }
            fila
        }

        val conError = preparadas.count { it.error != null }
        val porCorreo = notificationService.invitacionesPorCorreo()
        if (request.soloValidar || conError > 0) {
            return CargaMasivaResponse(
                empresa = empresa.nombre, sigla = sigla, soloValidar = request.soloValidar,
                total = preparadas.size, listas = preparadas.size - conError, creadas = 0, conError = conError,
                invitacionesPorCorreo = porCorreo,
                filas = preparadas.map {
                    FilaCargaMasivaResponse(it.fila, it.correo, it.nombreCompleto, if (it.error == null) "ok" else "error", it.error, it.usuario)
                }
            )
        }

        val ahora = LocalDateTime.now()
        val filas = preparadas.map { p ->
            val rol = roles.getValue(p.datos.rolCodigo.trim().uppercase())
            val clave = claveAleatoria()
            val cuenta = usuarioRepository.save(
                Usuario(
                    usuario = p.usuario!!, clave = passwordEncoder.encode(clave)!!, correo = p.correo,
                    nombres = p.datos.nombres.trim(), apePat = p.datos.apePat.trim(), apeMat = p.datos.apeMat?.trim()?.takeIf { it.isNotEmpty() },
                    cargo = p.datos.cargo?.trim()?.takeIf { it.isNotEmpty() }, empresa = empresa.nombre,
                    activo = true, fechaCreacion = ahora, fechaActualizacion = ahora
                )
            )
            usuarioRolRepository.save(UsuarioRol(usuario = cuenta, rol = rol))
            val invitada = porCorreo && invitar(cuenta, empresa.nombre)
            FilaCargaMasivaResponse(
                fila = p.fila, correo = p.correo, nombreCompleto = p.nombreCompleto, estado = "creada",
                mensaje = if (invitada) "Se le envió un enlace para crear su clave" else "Entrégale la clave temporal",
                usuario = cuenta.usuario, invitacionEnviada = invitada, claveTemporal = if (invitada) null else clave
            )
        }
        return CargaMasivaResponse(
            empresa = empresa.nombre, sigla = sigla, soloValidar = false,
            total = filas.size, listas = 0, creadas = filas.size, conError = 0,
            invitacionesPorCorreo = porCorreo, filas = filas
        )
    }

    /** Vuelve a mandar (o manda por primera vez) el enlace para elegir la clave. */
    @Transactional
    fun invitar(usuarioId: Int): InvitacionResponse {
        val cuenta = usuarioRepository.findById(usuarioId).orElseThrow { ResourceNotFoundException("Usuario no encontrado") }
        if (!cuenta.activo) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta está desactivada")
        }
        if (!notificationService.invitacionesPorCorreo()) {
            return InvitacionResponse(false, "El correo de salida no está configurado en el servidor: entrega la clave a mano o usa \"Olvidé mi contraseña\" cuando esté configurado.")
        }
        return if (invitar(cuenta, cuenta.empresa ?: "")) {
            InvitacionResponse(true, "Se envió a ${cuenta.correo} un enlace para crear su clave (vale $HORAS_INVITACION horas).")
        } else {
            InvitacionResponse(false, "No se pudo enviar el correo a ${cuenta.correo}. Revisa el registro del servidor.")
        }
    }

    private fun invitar(cuenta: Usuario, empresa: String): Boolean {
        return try {
            val token = passwordResetService.crearTokenInvitacion(cuenta, HORAS_INVITACION)
            notificationService.enviarInvitacion(cuenta.correo, cuenta.nombres ?: cuenta.usuario, empresa, cuenta.usuario, token, HORAS_INVITACION)
            true
        } catch (e: Exception) {
            log.warn("No se pudo enviar la invitación a {}: {}", cuenta.correo, e.message)
            false
        }
    }

    /** `sigla.parte-local-del-correo`, sin chocar con cuentas existentes ni con otras del mismo lote. */
    private fun usuarioLibre(sigla: String, correo: String, enLote: Set<String>): String {
        val local = correo.substringBefore('@').replace(Regex("[^a-z0-9._-]"), "").ifBlank { "usuario" }
        val base = usuarioConSigla(sigla, local)
        var candidato = base
        var n = 2
        while (candidato in enLote || usuarioRepository.existsByUsuario(candidato)) {
            candidato = "$base$n"
            n++
        }
        return candidato
    }

    private fun claveAleatoria(): String {
        val aleatorio = SecureRandom()
        while (true) {
            val clave = (1..12).map { ALFABETO[aleatorio.nextInt(ALFABETO.length)] }.joinToString("")
            if (clave.any { it.isLowerCase() } && clave.any { it.isUpperCase() } && clave.any { it.isDigit() }) return clave
        }
    }

    companion object {
        const val HORAS_INVITACION = 72L
        private val CORREO = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        // Sin caracteres que se confunden (0/O, 1/l/I)
        private const val ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    }
}
