package com.security.modules.auth.application.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Sin correo de salida configurado (no hay spring.mail.host): los enlaces se registran en el log del
 * servidor en vez de enviarse. Con SMTP configurado se usa SmtpNotificationService.
 */
@Service
@ConditionalOnProperty(name = ["spring.mail.host"], havingValue = "__sin_correo__", matchIfMissing = true)
class ConsoleNotificationService(
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontend: String
) : NotificationService {

    private val log = LoggerFactory.getLogger(ConsoleNotificationService::class.java)

    override fun invitacionesPorCorreo(): Boolean = false

    override fun enviarCorreoRecuperacion(destinatario: String, nombre: String, token: String) {
        log.info(
            "[RECUPERACION DE CONTRASEÑA] Para: {} ({}) | Enlace: {}/restablecer-clave?token={}",
            nombre, destinatario, frontend, token
        )
    }

    override fun enviarInvitacion(destinatario: String, nombre: String, empresa: String, usuario: String, token: String, horasValidez: Long) {
        log.info(
            "[INVITACION] Para: {} ({}) de {} | usuario {} | Enlace ({} h): {}/restablecer-clave?token={}",
            nombre, destinatario, empresa, usuario, horasValidez, frontend, token
        )
    }
}
