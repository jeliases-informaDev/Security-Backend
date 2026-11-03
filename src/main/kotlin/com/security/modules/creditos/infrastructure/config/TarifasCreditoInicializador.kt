package com.security.modules.creditos.infrastructure.config

import com.security.modules.creditos.domain.AccionCredito
import com.security.modules.creditos.domain.TarifaCredito
import com.security.modules.creditos.infrastructure.repository.TarifaCreditoRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Deja creadas las tarifas iniciales en BD si no existen.
 */
@Component
@Order(3)
class TarifasCreditoInicializador(
    private val tarifaRepository: TarifaCreditoRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            val ahora = LocalDateTime.now()
            for (accion in AccionCredito.values()) {
                if (!tarifaRepository.existsById(accion)) {
                    tarifaRepository.save(
                        TarifaCredito(
                            accion = accion,
                            costo = accion.costoInicial,
                            fechaActualizacion = ahora
                        )
                    )
                    log.info("Tarifa creada para {}: {} créditos", accion, accion.costoInicial)
                }
            }
        } catch (e: Exception) {
            log.warn("No se pudieron inicializar las tarifas de créditos: {}", e.message)
        }
    }
}
