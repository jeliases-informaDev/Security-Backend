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
    val sigla: String? = null,

    // Opcional: el UUID de la empresa en la cuenta Sulotec (asi la reconoce el servicio de licencias); si no viene se genera.
    val uuid: String? = null
)

/** El nombre no se cambia (ver Empresa); se puede corregir el RUC y, si viene, igualar el UUID con el de la cuenta Sulotec. */
data class EmpresaUpdateRequest(
    val ruc: String? = null,

    // Opcional: si no viene, el UUID se queda como esta.
    val uuid: String? = null
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
