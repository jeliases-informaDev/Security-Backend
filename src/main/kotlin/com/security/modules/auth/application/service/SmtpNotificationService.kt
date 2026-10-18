package com.security.modules.auth.application.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

/**
 * Correo real por SMTP (por ejemplo OCI Email Delivery). Se activa cuando existe spring.mail.host
 * (variables SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD y
 * SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH / STARTTLS_ENABLE). El remitente es app.mail.from.
 */
@Service
@ConditionalOnProperty(name = ["spring.mail.host"])
class SmtpNotificationService(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from:no-reply@sulotec.com}") private val remitente: String,
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontend: String,
    @Value("\${app.mail.nombre:PLAFT}") private val nombreApp: String
) : NotificationService {

    private val log = LoggerFactory.getLogger(SmtpNotificationService::class.java)

    override fun invitacionesPorCorreo(): Boolean = true

    override fun enviarCorreoRecuperacion(destinatario: String, nombre: String, token: String) {
        enviar(
            destinatario,
            "$nombreApp: restablecer tu contraseña",
            """
            Hola, $nombre.

            Recibimos un pedido para restablecer tu contraseña en $nombreApp. Entra a este enlace (vale 30 minutos y se usa una sola vez):

            $frontend/restablecer-clave?token=$token

            Si no fuiste tú, ignora este mensaje: tu contraseña no cambia.
            """.trimIndent()
        )
    }

    override fun enviarInvitacion(destinatario: String, nombre: String, empresa: String, usuario: String, token: String, horasValidez: Long) {
        enviar(
            destinatario,
            "$nombreApp: tu cuenta en $empresa",
            """
            Hola, $nombre.

            Te crearon una cuenta en $nombreApp para $empresa. Para elegir tu contraseña entra a este enlace (vale $horasValidez horas y se usa una sola vez):

            $frontend/restablecer-clave?token=$token

            Después entra en $frontend con tu correo ($destinatario) o tu usuario ($usuario) y la contraseña que elegiste.

            Si el enlace venció, usa "¿Olvidaste tu contraseña?" en la pantalla de ingreso.
            """.trimIndent()
        )
    }

    private fun enviar(destinatario: String, asunto: String, texto: String) {
        val mensaje = SimpleMailMessage()
        mensaje.from = remitente
        mensaje.setTo(destinatario)
        mensaje.subject = asunto
        mensaje.text = texto
        mailSender.send(mensaje)
        log.info("Correo enviado a {}: {}", destinatario, asunto)
    }
}
