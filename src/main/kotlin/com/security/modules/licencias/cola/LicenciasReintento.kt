package com.security.modules.licencias.cola

import com.security.modules.licencias.cliente.LicenciasCliente
import com.security.modules.licencias.cliente.LicenciasNoDisponibleException
import com.security.modules.licencias.config.LicenciasProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Cada minuto reenvía a Sulotec lo que quedó en la cola, en orden y con la MISMA clave (el servicio no descuenta dos
 * veces). Cualquier respuesta del servicio es definitiva y borra la fila: permitido o no, anulado o 404. Si el servicio
 * sigue caído se anota el intento y se corta la vuelta, para no esperar el tiempo máximo en cada fila.
 */
@Component
class LicenciasReintento(
    private val properties: LicenciasProperties,
    private val cliente: LicenciasCliente,
    private val cola: ColaLicencias
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun procesar() {
        if (!properties.activo) return
        try {
            for (fila in cola.siguientes()) {
                val id = fila.id ?: continue
                try {
                    enviar(fila)
                    cola.borrar(id)
                } catch (e: LicenciasNoDisponibleException) {
                    cola.anotarFallo(id, e.message)
                    log.warn("Sulotec sigue sin responder: quedan consumos en la cola ({})", e.message)
                    break
                }
            }
        } catch (e: Exception) {
            log.warn("No se pudo procesar la cola de licencias: {}", e.message)
        }
    }

    private fun enviar(fila: ConsumoPendiente) {
        when (fila.operacion) {
            OperacionPendiente.CONSUMIR -> {
                val uuid = fila.empresaUuid?.trim()?.takeIf { it.isNotEmpty() }
                val tipo = fila.tipo
                // Una fila incompleta no se puede reenviar nunca: se descarta para no trabar la cola.
                if (uuid == null || tipo == null) {
                    log.warn("Consumo {} de la cola sin empresa o sin tipo: se descarta", fila.claveIdempotente)
                    return
                }
                val respuesta = cliente.consumir(
                    uuid, fila.claveIdempotente, tipo, fila.cantidad, fila.usuario ?: "", fila.hechoPorSulotec, fila.detalle
                )
                if (respuesta.permitido != true && !respuesta.sinLicencia) {
                    // La acción ya se hizo: queda en el log para que Sulotec lo revise si hace falta.
                    log.warn("Sulotec rechazó el consumo encolado {} ({})", fila.claveIdempotente, respuesta.motivo)
                }
            }
            OperacionPendiente.ANULAR -> cliente.anular(fila.claveIdempotente)
        }
    }
}
