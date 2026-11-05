package com.security.modules.licencias.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Conexión con el servicio común de licencias de Sulotec: la única verdad de la licencia de cada empresa.
 * PLAFT solo pregunta, consume y muestra. Con la URL o el token vacíos las licencias están apagadas:
 * nada se consulta, nada se bloquea y nada falla.
 */
@ConfigurationProperties(prefix = "licencias")
data class LicenciasProperties(
    val url: String = "",
    val token: String = "",
    val producto: String = "plaft",
    // Espera máxima por llamada: una caída de Sulotec no debe demorar a la persona más que esto.
    val timeoutSegundos: Long = 3,
    val cacheSegundos: Long = 300,
    // Tras un fallo no se pregunta en cada petición: se espera esta pausa antes de volver a intentar.
    val pausaTrasFalloSegundos: Long = 30
) {
    val activo: Boolean
        get() = url.isNotBlank() && token.isNotBlank()

    /** URL base sin espacios ni barra final, para armar las rutas del servicio. */
    val urlBase: String
        get() = url.trim().trimEnd('/')
}

@Configuration
@EnableConfigurationProperties(LicenciasProperties::class)
class LicenciasConfig
