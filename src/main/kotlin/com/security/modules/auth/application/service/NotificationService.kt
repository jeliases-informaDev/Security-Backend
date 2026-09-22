package com.security.modules.auth.application.service

interface NotificationService {
    fun enviarCorreoRecuperacion(destinatario: String, nombre: String, token: String)
}
