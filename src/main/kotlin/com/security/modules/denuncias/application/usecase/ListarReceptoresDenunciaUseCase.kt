package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.dto.ReceptorDenunciaResponse
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListarReceptoresDenunciaUseCase(

    private val usuarioRepository:
    UsuarioRepository
) {

    @Transactional(readOnly = true)
    fun ejecutar():
            List<ReceptorDenunciaResponse> {

        val usuarios =
            usuarioRepository
                .findAllActivosByRoles(
                    setOf(
                        "ADMINISTRADOR",
                        "SUPERVISOR"
                    )
                )

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