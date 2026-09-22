package com.security

import com.security.modules.auth.application.service.JwtAuthenticationFilter
import com.security.shared.security.RestAccessDeniedHandler
import com.security.shared.security.RestAuthenticationEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val restAuthenticationEntryPoint: RestAuthenticationEntryPoint,
    private val restAccessDeniedHandler: RestAccessDeniedHandler
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {

        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/public/**",
                        "/actuator/health",
                        "/api/auth/login",
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password"
                    ).permitAll()
                    .anyRequest().authenticated()
            }

            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(restAuthenticationEntryPoint)
                    .accessDeniedHandler(restAccessDeniedHandler)
            }

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }


    @Bean
    fun corsFilter(): CorsFilter {

        val source = UrlBasedCorsConfigurationSource()

        val config = CorsConfiguration()

        config.allowCredentials = true
        config.addAllowedOrigin("http://localhost:3000")
        config.addAllowedHeader("*")
        config.addAllowedMethod("*")

        source.registerCorsConfiguration("/**", config)

        return CorsFilter(source)
    }

}