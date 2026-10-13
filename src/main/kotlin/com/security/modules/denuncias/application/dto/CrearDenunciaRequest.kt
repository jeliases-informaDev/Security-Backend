package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CrearDenunciaRequest(

    // =========================
    // DENUNCIA
    // =========================

    @field:NotNull(
        message = "La causa de la denuncia es obligatoria"
    )
    val causa: CausaDenuncia? = null,

    @field:NotNull(
        message = "La relación con la empresa es obligatoria"
    )
    val relacionEmpresa: RelacionEmpresa? = null,

    @field:NotBlank(
        message = "La descripción de la denuncia es obligatoria"
    )
    val descripcion: String? = null,

    // =========================
    // RECEPTOR
    // =========================

    val receptorUsuarioId: Int? = null,

    val receptorPropuesto: String? = null,

    // =========================
    // INVOLUCRADOS
    // =========================

    @field:Valid
    @field:Size(
        min = 1,
        message = "Debe registrar al menos una persona o empresa involucrada"
    )
    val involucrados: List<CrearInvolucradoRequest> =
        emptyList(),

    // =========================
    // DENUNCIANTE
    // =========================

    val anonima: Boolean = false,

    val nombreDenunciante: String? = null,

    val documentoDenunciante: String? = null,

    val telefonoDenunciante: String? = null,

    val correoDenunciante: String? = null,

    // =========================
    // TÉRMINOS
    // =========================

    @field:AssertTrue(
        message = "Debe aceptar los términos y condiciones"
    )
    val aceptaTerminos: Boolean = false
)