package com.security.modules.auth.application.service

/** Correos que manda la plataforma. La implementacion depende de si hay correo de salida configurado (spring.mail.host). */
interface NotificationService {

    /** Si las invitaciones y recuperaciones llegan de verdad por correo (false: solo se registran en el log). */
    fun invitacionesPorCorreo(): Boolean

    fun enviarCorreoRecuperacion(destinatario: String, nombre: String, token: String)

    /** Cuenta nueva: enlace de un solo uso para que la persona elija su clave. */
    fun enviarInvitacion(destinatario: String, nombre: String, empresa: String, usuario: String, token: String, horasValidez: Long)
}
