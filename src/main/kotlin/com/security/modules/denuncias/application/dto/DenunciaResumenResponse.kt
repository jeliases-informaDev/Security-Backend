package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
import com.security.modules.denuncias.application.service.DerivacionResponse
import com.security.modules.denuncias.domain.enums.ResultadoCierreDenuncia
import java.time.LocalDate
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

    val fechaCierre: LocalDateTime?,

    // ---- Campos nuevos (aditivos; docs/DENUNCIAS-CIERRE.md)
    val causaEtiqueta: String? = null,
    val estadoEtiqueta: String? = null,
    val fechaLimiteAtencion: LocalDate? = null,
    // null si esta cerrada; negativo = vencida hace N dias
    val diasParaVencer: Long? = null,
    val vencida: Boolean = false,
    // null si sigue abierta
    val atendidaATiempo: Boolean? = null,
    val resultadoCierre: ResultadoCierreDenuncia? = null,
    val resultadoCierreEtiqueta: String? = null,
    val motivoCierre: String? = null,
    // Reservado: null para quien no tiene el permiso del Oficial de Cumplimiento
    val derivadaAlOficial: Boolean? = null
)