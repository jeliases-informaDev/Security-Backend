package com.security.modules.operaciones.dto

data class RegistrarOperacionResponse(
    val id: Long,
    val numeroRegistro: String,
    val mensaje: String = "Operacion registrada correctamente"
)
