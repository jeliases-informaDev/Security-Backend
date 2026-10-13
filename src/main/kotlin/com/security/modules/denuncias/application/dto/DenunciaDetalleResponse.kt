package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
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

    val fechaCierre: LocalDateTime?
)