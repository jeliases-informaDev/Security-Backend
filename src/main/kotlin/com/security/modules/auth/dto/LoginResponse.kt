package com.security.modules.auth.dto

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val usuario: UsuarioLoginResponse
)