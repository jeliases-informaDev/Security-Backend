package com.security.modules.auth.application.service

import com.security.modules.auth.security.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        // No hay token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7)

        try {
            val username = jwtService.extractUsername(token)
            val rol = jwtService.extractRole(token)

            if (username != null &&
                SecurityContextHolder.getContext().authentication == null
            ) {

                val authorities = if (rol != null) {
                    listOf(SimpleGrantedAuthority("ROLE_$rol"))
                } else {
                    emptyList()
                }

                val authentication = UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    authorities
                )

                SecurityContextHolder.getContext().authentication = authentication
            }

        } catch (e: Exception) {
            // Token inválido o expirado
            SecurityContextHolder.clearContext()
        }

        filterChain.doFilter(request, response)
    }
}