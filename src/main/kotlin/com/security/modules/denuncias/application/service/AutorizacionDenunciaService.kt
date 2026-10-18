package com.security.modules.denuncias.application.service

import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import com.security.shared.datos.entities.Usuario
import com.security.shared.security.ROL_ADMINISTRADOR_EMPRESA
import com.security.shared.security.limpiarEmpresa
import com.security.shared.security.mismaEmpresa
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Quien puede gestionar denuncias:
 *  - ADMINISTRADOR (universal, Sulotec): todas.
 *  - ADMINISTRADOR_EMPRESA: todas las de su empresa.
 *  - SUPERVISOR: solo las asignadas a el.
 * Lo ajeno responde 404 para no revelar que existe.
 */
@Service
class AutorizacionDenunciaService {

    companion object {
        const val ROL_ADMINISTRADOR = "ADMINISTRADOR"
        const val ROL_SUPERVISOR = "SUPERVISOR"
    }

    // =========================================================
    // VALIDAR ACCESO AL MÓDULO ADMINISTRATIVO
    // =========================================================

    fun validarAccesoAdministrativo(usuario: Usuario) {
        validarUsuarioActivo(usuario)
        if (esAdministrador(usuario) || esAdministradorEmpresa(usuario) || esSupervisor(usuario)) {
            return
        }
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para gestionar denuncias.")
    }

    // =========================================================
    // VALIDAR ACCESO A UNA DENUNCIA ESPECÍFICA
    // =========================================================

    fun validarAccesoDenuncia(usuario: Usuario, denuncia: DenunciaEntity) {
        validarUsuarioActivo(usuario)

        // ADMINISTRADOR: puede gestionar cualquier denuncia.
        if (esAdministrador(usuario)) {
            return
        }

        // ADMINISTRADOR_EMPRESA: cualquier denuncia de su empresa.
        if (esAdministradorEmpresa(usuario)) {
            if (mismaEmpresa(usuario.empresa, empresaDe(denuncia))) {
                return
            }
            throw noEncontrada()
        }

        // Solo supervisores pueden continuar.
        if (!esSupervisor(usuario)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para gestionar denuncias.")
        }

        // SUPERVISOR: solo las asignadas a el.
        val usuarioId = usuario.id
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No se pudo identificar al usuario autenticado.")
        val receptorId = denuncia.receptorUsuario?.id

        /*
         * Devolvemos 404 deliberadamente: asi un supervisor no puede comprobar
         * si existe una denuncia perteneciente a otro receptor.
         */
        if (receptorId == null || receptorId != usuarioId) {
            throw noEncontrada()
        }
    }

    /** La empresa de una denuncia es la de su receptor; si no tiene, la de quien la registro. */
    fun empresaDe(denuncia: DenunciaEntity): String? =
        limpiarEmpresa(denuncia.receptorUsuario?.empresa) ?: limpiarEmpresa(denuncia.creadoPorUsuario?.empresa)

    // =========================================================
    // ROLES
    // =========================================================

    fun esAdministrador(usuario: Usuario): Boolean = tieneRol(usuario, ROL_ADMINISTRADOR)

    fun esAdministradorEmpresa(usuario: Usuario): Boolean =
        tieneRol(usuario, ROL_ADMINISTRADOR_EMPRESA) && limpiarEmpresa(usuario.empresa) != null

    fun esSupervisor(usuario: Usuario): Boolean = tieneRol(usuario, ROL_SUPERVISOR)

    private fun tieneRol(usuario: Usuario, codigoRol: String): Boolean {
        return usuario.roles.any { it.rol.activo && it.rol.codigo.equals(codigoRol, ignoreCase = true) }
    }

    private fun validarUsuarioActivo(usuario: Usuario) {
        if (!usuario.activo) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no se encuentra habilitado.")
        }
    }

    private fun noEncontrada() = ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la denuncia solicitada.")
}
