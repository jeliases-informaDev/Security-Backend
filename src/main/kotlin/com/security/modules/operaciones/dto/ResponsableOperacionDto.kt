package com.security.modules.operaciones.dto

import jakarta.validation.constraints.Positive

data class UsuarioAsignableResponse(val id: Int, val nombreCompleto: String)

data class AsignarResponsableRequest(@field:Positive val usuarioId: Int)

data class AsignarResponsableResponse(val id: Long, val responsable: UsuarioAsignableResponse)
