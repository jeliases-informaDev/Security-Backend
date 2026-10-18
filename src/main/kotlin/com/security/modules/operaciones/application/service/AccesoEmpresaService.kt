package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.esAdministrador
import com.tuempresa.operaciones.dto.RegistroDto
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Decide a que empresa puede acceder quien hace el pedido. La regla: la empresa sale de la cuenta
 * autenticada (el token), nunca de lo que mande la web o la app. Una cuenta comun solo ve y toca lo de
 * su propia empresa; un ADMINISTRADOR puede indicar cualquiera. Lo ajeno responde "no encontrado",
 * para no revelar que existe.
 */
@Service
class AccesoEmpresaService(
    private val usuarioRepository: UsuarioRepository,
    private val operacionRepository: OperacionRepository
) {

    data class Actor(val id: Int, val usuario: String, val empresa: String?, val esAdministrador: Boolean)

    @Transactional(readOnly = true)
    fun actor(): Actor {
        val autenticacion = SecurityContextHolder.getContext().authentication
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación")
        val cuenta = usuarioRepository.findByUsuario(autenticacion.name)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no disponible")
        if (!cuenta.activo) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario inactivo")
        }
        return Actor(
            id = requireNotNull(cuenta.id),
            usuario = cuenta.usuario,
            empresa = limpiar(cuenta.empresa),
            esAdministrador = autenticacion.esAdministrador()
        )
    }

    /** Empresa sobre la que se trabaja: la propia; un administrador puede pedir otra. */
    @Transactional(readOnly = true)
    fun empresaPermitida(solicitada: String?): String {
        val actor = actor()
        if (actor.esAdministrador) {
            return limpiar(solicitada) ?: actor.empresa
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la empresa")
        }
        return actor.empresa ?: throw SIN_EMPRESA()
    }

    /** Cuenta que se puede consultar: la propia; un administrador puede pedir otra. */
    @Transactional(readOnly = true)
    fun usuarioPermitido(solicitado: String?): String {
        val actor = actor()
        return if (actor.esAdministrador) limpiar(solicitado) ?: actor.usuario else actor.usuario
    }

    /** Un registro nuevo queda a nombre de quien lo crea y en su empresa (un administrador puede crearlo a nombre de otra cuenta). */
    @Transactional(readOnly = true)
    fun registroDelActor(registro: RegistroDto): RegistroDto {
        val actor = actor()
        if (actor.esAdministrador) return registro
        val empresa = actor.empresa ?: throw SIN_EMPRESA()
        return registro.copy(usuarioId = actor.id.toString(), usuario = actor.usuario, empresa = empresa)
    }

    /**
     * Antes de leer, exportar, editar o reasignar un registro: debe ser de la empresa de quien pide.
     * Si no existe no hace nada (el caso de uso devuelve su propio 404); si es ajeno responde 404.
     */
    @Transactional(readOnly = true)
    fun comprobarRegistro(id: Long) {
        val actor = actor()
        if (actor.esAdministrador) return
        val empresa = actor.empresa ?: throw SIN_EMPRESA()
        val registro = operacionRepository.findById(id).orElse(null) ?: return
        // La empresa de un registro es la de la cuenta dueña (igual que la lista); si esa cuenta ya no existe, la guardada.
        val empresaRegistro = usuarioRepository.findById(registro.idUsuario).orElse(null)
            ?.let { limpiar(it.empresa) } ?: registro.empresa
        if (!empresaRegistro.trim().equals(empresa, ignoreCase = true)) {
            throw ResourceNotFoundException("No existe un registro de operación con ID $id")
        }
    }

    private fun limpiar(valor: String?): String? = valor?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        @Suppress("FunctionName")
        private fun SIN_EMPRESA() = ResponseStatusException(
            HttpStatus.FORBIDDEN, "Tu usuario no tiene una empresa asignada: pídele a un administrador que la configure"
        )
    }
}
