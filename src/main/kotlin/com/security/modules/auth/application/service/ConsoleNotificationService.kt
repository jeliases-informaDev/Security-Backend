package com.security.modules.auth.application.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Implementación provisional: registra el enlace de recuperación en el log del servidor
 * en lugar de enviarlo por correo. Reemplazar por una implementación real (SMTP, SES, etc.)
 * cuando se defina el proveedor de correo del proyecto.
 */
@Service
class ConsoleNotificationService : NotificationService {

    private val log = LoggerFactory.getLogger(ConsoleNotificationService::class.java)

    override fun enviarCorreoRecuperacion(destinatario: String, nombre: String, token: String) {
        log.info(
            "[RECUPERACION DE CONTRASEÑA] Para: {} ({}) | Token: {} | " +
                "Enlace sugerido: http://localhost:3000/restablecer-clave?token={}",
            nombre, destinatario, token, token
        )
    }
}
