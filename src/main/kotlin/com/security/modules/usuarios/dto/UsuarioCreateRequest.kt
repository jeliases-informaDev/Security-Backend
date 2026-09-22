package com.security.modules.usuarios.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UsuarioCreateRequest(

    @field:NotBlank(message = "El usuario es obligatorio")
    val usuario: String,

    @field:NotBlank(message = "La contraseña es obligatoria")
    @field:Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    val clave: String,

    @field:NotBlank(message = "El correo es obligatorio")
    @field:Email(message = "El correo no es válido")
    val correo: String,

    val nombres: String? = null,
    val apePat: String? = null,
    val apeMat: String? = null,
    val cargo: String? = null,
    val empresa: String? = null,

    @field:NotBlank(message = "El rol es obligatorio")
    val rolCodigo: String
)
