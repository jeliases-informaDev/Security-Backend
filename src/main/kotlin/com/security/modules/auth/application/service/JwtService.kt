package com.security.modules.auth.security

import com.security.shared.datos.entities.Usuario
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date

@Service
class JwtService {

    private val secret = System.getenv("JWT_SECRET")
        ?: "CLAVE-TEMPORAL-SOLO-PARA-DESARROLLO-123456789"

    private val expiration = 15 * 60 * 1000L // 15 minutos

    private val key = Keys.hmacShaKeyFor(
        secret.toByteArray(StandardCharsets.UTF_8)
    )

    fun generateToken(usuario: Usuario): String {

        val now = Date()
        val expirationDate = Date(now.time + expiration)

        return Jwts.builder()
            .subject(usuario.usuario)
            .claim("userId", usuario.id)
            // Si tu entidad tiene rol:
            // .claim("rol", usuario.rol)
            .issuedAt(now)
            .expiration(expirationDate)
            .signWith(key)
            .compact()
    }

    fun extractUsername(token: String): String? {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
    }
}