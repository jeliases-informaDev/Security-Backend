package com.security.modules.denuncias.application.dto

import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import java.time.LocalDateTime

data class CrearDenunciaResponse(

    val id: Int,

    val codigoSeguimiento: String,

    /*
     * El PIN original solo se devuelve
     * al momento de registrar la denuncia.
     *
     * En la base de datos únicamente
     * se almacena su hash.
     */
    val pin: String,

    val estado: EstadoDenuncia,

    val fechaCreacion: LocalDateTime
)