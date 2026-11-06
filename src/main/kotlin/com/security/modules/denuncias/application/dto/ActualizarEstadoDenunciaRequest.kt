package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.ResultadoCierreDenuncia
import jakarta.validation.constraints.NotNull

data class ActualizarEstadoDenunciaRequest(

    @field:NotNull(
        message = "El nuevo estado es obligatorio"
    )
    val estado: EstadoDenuncia? = null,

    val comentario: String? = null,

    // Obligatorio para pasar a CERRADO. motivoCierre: obligatorio con ARCHIVADA (hasta 1000).
    val resultadoCierre: ResultadoCierreDenuncia? = null,

    val motivoCierre: String? = null
)

/** POST /api/denuncias/{id}/derivar-oficial (motivo opcional, hasta 500). */
data class DerivarOficialRequest(
    val motivo: String? = null
)
