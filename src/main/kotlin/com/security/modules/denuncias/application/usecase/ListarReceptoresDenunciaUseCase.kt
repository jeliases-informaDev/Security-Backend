package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.dto.ReceptorDenunciaResponse
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.modules.denuncias.application.service.AutorizacionDenunciaService
import com.security.shared.security.mismaEmpresa
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListarReceptoresDenunciaUseCase(

    private val usuarioRepository:
    UsuarioRepository,

    private val autorizacionDenunciaService:
    AutorizacionDenunciaService
) {

    @Transactional(readOnly = true)
    /**
     * Receptores que puede elegir quien registra la denuncia: solo los de su propia empresa
     * (los clientes no deben saber unos de otros). El administrador universal ve a todos.
     */
    fun ejecutar(
        solicitante: Usuario
    ): List<ReceptorDenunciaResponse> {

        val todos =
            usuarioRepository
                .findAllActivosByRoles(
                    setOf(
                        "ADMINISTRADOR",
                        "ADMINISTRADOR_EMPRESA",
                        "SUPERVISOR"
                    )
                )

        val usuarios =
            when {
                autorizacionDenunciaService.esAdministrador(solicitante) -> todos
                else -> todos.filter { mismaEmpresa(solicitante.empresa, it.empresa) }
            }

        return usuarios
            .map {
                    usuario ->

                ReceptorDenunciaResponse(

                    id =
                        requireNotNull(
                            usuario.id
                        ),

                    usuario =
                        usuario.usuario,

                    nombreCompleto =
                        nombreUsuario(
                            usuario
                        ),

                    correo =
                        usuario.correo,

                    cargo =
                        usuario.cargo
                )
            }
            .sortedBy {
                it.nombreCompleto
                    .lowercase()
            }
    }

    private fun nombreUsuario(
        usuario: Usuario
    ): String {

        return listOfNotNull(
            usuario.nombres,
            usuario.apePat,
            usuario.apeMat
        )
            .map {
                it.trim()
            }
            .filter {
                it.isNotBlank()
            }
            .joinToString(" ")
            .takeIf {
                it.isNotBlank()
            }
            ?: usuario.usuario
    }
}