package com.security.modules.listas.dto

import java.time.LocalDateTime

data class HistorialConsultaResponse(
    val id: Int,
    val fechaConsulta: LocalDateTime,
    val resultado: ResultadoBusquedaResponse
)
