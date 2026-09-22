package com.security.modules.auth.application.service

import com.security.modules.auth.domain.PasswordResetToken
import com.security.modules.auth.infrastructure.PasswordResetTokenRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.InvalidTokenException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class PasswordResetService(
    private val usuarioRepository: UsuarioRepository,
    private val tokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val notificationService: NotificationService
) {

    @Transactional
    fun solicitarRecuperacion(correo: String) {

        // Respuesta siempre genérica en el controlador: no revelamos si el correo existe
        val usuario = usuarioRepository.findByCorreo(correo) ?: return
        if (!usuario.activo) return

        tokenRepository.invalidarPendientesDe(usuario.id!!)

        val token = UUID.randomUUID().toString()

        tokenRepository.save(
            PasswordResetToken(
                usuario = usuario,
                token = token,
                fechaExpiracion = LocalDateTime.now().plusMinutes(EXPIRACION_MINUTOS)
            )
        )

        notificationService.enviarCorreoRecuperacion(
            destinatario = usuario.correo,
            nombre = usuario.nombres ?: usuario.usuario,
            token = token
        )
    }

    @Transactional
    fun restablecerClave(token: String, nuevaClave: String) {

        val resetToken = tokenRepository.findByToken(token)
            ?: throw InvalidTokenException("El enlace de recuperación no es válido")

        if (resetToken.usado || resetToken.fechaExpiracion.isBefore(LocalDateTime.now())) {
            throw InvalidTokenException("El enlace de recuperación ha expirado o ya fue utilizado")
        }

        val usuario = resetToken.usuario
        usuario.clave = passwordEncoder.encode(nuevaClave)!!
        usuario.fechaActualizacion = LocalDateTime.now()
        usuarioRepository.save(usuario)

        resetToken.usado = true
        tokenRepository.save(resetToken)
    }

    companion object {
        private const val EXPIRACION_MINUTOS = 30L
    }
}
