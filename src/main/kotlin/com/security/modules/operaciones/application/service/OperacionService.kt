package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.dto.CambiarUsuarioOperacionResponse
import com.security.modules.operaciones.dto.MostrarUsuarioResponse
import com.security.modules.operaciones.dto.UsuarioEmpresaResponse
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Usuarios de la empresa, cuenta duena de un registro y usuario actual. El alta, la lista, la edicion, el responsable y
 * el historial estan en AltaOperacionService, ListaOperacionesService, EdicionOperacionService,
 * ResponsableOperacionService e HistorialOperacionService.
 */
@Service
class OperacionService(
    private val operacionRepository: OperacionRepository,
    private val usuarioRepository: UsuarioRepository
) {

    @Transactional(readOnly = true)
    fun listarUsuariosPorEmpresa(empresa: String): List<UsuarioEmpresaResponse> {
        if (empresa.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La empresa es obligatoria")
        }
        return usuarioRepository.findAllByEmpresaOrderByNombresAscApePatAscIdAsc(empresa.trim()).map {
            UsuarioEmpresaResponse(
                id = requireNotNull(it.id), usuario = it.usuario,
                nombreCompleto = nombreCompleto(it),
                empresa = requireNotNull(it.empresa), activo = it.activo
            )
        }
    }

    @Transactional
    fun cambiarUsuarioOperacion(id: Long, usuarioId: Int): CambiarUsuarioOperacionResponse {
        if (id <= 0 || usuarioId <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Los IDs deben ser positivos")
        }
        val registro = operacionRepository.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Operacion no encontrada")
        val anterior = usuarioRepository.findById(registro.idUsuario).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Usuario actual del registro no encontrado")
        val nuevo = usuarioRepository.findById(usuarioId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario seleccionado no encontrado")
        val empresa = anterior.empresa
        if (empresa.isNullOrBlank() || nuevo.empresa.isNullOrBlank() ||
            !empresa.trim().equals(nuevo.empresa?.trim(), ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario seleccionado debe pertenecer a la misma empresa")
        }
        if (!nuevo.activo) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario seleccionado esta inactivo")
        }
        if (registro.idUsuario != usuarioId &&
            operacionRepository.cambiarUsuario(id, registro.idUsuario, usuarioId) != 1) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "La operacion cambio; actualice la lista y vuelva a intentar")
        }
        return CambiarUsuarioOperacionResponse(id, usuarioId, nombreCompleto(nuevo))
    }

    fun obtenerUsuarioActual(usuario: String): MostrarUsuarioResponse {
        val usuarioEncontrado = operacionRepository.buscarPorUsuario(usuario)
            ?: throw ResourceNotFoundException("Usuario no encontrado")
        return MostrarUsuarioResponse(
            id = usuarioEncontrado.id.toString(),
            usuario = usuarioEncontrado.usuario,
            empresa = usuarioEncontrado.empresa ?: ""
        )
    }
}
