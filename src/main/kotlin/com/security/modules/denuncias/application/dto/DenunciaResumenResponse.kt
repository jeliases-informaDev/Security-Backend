package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
import java.time.LocalDateTime

data class DenunciaResumenResponse(

    val id: Int,

    val causa: CausaDenuncia,

    val relacionEmpresa: RelacionEmpresa,

    val descripcion: String,

    val receptor: String?,

    val estado: EstadoDenuncia,

    val anonima: Boolean,

    val fechaCreacion: LocalDateTime,

    val fechaCierre: LocalDateTime?
)