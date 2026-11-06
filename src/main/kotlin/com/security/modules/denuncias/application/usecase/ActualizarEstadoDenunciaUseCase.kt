package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.dto.ActualizarEstadoDenunciaRequest
import com.security.modules.denuncias.application.dto.DenunciaDetalleResponse
import com.security.modules.denuncias.application.service.AutorizacionDenunciaService
import com.security.modules.denuncias.application.service.CodigosDenuncia
import com.security.modules.denuncias.application.service.DenunciaException
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaHistorialEntity
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaHistorialRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.shared.datos.entities.Usuario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime


@Service
class ActualizarEstadoDenunciaUseCase(

    private val denunciaRepository:
    DenunciaRepository,

    private val historialRepository:
    DenunciaHistorialRepository,

    private val obtenerDenunciaUseCase:
    ObtenerDenunciaUseCase,

    private val autorizacionDenunciaService:
    AutorizacionDenunciaService

) {

    @Transactional
    fun ejecutar(
        id: Int,
        request: ActualizarEstadoDenunciaRequest,
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
        // NUEVO ESTADO
        // =====================================================

        val nuevoEstado =
            request.estado
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El nuevo estado es obligatorio."
                )


        val estadoAnterior =
            denuncia.estado


        // =====================================================
        // VALIDAR CAMBIO DE ESTADO
        // =====================================================

        if (
            estadoAnterior ==
            nuevoEstado
        ) {

            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "La denuncia ya se encuentra en el estado $nuevoEstado."
            )
        }


        if (
            !transicionPermitida(
                actual =
                    estadoAnterior,

                nuevo =
                    nuevoEstado
            )
        ) {

            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "No se puede cambiar la denuncia de $estadoAnterior a $nuevoEstado."
            )
        }


        // =====================================================
        // RESULTADO DEL CIERRE (docs/DENUNCIAS-CIERRE.md)
        // =====================================================

        val motivo = request.motivoCierre?.trim()?.takeIf { it.isNotEmpty() }
        if (nuevoEstado != EstadoDenuncia.CERRADO && (request.resultadoCierre != null || motivo != null)) {
            throw DenunciaException(HttpStatus.BAD_REQUEST, CodigosDenuncia.RESULTADO_SOLO_AL_CERRAR,
                "El resultado y el motivo del cierre solo se indican al cerrar la denuncia.")
        }
        if (nuevoEstado == EstadoDenuncia.CERRADO) {
            val resultado = request.resultadoCierre
                ?: throw DenunciaException(HttpStatus.BAD_REQUEST, CodigosDenuncia.RESULTADO_OBLIGATORIO,
                    "Para cerrar la denuncia indica el resultado: Fundada, Infundada o Archivada.")
            if (resultado.pideMotivo && motivo == null) {
                throw DenunciaException(HttpStatus.BAD_REQUEST, CodigosDenuncia.MOTIVO_OBLIGATORIO,
                    "Para archivar la denuncia escribe el motivo.")
            }
            if (motivo != null && motivo.length > 1000) {
                throw DenunciaException(HttpStatus.BAD_REQUEST, CodigosDenuncia.MOTIVO_LARGO,
                    "El motivo del cierre puede tener hasta 1000 caracteres.")
            }
            denuncia.resultadoCierre = resultado
            denuncia.motivoCierre = motivo
        }


        // =====================================================
        // ACTUALIZAR
        // =====================================================

        val ahora =
            LocalDateTime.now()


        denuncia.estado =
            nuevoEstado

        denuncia.fechaActualizacion =
            ahora


        if (
            nuevoEstado ==
            EstadoDenuncia.CERRADO
        ) {

            denuncia.fechaCierre =
                ahora
        }


        denunciaRepository.save(
            denuncia
        )


        // =====================================================
        // HISTORIAL
        // =====================================================

        historialRepository.save(

            DenunciaHistorialEntity(

                denuncia =
                    denuncia,

                estadoAnterior =
                    estadoAnterior,

                estadoNuevo =
                    nuevoEstado,

                comentario =
                    listOfNotNull(
                        request.comentario?.trim()?.takeIf { it.isNotBlank() },
                        denuncia.resultadoCierre?.takeIf { nuevoEstado == EstadoDenuncia.CERRADO }
                            ?.let { "Resultado del cierre: ${it.etiqueta}" + (denuncia.motivoCierre?.let { m -> ". Motivo: $m" } ?: "") }
                    ).joinToString(" · ").takeIf { it.isNotBlank() },

                usuario =
                    usuarioResponsable,

                fechaCreacion =
                    ahora
            )
        )


        // =====================================================
        // DEVOLVER DETALLE ACTUALIZADO
        // =====================================================

        return obtenerDenunciaUseCase
            .ejecutar(
                id =
                    id,

                usuarioResponsable =
                    usuarioResponsable
            )
    }


    // =========================================================
    // TRANSICIONES
    // =========================================================

    private fun transicionPermitida(
        actual: EstadoDenuncia,
        nuevo: EstadoDenuncia
    ): Boolean {

        return when (
            actual
        ) {

            EstadoDenuncia.RECIBIDO ->
                nuevo ==
                        EstadoDenuncia.EN_PROCESO

            EstadoDenuncia.EN_PROCESO ->
                nuevo ==
                        EstadoDenuncia.CERRADO

            EstadoDenuncia.CERRADO ->
                false
        }
    }
}