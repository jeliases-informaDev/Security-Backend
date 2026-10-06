package com.security.modules.auth.security

import com.security.shared.datos.entities.Usuario
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date

@Service
class JwtService(
    // Se define con JWT_SECRET (variable de entorno o .env); ver application.yml.
    @Value("\${jwt.secret}") secret: String
) {

    private val expiration = 15 * 60 * 1000L // 15 minutos

    private val key = Keys.hmacShaKeyFor(
        secret.toByteArray(StandardCharsets.UTF_8)
    )

    fun generateToken(usuario: Usuario): String {

        val now = Date()
        val expirationDate = Date(now.time + expiration)

        val rol = usuario.roles.firstOrNull()?.rol

        val builder = Jwts.builder()
            .subject(usuario.usuario)
            .claim("userId", usuario.id)
            .issuedAt(now)
            .expiration(expirationDate)

        if (rol != null) {
            builder.claim("rol", rol.codigo)
        }

        return builder.signWith(key).compact()
    }

    fun extractUsername(token: String): String? {
        return parseClaims(token).subject
    }

    fun extractRole(token: String): String? {
        return parseClaims(token).get("rol", String::class.java)
    }

    private fun parseClaims(token: String) =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}