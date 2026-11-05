package com.security.modules.usuarios.dto

import com.security.modules.auth.sso.CuentaSulotecResponse

data class UsuarioResponse(
    val id: Int,
    val usuario: String,
    val correo: String,
    val nombreCompleto: String,
    val cargo: String?,
    val empresa: String?,
    val rolCodigo: String?,
    val rolNombre: String?,
    val activo: Boolean,
    // Solo en el alta y en modo Sulotec: lo que dijo Sulotec de la cuenta (null fuera de ese modo).
    val cuentaSulotec: CuentaSulotecResponse? = null
)
