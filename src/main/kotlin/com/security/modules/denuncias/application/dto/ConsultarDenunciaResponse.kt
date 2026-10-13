package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
import java.time.LocalDateTime

data class ConsultarDenunciaResponse(

    val codigoSeguimiento: String,

    val causa: CausaDenuncia,

    val relacionEmpresa: RelacionEmpresa,

    val descripcion: String,

    val estado: EstadoDenuncia,

    val fechaCreacion: LocalDateTime,

    val fechaActualizacion: LocalDateTime,

    val fechaCierre: LocalDateTime?,

    val historial: List<SeguimientoDenunciaHistorialResponse>
)