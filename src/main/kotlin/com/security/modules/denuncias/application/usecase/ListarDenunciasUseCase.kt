package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.dto.DenunciaResumenResponse
import com.security.modules.denuncias.application.service.AutorizacionDenunciaService
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.shared.datos.entities.Usuario
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class ListarDenunciasUseCase(

    private val denunciaRepository:
    DenunciaRepository,

    private val autorizacionDenunciaService:
    AutorizacionDenunciaService

) {

    @Transactional(readOnly = true)
    fun ejecutar(
        estado: EstadoDenuncia? = null,
        usuarioResponsable: Usuario
    ): List<DenunciaResumenResponse> {

        // =====================================================
        // VALIDAR ACCESO ADMINISTRATIVO
        // =====================================================

        autorizacionDenunciaService
            .validarAccesoAdministrativo(
                usuarioResponsable
            )


        // =====================================================
        // ADMINISTRADOR
        // VE TODAS LAS DENUNCIAS
        // =====================================================

        val denuncias =
            if (
                autorizacionDenunciaService
                    .esAdministrador(
                        usuarioResponsable
                    )
            ) {

                obtenerParaAdministrador(
                    estado
                )

            } else if (
                autorizacionDenunciaService
                    .esAdministradorEmpresa(
                        usuarioResponsable
                    )
            ) {

                // ADMINISTRADOR DE EMPRESA: todas las de su empresa
                obtenerParaEmpresa(
                    requireNotNull(usuarioResponsable.empresa).trim(),
                    estado
                )

            } else {

                // =================================================
                // SUPERVISOR
                // SOLO VE LAS DENUNCIAS ASIGNADAS A ÉL
                // =================================================

                obtenerParaSupervisor(
                    usuarioResponsable =
                        usuarioResponsable,

                    estado =
                        estado
                )
            }


        return denuncias.map {
            toResumen(it)
        }
    }


    // =========================================================
    // ADMINISTRADOR
    // =========================================================

    private fun obtenerParaAdministrador(
        estado: EstadoDenuncia?
    ): List<DenunciaEntity> {

        return if (
            estado != null
        ) {

            denunciaRepository
                .findAllByEstadoOrderByFechaCreacionDesc(
                    estado
                )

        } else {

            denunciaRepository
                .findAllByOrderByFechaCreacionDesc()
        }
    }


    // =========================================================
    // ADMINISTRADOR DE EMPRESA
    // =========================================================

    private fun obtenerParaEmpresa(
        empresa: String,
        estado: EstadoDenuncia?
    ): List<DenunciaEntity> {

        return if (estado != null) {
            denunciaRepository.buscarPorEmpresaYEstado(empresa, estado)
        } else {
            denunciaRepository.buscarPorEmpresa(empresa)
        }
    }


    // =========================================================
    // SUPERVISOR
    // =========================================================

    private fun obtenerParaSupervisor(
        usuarioResponsable: Usuario,
        estado: EstadoDenuncia?
    ): List<DenunciaEntity> {

        val usuarioId =
            requireNotNull(
                usuarioResponsable.id
            ) {
                "El usuario autenticado no tiene identificador."
            }


        return if (
            estado != null
        ) {

            denunciaRepository
                .findAllByReceptorUsuario_IdAndEstadoOrderByFechaCreacionDesc(
                    receptorUsuarioId =
                        usuarioId,

                    estado =
                        estado
                )

        } else {

            denunciaRepository
                .findAllByReceptorUsuario_IdOrderByFechaCreacionDesc(
                    usuarioId
                )
        }
    }


    // =========================================================
    // RESPONSE
    // =========================================================

    private fun toResumen(
        denuncia: DenunciaEntity
    ): DenunciaResumenResponse {

        return DenunciaResumenResponse(

            id =
                requireNotNull(
                    denuncia.id
                ),

            causa =
                denuncia.causa,

            relacionEmpresa =
                denuncia.relacionEmpresa,

            descripcion =
                denuncia.descripcion,

            receptor =
                nombreUsuario(
                    denuncia.receptorUsuario
                )
                    ?: denuncia.receptorPropuesto,

            estado =
                denuncia.estado,

            anonima =
                denuncia.anonima,

            fechaCreacion =
                denuncia.fechaCreacion,

            fechaCierre =
                denuncia.fechaCierre
        )
    }


    // =========================================================
    // NOMBRE DE USUARIO
    // =========================================================

    private fun nombreUsuario(
        usuario: Usuario?
    ): String? {

        if (
            usuario == null
        ) {
            return null
        }


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