package com.security.modules.matrices.application.dto

import com.security.modules.matrices.application.validation.RegistroAnalisisValidation
import com.security.modules.matrices.domain.enums.FactorRiesgo
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelSupervision
import com.security.modules.matrices.domain.enums.OperatividadControl
import com.security.modules.matrices.domain.enums.PeriodicidadControl
import com.security.modules.matrices.domain.enums.RespuestaControl
import com.security.modules.matrices.domain.enums.TipoControl
import com.security.modules.matrices.domain.enums.TipoEmpresa
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class GuardarMatrizRiesgoRequest(

    val id: Int? = null,

    // =========================
    // RIESGO INHERENTE
    // =========================

    @field:NotNull(
        message = "El tipo de empresa es obligatorio",
        groups = [RegistroAnalisisValidation::class]
    )
    val tipoEmpresa: TipoEmpresa? = null,

    @field:NotBlank(
        message = "El título es obligatorio",
        groups = [RegistroAnalisisValidation::class]
    )
    val titulo: String? = null,

    @field:NotNull(
        message = "El área es obligatoria",
        groups = [RegistroAnalisisValidation::class]
    )
    val areaId: Int? = null,

    @field:NotNull(
        message = "El proceso es obligatorio",
        groups = [RegistroAnalisisValidation::class]
    )
    val procesoId: Int? = null,

    @field:NotBlank(
        message = "El detalle del riesgo es obligatorio",
        groups = [RegistroAnalisisValidation::class]
    )
    val detalleRiesgo: String? = null,

    @field:NotNull(
        message = "El factor de riesgo es obligatorio",
        groups = [RegistroAnalisisValidation::class]
    )
    val factor: FactorRiesgo? = null,

    @field:NotNull(
        message = "La probabilidad es obligatoria",
        groups = [RegistroAnalisisValidation::class]
    )
    val probabilidad: NivelProbabilidad? = null,

    @field:NotNull(
        message = "El impacto estimado es obligatorio",
        groups = [RegistroAnalisisValidation::class]
    )
    @field:DecimalMin(
        value = "0.00",
        inclusive = true,
        message = "El impacto estimado no puede ser negativo",
        groups = [RegistroAnalisisValidation::class]
    )
    val impactoEstimado: BigDecimal? = null,

    // =========================
    // IDENTIFICACIÓN DEL CONTROL
    // =========================

    val controlDescripcion: String? = null,

    val controlDocumento: String? = null,

    val controlAreaId: Int? = null,

    // =========================
    // DISEÑO Y EJECUCIÓN
    // =========================

    @field:NotNull(
        message = "La periodicidad es obligatoria",
        groups = [RegistroAnalisisValidation::class]
    )
    val periodicidad: PeriodicidadControl? = null,

    @field:NotNull(
        message = "La operatividad es obligatoria",
        groups = [RegistroAnalisisValidation::class]
    )
    val operatividad: OperatividadControl? = null,

    @field:NotNull(
        message = "El tipo de control es obligatorio",
        groups = [RegistroAnalisisValidation::class]
    )
    val tipoControl: TipoControl? = null,

    @field:NotNull(
        message = "La supervisión es obligatoria",
        groups = [RegistroAnalisisValidation::class]
    )
    val supervision: NivelSupervision? = null,

    @field:NotNull(
        message = "La frecuencia oportuna es obligatoria",
        groups = [RegistroAnalisisValidation::class]
    )
    val frecuenciaOportuna: RespuestaControl? = null,

    @field:NotNull(
        message = "El seguimiento adecuado es obligatorio",
        groups = [RegistroAnalisisValidation::class]
    )
    val seguimientoAdecuado: RespuestaControl? = null,

    // =========================
    // TRATAMIENTO
    // =========================

    val planAccion: String? = null,

    val areaResponsableId: Int? = null,

    val fechaInicio: LocalDate? = null,

    val fechaCierre: LocalDate? = null
) {

    /**
     * Devuelve los datos necesarios para calcular la matriz
     * únicamente cuando todos los campos requeridos existen.
     *
     * Para un borrador incompleto devuelve null.
     */
    fun toCalculoRequestOrNull(): CalcularMatrizRiesgoRequest? {

        val tipoEmpresa = tipoEmpresa ?: return null
        val probabilidad = probabilidad ?: return null
        val impactoEstimado = impactoEstimado ?: return null
        val supervision = supervision ?: return null
        val tipoControl = tipoControl ?: return null
        val operatividad = operatividad ?: return null
        val periodicidad = periodicidad ?: return null
        val frecuenciaOportuna = frecuenciaOportuna ?: return null
        val seguimientoAdecuado = seguimientoAdecuado ?: return null

        return CalcularMatrizRiesgoRequest(
            tipoEmpresa = tipoEmpresa,
            probabilidad = probabilidad,
            impactoEstimado = impactoEstimado,
            supervision = supervision,
            tipoControl = tipoControl,
            operatividad = operatividad,
            periodicidad = periodicidad,
            frecuenciaOportuna = frecuenciaOportuna,
            seguimientoAdecuado = seguimientoAdecuado
        )
    }
}