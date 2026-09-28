package com.security.modules.matrices.application.service

import com.security.modules.matrices.application.dto.MatrizRiesgoDetalleResponse
import com.security.modules.matrices.application.dto.MatrizRiesgoResumenResponse
import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAnalisisEntity
import com.security.modules.matrices.infrastructure.persistence.repository.MatrizRiesgoAnalisisRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MatrizRiesgoConsultaService(
    private val usuarioRepository: UsuarioRepository,
    private val analisisRepository: MatrizRiesgoAnalisisRepository
) {

    @Transactional(readOnly = true)
    fun listar(
        username: String
    ): List<MatrizRiesgoResumenResponse> {

        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException(
                "Usuario no encontrado"
            )

        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException(
                "Usuario inválido"
            )

        return analisisRepository
            .findAllByUsuario_IdOrderByFechaCreacionDesc(
                usuarioId
            )
            .map(::toResumen)
    }

    @Transactional(readOnly = true)
    fun obtenerDetalle(
        username: String,
        id: Int
    ): MatrizRiesgoDetalleResponse {

        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException(
                "Usuario no encontrado"
            )

        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException(
                "Usuario inválido"
            )

        val analisis =
            analisisRepository.findByIdAndUsuario_Id(
                id,
                usuarioId
            )
                ?: throw ResourceNotFoundException(
                    "El análisis de riesgo no fue encontrado"
                )

        return toDetalle(analisis)
    }

    private fun toResumen(
        analisis: MatrizRiesgoAnalisisEntity
    ): MatrizRiesgoResumenResponse {

        return MatrizRiesgoResumenResponse(
            id = analisis.id!!,
            titulo = analisis.titulo,
            area = analisis.area?.nombre,
            proceso = analisis.proceso?.nombre,
            riesgoInherente = analisis.riesgoInherente,
            riesgoResidual = analisis.riesgoResidual,
            estado = analisis.estado,
            fechaCreacion = analisis.fechaCreacion,
            fechaCierre = analisis.fechaCierre
        )
    }

    private fun toDetalle(
        analisis: MatrizRiesgoAnalisisEntity
    ): MatrizRiesgoDetalleResponse {

        return MatrizRiesgoDetalleResponse(

            id = analisis.id!!,

            tipoEmpresa = analisis.tipoEmpresa,
            titulo = analisis.titulo,

            areaId = analisis.area?.id,
            area = analisis.area?.nombre,

            procesoId = analisis.proceso?.id,
            proceso = analisis.proceso?.nombre,

            detalleRiesgo = analisis.detalleRiesgo,
            factor = analisis.factor,

            probabilidad = analisis.probabilidadNivel,
            impactoEstimado = analisis.impactoEstimado,
            impactoInherente = analisis.impactoNivel,
            riesgoInherente = analisis.riesgoInherente,

            controlDescripcion = analisis.controlDescripcion,
            controlDocumento = analisis.controlDocumento,

            controlAreaId = analisis.controlArea?.id,
            controlArea = analisis.controlArea?.nombre,

            periodicidad = analisis.controlPeriodicidad,
            operatividad = analisis.controlOperatividad,
            tipoControl = analisis.controlTipo,
            supervision = analisis.controlSupervision,

            frecuenciaOportuna =
                analisis.controlFrecuenciaOportuna,

            seguimientoAdecuado =
                analisis.controlSeguimientoAdecuado,

            mitigacion = analisis.mitigacion,
            probabilidadResidual =
                analisis.probabilidadResidual,

            impactoResidual =
                analisis.impactoResidual,

            riesgoResidual =
                analisis.riesgoResidual,

            planAccion = analisis.planAccion,

            areaResponsableId =
                analisis.areaResponsable?.id,

            areaResponsable =
                analisis.areaResponsable?.nombre,

            fechaInicio = analisis.fechaInicio,
            fechaCierre = analisis.fechaCierre,

            estado = analisis.estado,

            fechaCreacion =
                analisis.fechaCreacion,

            fechaActualizacion =
                analisis.fechaActualizacion
        )
    }
}