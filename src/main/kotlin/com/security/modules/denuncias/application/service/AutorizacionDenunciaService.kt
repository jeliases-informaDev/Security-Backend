package com.security.modules.denuncias.application.service

import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import com.security.shared.datos.entities.Usuario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException


@Service
class AutorizacionDenunciaService {

    companion object {

        const val ROL_ADMINISTRADOR =
            "ADMINISTRADOR"

        const val ROL_SUPERVISOR =
            "SUPERVISOR"
    }


    // =========================================================
    // VALIDAR ACCESO AL MÓDULO ADMINISTRATIVO
    // =========================================================

    fun validarAccesoAdministrativo(
        usuario: Usuario
    ) {

        validarUsuarioActivo(
            usuario
        )

        if (
            esAdministrador(usuario) ||
            esSupervisor(usuario)
        ) {
            return
        }

        throw ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "No tiene permisos para gestionar denuncias."
        )
    }


    // =========================================================
    // VALIDAR ACCESO A UNA DENUNCIA ESPECÍFICA
    // =========================================================

    fun validarAccesoDenuncia(
        usuario: Usuario,
        denuncia: DenunciaEntity
    ) {

        validarUsuarioActivo(
            usuario
        )


        // -----------------------------------------------------
        // ADMINISTRADOR
        // Puede gestionar cualquier denuncia.
        // -----------------------------------------------------

        if (
            esAdministrador(usuario)
        ) {
            return
        }


        // -----------------------------------------------------
        // SOLO SUPERVISORES PUEDEN CONTINUAR
        // -----------------------------------------------------

        if (
            !esSupervisor(usuario)
        ) {

            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "No tiene permisos para gestionar denuncias."
            )
        }


        // -----------------------------------------------------
        // SUPERVISOR
        // Solo puede gestionar denuncias asignadas a él.
        // -----------------------------------------------------

        val usuarioId =
            usuario.id
                ?: throw ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "No se pudo identificar al usuario autenticado."
                )


        val receptorId =
            denuncia
                .receptorUsuario
                ?.id


        /*
         * Devolvemos 404 deliberadamente.
         *
         * Así un supervisor no puede comprobar
         * si existe una denuncia perteneciente
         * a otro receptor.
         */
        if (
            receptorId == null ||
            receptorId != usuarioId
        ) {

            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No se encontró la denuncia solicitada."
            )
        }
    }


    // =========================================================
    // ROLES
    // =========================================================

    fun esAdministrador(
        usuario: Usuario
    ): Boolean {

        return tieneRol(
            usuario,
            ROL_ADMINISTRADOR
        )
    }


    fun esSupervisor(
        usuario: Usuario
    ): Boolean {

        return tieneRol(
            usuario,
            ROL_SUPERVISOR
        )
    }


    // =========================================================
    // VALIDAR ROL
    // =========================================================

    private fun tieneRol(
        usuario: Usuario,
        codigoRol: String
    ): Boolean {

        return usuario.roles.any {

            it.rol.activo &&
                    it.rol.codigo.equals(
                        codigoRol,
                        ignoreCase = true
                    )
        }
    }


    // =========================================================
    // USUARIO ACTIVO
    // =========================================================

    private fun validarUsuarioActivo(
        usuario: Usuario
    ) {

        if (
            !usuario.activo
        ) {

            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "El usuario no se encuentra habilitado."
            )
        }
    }
}