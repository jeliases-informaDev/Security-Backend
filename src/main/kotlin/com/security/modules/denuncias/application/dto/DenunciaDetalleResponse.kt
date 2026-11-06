package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
import com.security.modules.denuncias.application.service.DerivacionResponse
import com.security.modules.denuncias.domain.enums.ResultadoCierreDenuncia
import java.time.LocalDate
import java.time.LocalDateTime

data class DenunciaDetalleResponse(

    val id: Int,

    // =========================
    // DENUNCIA
    // =========================

    val causa: CausaDenuncia,

    val relacionEmpresa: RelacionEmpresa,

    val descripcion: String,

    // =========================
    // RECEPTOR
    // =========================

    val receptorUsuarioId: Int?,

    val receptor: String?,

    val receptorPropuesto: String?,

    // =========================
    // DENUNCIANTE
    // =========================

    val anonima: Boolean,

    val nombreDenunciante: String?,

    val documentoDenunciante: String?,

    val telefonoDenunciante: String?,

    val correoDenunciante: String?,

    // =========================
    // ESTADO
    // =========================

    val estado: EstadoDenuncia,

    // =========================
    // RELACIONES
    // =========================

    val involucrados:
    List<DenunciaInvolucradoResponse>,

    val archivos:
    List<DenunciaArchivoResponse>,

    val historial:
    List<DenunciaHistorialResponse>,

    // =========================
    // FECHAS
    // =========================

    val fechaCreacion: LocalDateTime,

    val fechaActualizacion: LocalDateTime,

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
    val derivadaAlOficial: Boolean? = null,
    // Reservado: quien, cuando y motivo (solo con el permiso del Oficial de Cumplimiento)
    val derivacion: DerivacionResponse? = null
)