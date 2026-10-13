package com.security.modules.operaciones.infraestructure

import java.time.LocalDate

interface OperacionResumenProjection {
    val id: Long
    val numeroRegistro: String?
    val personaFisica: String?
    val personaNombre: String?
    val oficina: String?
    val fecha: LocalDate?
}

interface UsuarioProjection {
    val id: Int
    val usuario: String
    val empresa: String?
}
