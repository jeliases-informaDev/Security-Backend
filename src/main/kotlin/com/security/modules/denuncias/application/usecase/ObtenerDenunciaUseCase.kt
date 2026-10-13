package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.dto.DenunciaArchivoResponse
import com.security.modules.denuncias.application.dto.DenunciaDetalleResponse
import com.security.modules.denuncias.application.dto.DenunciaHistorialResponse
import com.security.modules.denuncias.application.dto.DenunciaInvolucradoResponse
import com.security.modules.denuncias.application.service.AutorizacionDenunciaService
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaArchivoRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaHistorialRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaInvolucradoRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.shared.datos.entities.Usuario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException


@Service
class ObtenerDenunciaUseCase(

    private val denunciaRepository:
    DenunciaRepository,

    private val involucradoRepository:
    DenunciaInvolucradoRepository,

    private val archivoRepository:
    DenunciaArchivoRepository,

    private val historialRepository:
    DenunciaHistorialRepository,

    private val autorizacionDenunciaService:
    AutorizacionDenunciaService

) {

    @Transactional(readOnly = true)
    fun ejecutar(
        id: Int,
        usuarioResponsable: Usuario
    ): DenunciaDetalleResponse {

        // =====================================================
        // BUSCAR DENUNCIA
        // =====================================================

        val denuncia =
            denunciaRepository
                .findById(id)
                .orElseThrow {

                    ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La denuncia no existe."
                    )
                }


        // =====================================================
        // VALIDAR AUTORIZACIÓN
        // =====================================================

        autorizacionDenunciaService
            .validarAccesoDenuncia(
                usuario =
                    usuarioResponsable,

                denuncia =
                    denuncia
            )


        // =====================================================
        // INVOLUCRADOS
        // =====================================================

        val involucrados =
            involucradoRepository
                .findAllByDenuncia_IdOrderByIdAsc(
                    id
                )
                .map {

                    DenunciaInvolucradoResponse(

                        id =
                            requireNotNull(
                                it.id
                            ),

                        tipoPersona =
                            it.tipoPersona,

                        rolIncidente =
                            it.rolIncidente,

                        documento =
                            it.documento,

                        nombreRazonSocial =
                            it.nombreRazonSocial,

                        cargo =
                            it.cargo,

                        fechaCreacion =
                            it.fechaCreacion
                    )
                }


        // =====================================================
        // ARCHIVOS
        // =====================================================

        val archivos =
            archivoRepository
                .findAllByDenuncia_IdOrderByFechaCreacionAsc(
                    id
                )
                .map {

                    DenunciaArchivoResponse(

                        id =
                            requireNotNull(
                                it.id
                            ),

                        nombreOriginal =
                            it.nombreOriginal,

                        tipoContenido =
                            it.tipoContenido,

                        tamanoBytes =
                            it.tamanoBytes,

                        fechaCreacion =
                            it.fechaCreacion
                    )
                }


        // =====================================================
        // HISTORIAL
        // =====================================================

        val historial =
            historialRepository
                .findAllByDenuncia_IdOrderByFechaCreacionAsc(
                    id
                )
                .map {

                    DenunciaHistorialResponse(

                        id =
                            requireNotNull(
                                it.id
                            ),

                        estadoAnterior =
                            it.estadoAnterior,

                        estadoNuevo =
                            it.estadoNuevo,

                        comentario =
                            it.comentario,

                        usuarioId =
                            it.usuario?.id,

                        usuario =
                            nombreUsuario(
                                it.usuario
                            ),

                        fechaCreacion =
                            it.fechaCreacion
                    )
                }


        // =====================================================
        // RESPUESTA
        // =====================================================

        return DenunciaDetalleResponse(

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

            receptorUsuarioId =
                denuncia.receptorUsuario?.id,

            receptor =
                nombreUsuario(
                    denuncia.receptorUsuario
                ),

            receptorPropuesto =
                denuncia.receptorPropuesto,

            anonima =
                denuncia.anonima,


            /*
             * Aunque normalmente una denuncia anónima
             * ya tendrá estos campos en NULL,
             * aplicamos una segunda protección.
             */
            nombreDenunciante =
                if (
                    denuncia.anonima
                ) {
                    null
                } else {
                    denuncia.nombreDenunciante
                },

            documentoDenunciante =
                if (
                    denuncia.anonima
                ) {
                    null
                } else {
                    denuncia.documentoDenunciante
                },

            telefonoDenunciante =
                if (
                    denuncia.anonima
                ) {
                    null
                } else {
                    denuncia.telefonoDenunciante
                },

            correoDenunciante =
                if (
                    denuncia.anonima
                ) {
                    null
                } else {
                    denuncia.correoDenunciante
                },

            estado =
                denuncia.estado,

            involucrados =
                involucrados,

            archivos =
                archivos,

            historial =
                historial,

            fechaCreacion =
                denuncia.fechaCreacion,

            fechaActualizacion =
                denuncia.fechaActualizacion,

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