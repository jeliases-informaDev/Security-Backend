package com.security.modules.matrices.application.dto

import com.security.modules.matrices.domain.enums.EstadoAnalisis
import com.security.modules.matrices.domain.enums.FactorRiesgo
import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import com.security.modules.matrices.domain.enums.NivelSupervision
import com.security.modules.matrices.domain.enums.OperatividadControl
import com.security.modules.matrices.domain.enums.PeriodicidadControl
import com.security.modules.matrices.domain.enums.TipoControl
import com.security.modules.matrices.domain.enums.TipoEmpresa
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class MatrizRiesgoDetalleResponse(

    val id: Int,

    val tipoEmpresa: TipoEmpresa?,
    val titulo: String?,

    val areaId: Int?,
    val area: String?,

    val procesoId: Int?,
    val proceso: String?,

    val detalleRiesgo: String?,
    val factor: FactorRiesgo?,

    val probabilidad: NivelProbabilidad?,
    val impactoEstimado: BigDecimal?,
    val impactoInherente: NivelImpacto?,
    val riesgoInherente: NivelRiesgo?,

    val controlDescripcion: String?,
    val controlDocumento: String?,

    val controlAreaId: Int?,
    val controlArea: String?,

    val periodicidad: PeriodicidadControl?,
    val operatividad: OperatividadControl?,
    val tipoControl: TipoControl?,
    val supervision: NivelSupervision?,

    val frecuenciaOportuna: Boolean?,
    val seguimientoAdecuado: Boolean?,

    val mitigacion: BigDecimal?,
    val probabilidadResidual: NivelProbabilidad?,
    val impactoResidual: NivelImpacto?,
    val riesgoResidual: NivelRiesgo?,

    val planAccion: String?,

    val areaResponsableId: Int?,
    val areaResponsable: String?,

    val fechaInicio: LocalDate?,
    val fechaCierre: LocalDate?,

    val estado: EstadoAnalisis,

    val fechaCreacion: LocalDateTime,
    val fechaActualizacion: LocalDateTime
)