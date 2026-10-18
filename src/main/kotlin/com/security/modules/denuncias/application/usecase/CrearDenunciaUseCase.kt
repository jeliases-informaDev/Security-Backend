package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.dto.CrearDenunciaRequest
import com.security.modules.denuncias.application.dto.CrearDenunciaResponse
import com.security.modules.denuncias.application.service.SeguimientoDenunciaService
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaHistorialEntity
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaInvolucradoEntity
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaHistorialRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaInvolucradoRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.shared.datos.entities.Usuario
import com.security.modules.denuncias.application.service.AutorizacionDenunciaService
import com.security.shared.security.limpiarEmpresa
import com.security.shared.security.mismaEmpresa
import com.security.shared.datos.repositories.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@Service
class CrearDenunciaUseCase(

    private val denunciaRepository:
    DenunciaRepository,

    private val involucradoRepository:
    DenunciaInvolucradoRepository,

    private val historialRepository:
    DenunciaHistorialRepository,

    private val usuarioRepository:
    UsuarioRepository,

    private val seguimientoDenunciaService:
    SeguimientoDenunciaService,

    private val autorizacionDenunciaService:
    AutorizacionDenunciaService
) {

    @Transactional
    fun ejecutar(
        request: CrearDenunciaRequest,
        usuarioCreador: Usuario? = null
    ): CrearDenunciaResponse {

        // =====================================================
        // VALIDAR RECEPTOR
        // =====================================================

        validarReceptor(request)

        val receptor =
            obtenerReceptor(
                request.receptorUsuarioId
            )

        validarReceptorDeMiEmpresa(
            receptor,
            usuarioCreador
        )

        // =====================================================
        // GENERAR DATOS DE SEGUIMIENTO
        // =====================================================

        val codigoSeguimiento =
            generarCodigoSeguimientoUnico()

        /*
         * Este PIN se devolverá al frontend
         * solamente en esta operación.
         *
         * NO se almacena directamente.
         */
        val pin =
            seguimientoDenunciaService
                .generarPin()

        val pinHash =
            seguimientoDenunciaService
                .generarHashPin(
                    pin
                )

        // =====================================================
        // CREAR DENUNCIA
        // =====================================================

        val ahora =
            LocalDateTime.now()

        /*
         * Aunque el usuario haya iniciado sesión,
         * una denuncia anónima no debe quedar
         * relacionada con su cuenta.
         */
        val usuarioAsociado =
            if (request.anonima) {
                null
            } else {
                usuarioCreador
            }

        val denuncia =
            DenunciaEntity(

                causa =
                    request.causa
                        ?: throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "La causa de la denuncia es obligatoria."
                        ),

                relacionEmpresa =
                    request.relacionEmpresa
                        ?: throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "La relación con la empresa es obligatoria."
                        ),

                descripcion =
                    request.descripcion
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "La descripción de la denuncia es obligatoria."
                        ),

                // ---------------------------------------------
                // SEGUIMIENTO
                // ---------------------------------------------

                codigoSeguimiento =
                    codigoSeguimiento,

                pinHash =
                    pinHash,

                // ---------------------------------------------
                // RECEPTOR
                // ---------------------------------------------

                receptorUsuario =
                    receptor,

                receptorPropuesto =
                    request.receptorPropuesto
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        },

                // ---------------------------------------------
                // DENUNCIANTE
                // ---------------------------------------------

                anonima =
                    request.anonima,

                nombreDenunciante =
                    if (request.anonima) {
                        null
                    } else {
                        request.nombreDenunciante
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                    },

                documentoDenunciante =
                    if (request.anonima) {
                        null
                    } else {
                        request.documentoDenunciante
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                    },

                telefonoDenunciante =
                    if (request.anonima) {
                        null
                    } else {
                        request.telefonoDenunciante
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                    },

                correoDenunciante =
                    if (request.anonima) {
                        null
                    } else {
                        request.correoDenunciante
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                    },

                aceptaTerminos =
                    request.aceptaTerminos,

                // ---------------------------------------------
                // ESTADO
                // ---------------------------------------------

                estado =
                    EstadoDenuncia.RECIBIDO,

                // ---------------------------------------------
                // USUARIO
                // ---------------------------------------------

                creadoPorUsuario =
                    usuarioAsociado,

                // ---------------------------------------------
                // FECHAS
                // ---------------------------------------------

                fechaCreacion =
                    ahora,

                fechaActualizacion =
                    ahora,

                fechaCierre =
                    null
            )

        val denunciaGuardada =
            denunciaRepository.save(
                denuncia
            )

        // =====================================================
        // GUARDAR INVOLUCRADOS
        // =====================================================

        request.involucrados.forEach {
                involucradoRequest ->

            val tipoPersona =
                involucradoRequest.tipoPersona
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El tipo de persona involucrada es obligatorio."
                    )

            val rolIncidente =
                involucradoRequest.rolIncidente
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El rol en el incidente es obligatorio."
                    )

            val nombreRazonSocial =
                involucradoRequest
                    .nombreRazonSocial
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El nombre o razón social es obligatorio."
                    )

            val cargo =
                involucradoRequest
                    .cargo
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El cargo es obligatorio."
                    )

            involucradoRepository.save(
                DenunciaInvolucradoEntity(

                    denuncia =
                        denunciaGuardada,

                    tipoPersona =
                        tipoPersona,

                    rolIncidente =
                        rolIncidente,

                    documento =
                        involucradoRequest
                            .documento
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            },

                    nombreRazonSocial =
                        nombreRazonSocial,

                    cargo =
                        cargo,

                    fechaCreacion =
                        ahora
                )
            )
        }

        // =====================================================
        // HISTORIAL INICIAL
        // =====================================================

        historialRepository.save(
            DenunciaHistorialEntity(

                denuncia =
                    denunciaGuardada,

                estadoAnterior =
                    null,

                estadoNuevo =
                    EstadoDenuncia.RECIBIDO,

                comentario =
                    "Denuncia registrada.",

                /*
                 * Si la denuncia es anónima,
                 * tampoco asociamos usuario
                 * en el historial inicial.
                 */
                usuario =
                    usuarioAsociado,

                fechaCreacion =
                    ahora
            )
        )

        // =====================================================
        // RESPUESTA DE CREACIÓN
        // =====================================================

        return CrearDenunciaResponse(

            id =
                requireNotNull(
                    denunciaGuardada.id
                ),

            codigoSeguimiento =
                codigoSeguimiento,

            /*
             * Este es el ÚNICO momento
             * en que devolvemos el PIN original.
             */
            pin =
                pin,

            estado =
                denunciaGuardada.estado,

            fechaCreacion =
                denunciaGuardada.fechaCreacion
        )
    }

    // =========================================================
    // GENERAR CÓDIGO ÚNICO
    // =========================================================

    private fun generarCodigoSeguimientoUnico():
            String {

        /*
         * La posibilidad de colisión es muy baja,
         * pero igualmente comprobamos la BD.
         */
        repeat(10) {

            val codigo =
                seguimientoDenunciaService
                    .generarCodigoSeguimiento()

            val existe =
                denunciaRepository
                    .existsByCodigoSeguimiento(
                        codigo
                    )

            if (!existe) {
                return codigo
            }
        }

        throw IllegalStateException(
            "No fue posible generar un código de seguimiento único."
        )
    }

    // =========================================================
    // VALIDACIONES
    // =========================================================

    private fun validarReceptor(
        request: CrearDenunciaRequest
    ) {

        val tieneReceptorSistema =
            request.receptorUsuarioId != null

        val tieneReceptorPropuesto =
            !request.receptorPropuesto
                .isNullOrBlank()

        if (
            !tieneReceptorSistema &&
            !tieneReceptorPropuesto
        ) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Debe seleccionar un receptor o proponer otro receptor."
            )
        }

        if (
            tieneReceptorSistema &&
            tieneReceptorPropuesto
        ) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Debe seleccionar un receptor o proponer otro, no ambos."
            )
        }

        if (!request.aceptaTerminos) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Debe aceptar los términos y condiciones."
            )
        }

        if (request.involucrados.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Debe registrar al menos una persona o empresa involucrada."
            )
        }
    }

    // =========================================================
    // OBTENER RECEPTOR
    // =========================================================

    /**
     * Una denuncia solo puede dirigirse a un receptor de la propia empresa de quien la registra
     * (el administrador universal puede elegir a cualquiera). Se comprueba aunque sea anonima.
     */
    private fun validarReceptorDeMiEmpresa(
        receptor: Usuario?,
        creador: Usuario?
    ) {
        if (receptor == null || creador == null) {
            return
        }
        if (autorizacionDenunciaService.esAdministrador(creador)) {
            return
        }
        if (limpiarEmpresa(creador.empresa) == null) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Tu usuario no tiene una empresa asignada: pídele a un administrador que la configure."
            )
        }
        if (!mismaEmpresa(creador.empresa, receptor.empresa)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "El receptor seleccionado no pertenece a tu empresa."
            )
        }
    }

    private fun obtenerReceptor(
        receptorUsuarioId: Int?
    ): Usuario? {

        if (receptorUsuarioId == null) {
            return null
        }

        val usuario =
            usuarioRepository
                .findById(
                    receptorUsuarioId
                )
                .orElseThrow {
                    ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El receptor seleccionado no existe."
                    )
                }

        if (!usuario.activo) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "El receptor seleccionado se encuentra inactivo."
            )
        }

        return usuario
    }
}