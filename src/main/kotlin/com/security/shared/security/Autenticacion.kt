package com.security.shared.security

import org.springframework.security.core.Authentication

const val ROL_ADMINISTRADOR = "ADMINISTRADOR"

/** El rol viene en el token (lo pone JwtAuthenticationFilter como ROLE_<codigo>). */
fun Authentication.esAdministrador(): Boolean =
    authorities.any { it.authority == "ROLE_$ROL_ADMINISTRADOR" }
