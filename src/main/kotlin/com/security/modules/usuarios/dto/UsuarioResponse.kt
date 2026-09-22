package com.security.modules.usuarios.dto

data class UsuarioResponse(
    val id: Int,
    val usuario: String,
    val correo: String,
    val nombreCompleto: String,
    val cargo: String?,
    val empresa: String?,
    val rolCodigo: String?,
    val rolNombre: String?,
    val activo: Boolean
)
