package com.security.modules.ml.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Conexión con el microservicio de Machine Learning (FastAPI).
 * El frontend nunca llama al ML directamente: lo hace este backend con la clave interna.
 */
@ConfigurationProperties(prefix = "ml")
data class MlProperties(
    val baseUrl: String = "http://localhost:8000",
    val apiKey: String = "",
    // Indira usa un LLM local y puede tardar bastante en responder.
    val chatTimeoutSeconds: Long = 120,
    val timeoutSeconds: Long = 15
)

@Configuration
@EnableConfigurationProperties(MlProperties::class)
class MlConfig
