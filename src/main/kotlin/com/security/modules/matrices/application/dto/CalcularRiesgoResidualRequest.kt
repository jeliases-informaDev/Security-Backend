package com.security.modules.matrices.application.dto

import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelSupervision
import com.security.modules.matrices.domain.enums.OperatividadControl
import com.security.modules.matrices.domain.enums.PeriodicidadControl
import com.security.modules.matrices.domain.enums.RespuestaControl
import com.security.modules.matrices.domain.enums.TipoControl
import com.security.modules.matrices.domain.enums.TipoEmpresa
import java.math.BigDecimal

data class CalcularRiesgoResidualRequest(
    val tipoEmpresa: TipoEmpresa,
    val probabilidadInherente: NivelProbabilidad,
    val impactoEstimado: BigDecimal,

    val supervision: NivelSupervision,
    val tipoControl: TipoControl,
    val operatividad: OperatividadControl,
    val periodicidad: PeriodicidadControl,
    val frecuenciaOportuna: RespuestaControl,
    val seguimientoAdecuado: RespuestaControl
)