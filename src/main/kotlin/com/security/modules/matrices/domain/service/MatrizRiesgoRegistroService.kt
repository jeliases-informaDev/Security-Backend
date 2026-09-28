package com.security.modules.matrices.application.service

import com.security.modules.matrices.application.dto.GuardarMatrizRiesgoRequest
import com.security.modules.matrices.application.dto.MatrizRiesgoRegistroResponse
import com.security.modules.matrices.application.usecase.CalcularMatrizRiesgoUseCase
import com.security.modules.matrices.domain.enums.EstadoAnalisis
import com.security.modules.matrices.domain.enums.NivelRiesgo
import com.security.modules.matrices.domain.enums.RespuestaControl
import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAnalisisEntity
import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAreaEntity
import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoProcesoEntity
import com.security.modules.matrices.infrastructure.persistence.repository.MatrizRiesgoAnalisisRepository
import com.security.modules.matrices.infrastructure.persistence.repository.MatrizRiesgoAreaProcesoRepository
import com.security.modules.matrices.infrastructure.persistence.repository.MatrizRiesgoAreaRepository
import com.security.modules.matrices.infrastructure.persistence.repository.MatrizRiesgoProcesoRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@Service
class MatrizRiesgoRegistroService(

    private val usuarioRepository: UsuarioRepository,
    private val areaRepository: MatrizRiesgoAreaRepository,
    private val procesoRepository: MatrizRiesgoProcesoRepository,
    private val areaProcesoRepository: MatrizRiesgoAreaProcesoRepository,
    private val analisisRepository: MatrizRiesgoAnalisisRepository,
    private val calculadora: CalcularMatrizRiesgoUseCase

) {

    /**
     * Guarda un análisis como borrador.
     *
     * No es necesario que el formulario esté completo.
     */
    @Transactional
    fun guardar(
        username: String,
        request: GuardarMatrizRiesgoRequest
    ): MatrizRiesgoRegistroResponse {

        return persistir(
            username = username,
            request = request,
            estado = EstadoAnalisis.EDITANDO
        )
    }

    /**
     * Registra definitivamente el análisis.
     *
     * El controller valida previamente que los campos
     * obligatorios estén presentes.
     */
    @Transactional
    fun registrar(
        username: String,
        request: GuardarMatrizRiesgoRequest
    ): MatrizRiesgoRegistroResponse {

        return persistir(
            username = username,
            request = request,
            estado = EstadoAnalisis.ABIERTO
        )
    }

    @Transactional
    fun actualizar(
        username: String,
        id: Int,
        request: GuardarMatrizRiesgoRequest
    ): MatrizRiesgoRegistroResponse {

        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException(
                "Usuario no encontrado"
            )

        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException(
                "Usuario inválido"
            )

        val existente =
            analisisRepository.findByIdAndUsuario_Id(
                id,
                usuarioId
            )
                ?: throw ResourceNotFoundException(
                    "El análisis de riesgo no fue encontrado"
                )

        return persistir(
            username = username,
            request = request.copy(id = id),
            estado = existente.estado
        )
    }

    /**
     * Lógica común para crear o actualizar.
     */
    private fun persistir(
        username: String,
        request: GuardarMatrizRiesgoRequest,
        estado: EstadoAnalisis
    ): MatrizRiesgoRegistroResponse {

        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException(
                "Usuario no encontrado"
            )

        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException(
                "Usuario inválido"
            )

        /*
         * Si no llega ID:
         *     crea un análisis nuevo.
         *
         * Si llega ID:
         *     actualiza el análisis existente del usuario.
         */
        val analisis = request.id
            ?.let { id ->
                analisisRepository.findByIdAndUsuario_Id(
                    id,
                    usuarioId
                )
                    ?: throw ResourceNotFoundException(
                        "El análisis de riesgo no fue encontrado"
                    )
            }
            ?: MatrizRiesgoAnalisisEntity(
                usuario = usuario
            )

        val area = obtenerArea(
            id = request.areaId,
            usuarioId = usuarioId
        )

        val proceso = obtenerProceso(
            id = request.procesoId,
            usuarioId = usuarioId
        )

        validarRelacionAreaProceso(
            area = area,
            proceso = proceso
        )

        actualizarDatosInherentes(
            analisis = analisis,
            request = request,
            area = area,
            proceso = proceso
        )

        actualizarDatosControl(
            analisis = analisis,
            request = request,
            usuarioId = usuarioId
        )

        actualizarTratamiento(
            analisis = analisis,
            request = request,
            usuarioId = usuarioId
        )

        analisis.estado = estado
        analisis.fechaActualizacion = LocalDateTime.now()

        calcularSiCorresponde(
            analisis = analisis,
            request = request,
            estado = estado
        )

        val guardado =
            analisisRepository.save(analisis)

        return convertirResponse(guardado)
    }

    /**
     * Actualiza únicamente la sección de riesgo inherente.
     */
    private fun actualizarDatosInherentes(
        analisis: MatrizRiesgoAnalisisEntity,
        request: GuardarMatrizRiesgoRequest,
        area: MatrizRiesgoAreaEntity?,
        proceso: MatrizRiesgoProcesoEntity?
    ) {

        analisis.tipoEmpresa =
            request.tipoEmpresa

        analisis.titulo =
            request.titulo?.trim()

        analisis.area =
            area

        analisis.proceso =
            proceso

        analisis.detalleRiesgo =
            request.detalleRiesgo?.trim()

        analisis.factor =
            request.factor

        analisis.probabilidadNivel =
            request.probabilidad

        analisis.probabilidadOpcion =
            request.probabilidad?.descripcion

        analisis.impactoEstimado =
            request.impactoEstimado
    }

    /**
     * Actualiza únicamente la sección de controles.
     */
    private fun actualizarDatosControl(
        analisis: MatrizRiesgoAnalisisEntity,
        request: GuardarMatrizRiesgoRequest,
        usuarioId: Int
    ) {

        analisis.controlDescripcion =
            request.controlDescripcion?.trim()

        analisis.controlDocumento =
            request.controlDocumento?.trim()

        analisis.controlArea =
            obtenerArea(
                id = request.controlAreaId,
                usuarioId = usuarioId
            )

        analisis.controlPeriodicidad =
            request.periodicidad

        analisis.controlOperatividad =
            request.operatividad

        analisis.controlTipo =
            request.tipoControl

        analisis.controlSupervision =
            request.supervision

        analisis.controlFrecuenciaOportuna =
            request.frecuenciaOportuna
                ?.let { respuesta ->
                    respuesta == RespuestaControl.SI
                }

        analisis.controlSeguimientoAdecuado =
            request.seguimientoAdecuado
                ?.let { respuesta ->
                    respuesta == RespuestaControl.SI
                }
    }

    /**
     * Actualiza la sección de tratamiento.
     */
    private fun actualizarTratamiento(
        analisis: MatrizRiesgoAnalisisEntity,
        request: GuardarMatrizRiesgoRequest,
        usuarioId: Int
    ) {

        analisis.planAccion =
            request.planAccion?.trim()

        analisis.areaResponsable =
            obtenerArea(
                id = request.areaResponsableId,
                usuarioId = usuarioId
            )

        analisis.fechaInicio =
            request.fechaInicio

        analisis.fechaCierre =
            request.fechaCierre

        validarFechas(
            fechaInicio = request.fechaInicio,
            fechaCierre = request.fechaCierre
        )
    }

    /**
     * Comprueba que el proceso realmente esté asociado
     * con el área seleccionada.
     */
    private fun validarRelacionAreaProceso(
        area: MatrizRiesgoAreaEntity?,
        proceso: MatrizRiesgoProcesoEntity?
    ) {

        if (area == null || proceso == null) {
            return
        }

        val existeRelacion =
            areaProcesoRepository.existsByArea_IdAndProceso_Id(
                area.id!!,
                proceso.id!!
            )

        if (!existeRelacion) {
            badRequest(
                "El proceso seleccionado no pertenece al área indicada"
            )
        }
    }

    /**
     * Ejecuta el motor de cálculo cuando existen todos
     * los datos requeridos.
     *
     * Un borrador puede quedar incompleto.
     * Un análisis ABIERTO no.
     */
    private fun calcularSiCorresponde(
        analisis: MatrizRiesgoAnalisisEntity,
        request: GuardarMatrizRiesgoRequest,
        estado: EstadoAnalisis
    ) {

        val datosCalculo =
            request.toCalculoRequestOrNull()

        if (datosCalculo == null) {

            if (estado == EstadoAnalisis.ABIERTO) {
                badRequest(
                    "Faltan datos necesarios para calcular el riesgo"
                )
            }

            limpiarCalculo(analisis)

            return
        }

        val resultado =
            calculadora.ejecutar(datosCalculo)

        analisis.impactoNivel =
            resultado.impactoInherente

        analisis.riesgoInherente =
            resultado.riesgoInherente

        analisis.riesgoInherenteColor =
            obtenerColor(
                resultado.riesgoInherente
            )

        analisis.mitigacion =
            resultado.mitigacion

        analisis.probabilidadResidual =
            resultado.probabilidadResidual

        analisis.impactoResidual =
            resultado.impactoResidual

        analisis.riesgoResidual =
            resultado.riesgoResidual

        analisis.riesgoResidualColor =
            obtenerColor(
                resultado.riesgoResidual
            )
    }

    /**
     * Si un borrador pierde información necesaria para calcular,
     * no debemos conservar resultados antiguos.
     */
    private fun limpiarCalculo(
        analisis: MatrizRiesgoAnalisisEntity
    ) {

        analisis.impactoNivel = null

        analisis.riesgoInherente = null

        analisis.riesgoInherenteColor = null

        analisis.mitigacion = null

        analisis.probabilidadResidual = null

        analisis.impactoResidual = null

        analisis.riesgoResidual = null

        analisis.riesgoResidualColor = null

        analisis.riesgoResidualValor = null
    }

    /**
     * Busca un área y comprueba que pertenece
     * al usuario autenticado.
     */
    private fun obtenerArea(
        id: Int?,
        usuarioId: Int
    ): MatrizRiesgoAreaEntity? {

        if (id == null) {
            return null
        }

        val area =
            areaRepository.findById(id)
                .orElseThrow {
                    ResourceNotFoundException(
                        "Área no encontrada"
                    )
                }

        /*
         * Importante:
         * un área sin propietario tampoco puede ser usada
         * por otro usuario.
         */
        if (area.usuario?.id != usuarioId) {
            throw ResourceNotFoundException(
                "Área no encontrada"
            )
        }

        if (!area.activo) {
            throw ResourceNotFoundException(
                "Área no encontrada"
            )
        }

        return area
    }

    /**
     * Busca un proceso y comprueba que pertenece
     * al usuario autenticado.
     */
    private fun obtenerProceso(
        id: Int?,
        usuarioId: Int
    ): MatrizRiesgoProcesoEntity? {

        if (id == null) {
            return null
        }

        val proceso =
            procesoRepository.findById(id)
                .orElseThrow {
                    ResourceNotFoundException(
                        "Proceso no encontrado"
                    )
                }

        if (proceso.usuario?.id != usuarioId) {
            throw ResourceNotFoundException(
                "Proceso no encontrado"
            )
        }

        if (!proceso.activo) {
            throw ResourceNotFoundException(
                "Proceso no encontrado"
            )
        }

        return proceso
    }

    /**
     * Validación de negocio entre dos fechas.
     *
     * Este IF sí corresponde al dominio:
     * la fecha de cierre no puede ser anterior al inicio.
     */
    private fun validarFechas(
        fechaInicio: java.time.LocalDate?,
        fechaCierre: java.time.LocalDate?
    ) {

        if (
            fechaInicio != null &&
            fechaCierre != null &&
            fechaCierre.isBefore(fechaInicio)
        ) {
            badRequest(
                "La fecha de cierre no puede ser anterior a la fecha de inicio"
            )
        }
    }

    private fun obtenerColor(
        riesgo: NivelRiesgo
    ): String {

        return when (riesgo) {

            NivelRiesgo.MINIMO ->
                "AZUL"

            NivelRiesgo.LEVE ->
                "VERDE"

            NivelRiesgo.MODERADO ->
                "AMARILLO"

            NivelRiesgo.ALTO ->
                "NARANJA"

            NivelRiesgo.MUY_ALTO ->
                "ROJO"
        }
    }

    private fun convertirResponse(
        analisis: MatrizRiesgoAnalisisEntity
    ): MatrizRiesgoRegistroResponse {

        return MatrizRiesgoRegistroResponse(

            id = analisis.id!!,

            estado =
                analisis.estado,

            impactoInherente =
                analisis.impactoNivel,

            riesgoInherente =
                analisis.riesgoInherente,

            mitigacion =
                analisis.mitigacion,

            probabilidadResidual =
                analisis.probabilidadResidual,

            impactoResidual =
                analisis.impactoResidual,

            riesgoResidual =
                analisis.riesgoResidual,

            fechaActualizacion =
                analisis.fechaActualizacion
        )
    }

    private fun badRequest(
        mensaje: String
    ): Nothing {

        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            mensaje
        )
    }
}