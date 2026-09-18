package com.security.modules.auth.dto

data class UsuarioLoginResponse(
    val nombreCompleto: String,
    val usuario: String,
    val codigo: String,
    val rol: String
)