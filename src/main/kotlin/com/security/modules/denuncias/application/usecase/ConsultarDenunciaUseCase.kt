package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.dto.ConsultarDenunciaRequest
import com.security.modules.denuncias.application.dto.ConsultarDenunciaResponse
import com.security.modules.denuncias.application.dto.SeguimientoDenunciaHistorialResponse
import com.security.modules.denuncias.application.service.SeguimientoDenunciaService
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaHistorialRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.Locale

@Service
class ConsultarDenunciaUseCase(

    private val denunciaRepository:
    DenunciaRepository,

    private val historialRepository:
    DenunciaHistorialRepository,

    private val seguimientoDenunciaService:
    SeguimientoDenunciaService
) {

    // =========================================================
    // CONSULTAR SEGUIMIENTO
    // =========================================================

    @Transactional(readOnly = true)
    fun ejecutar(
        request: ConsultarDenunciaRequest
    ): ConsultarDenunciaResponse {

        // -----------------------------------------------------
        // NORMALIZAR DATOS
        // -----------------------------------------------------

        val codigoSeguimiento =
            request.codigoSeguimiento
                .trim()
                .uppercase(Locale.ROOT)

        val pin =
            request.pin
                .trim()
                .uppercase(Locale.ROOT)

        // -----------------------------------------------------
        // BUSCAR DENUNCIA
        // -----------------------------------------------------

        val denuncia =
            denunciaRepository
                .findByCodigoSeguimiento(
                    codigoSeguimiento
                )
                ?: throw credencialesInvalidas()

        // -----------------------------------------------------
        // VALIDAR PIN
        // -----------------------------------------------------

        val pinHash =
            denuncia.pinHash
                ?: throw credencialesInvalidas()

        val pinValido =
            seguimientoDenunciaService
                .validarPin(
                    pin = pin,
                    pinHash = pinHash
                )

        if (!pinValido) {
            throw credencialesInvalidas()
        }

        // -----------------------------------------------------
        // IDENTIFICADOR INTERNO
        // -----------------------------------------------------

        val denunciaId =
            denuncia.id
                ?: throw IllegalStateException(
                    "La denuncia no tiene ID."
                )

        // -----------------------------------------------------
        // HISTORIAL
        // -----------------------------------------------------

        val historial =
            historialRepository
                .findAllByDenuncia_IdOrderByFechaCreacionAsc(
                    denunciaId
                )
                .map { registro ->

                    SeguimientoDenunciaHistorialResponse(

                        estadoAnterior =
                            registro.estadoAnterior,

                        estadoNuevo =
                            registro.estadoNuevo,

                        comentario =
                            registro.comentario,

                        fechaCreacion =
                            registro.fechaCreacion
                    )
                }

        // -----------------------------------------------------
        // RESPUESTA
        // -----------------------------------------------------

        return ConsultarDenunciaResponse(

            codigoSeguimiento =
                codigoSeguimiento,

            causa =
                denuncia.causa,

            relacionEmpresa =
                denuncia.relacionEmpresa,

            descripcion =
                denuncia.descripcion,

            estado =
                denuncia.estado,

            fechaCreacion =
                denuncia.fechaCreacion,

            fechaActualizacion =
                denuncia.fechaActualizacion,

            fechaCierre =
                denuncia.fechaCierre,

            historial =
                historial
        )
    }

    // =========================================================
    // ERROR GENÉRICO
    // =========================================================

    private fun credencialesInvalidas():
            ResponseStatusException {

        /*
         * Utilizamos el mismo mensaje si:
         *
         * - el identificador no existe
         * - el PIN es incorrecto
         * - la denuncia antigua no posee PIN
         *
         * Así evitamos revelar si un identificador
         * específico existe en el sistema.
         */
        return ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "No fue posible consultar la denuncia con los datos proporcionados."
        )
    }
}