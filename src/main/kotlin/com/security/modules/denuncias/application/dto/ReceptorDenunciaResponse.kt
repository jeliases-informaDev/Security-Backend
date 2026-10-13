package com.security.modules.denuncias.application.dto

data class ReceptorDenunciaResponse(

    val id: Int,

    val usuario: String,

    val nombreCompleto: String,

    val correo: String,

    val cargo: String?
)