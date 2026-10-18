package com.security.modules.empresas.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EmpresaCreateRequest(
    @field:NotBlank(message = "El nombre de la empresa es obligatorio")
    @field:Size(max = 255, message = "El nombre de la empresa admite hasta 255 caracteres")
    val nombre: String,

    // Opcional; si viene, 11 digitos.
    val ruc: String? = null,

    // Opcional: 2 a 8 letras o digitos; si no viene se propone a partir del nombre.
    val sigla: String? = null
)

/** El nombre no se cambia (ver Empresa); solo se puede corregir el RUC. */
data class EmpresaUpdateRequest(
    val ruc: String? = null
)

data class EmpresaResponse(
    val id: Int,
    val uuid: String?,
    val sigla: String?,
    val nombre: String,
    val ruc: String?,
    val activa: Boolean,
    // Cuentas (usuarios) que tienen esta empresa
    val cuentas: Long
)
