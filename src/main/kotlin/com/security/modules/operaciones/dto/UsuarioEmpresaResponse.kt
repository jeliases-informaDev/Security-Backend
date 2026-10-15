package com.security.modules.operaciones.dto

data class UsuarioEmpresaResponse(
    val id: Int,
    val usuario: String,
    val nombreCompleto: String,
    val empresa: String,
    val activo: Boolean
)
