package com.security.modules.licencias.cola

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Cola local de consumos y anulaciones por avisar a Sulotec. Cada cambio va en su propia transacción
 * (REQUIRES_NEW): un fallo aquí no debe arrastrar la transacción de la acción de la persona, y lo encolado
 * no depende de si esa acción termina bien.
 */
@Service
class ColaLicencias(private val repositorio: ConsumoPendienteRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun encolarConsumo(
        empresaUuid: String,
        clave: String,
        tipo: String,
        cantidad: Int,
        usuario: String,
        hechoPorSulotec: Boolean,
        detalle: String?,
        error: String?
    ) {
        if (repositorio.existsByClaveIdempotenteAndOperacion(clave, OperacionPendiente.CONSUMIR)) return
        repositorio.save(
            ConsumoPendiente(
                operacion = OperacionPendiente.CONSUMIR,
                claveIdempotente = clave,
                empresaUuid = empresaUuid,
                tipo = tipo,
                cantidad = cantidad,
                usuario = usuario.take(150),
                hechoPorSulotec = hechoPorSulotec,
                detalle = detalle?.take(500),
                ultimoError = error?.take(500),
                fechaCreacion = LocalDateTime.now()
            )
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun encolarAnulacion(clave: String, error: String?) {
        if (repositorio.existsByClaveIdempotenteAndOperacion(clave, OperacionPendiente.ANULAR)) return
        repositorio.save(
            ConsumoPendiente(
                operacion = OperacionPendiente.ANULAR,
                claveIdempotente = clave,
                ultimoError = error?.take(500),
                fechaCreacion = LocalDateTime.now()
            )
        )
    }

    /** Si el consumo aún esperaba en la cola, nunca llegó a Sulotec: basta con quitarlo. Devuelve true si estaba. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun quitarConsumoPendiente(clave: String): Boolean {
        val fila = repositorio.findByClaveIdempotenteAndOperacion(clave, OperacionPendiente.CONSUMIR) ?: return false
        repositorio.delete(fila)
        return true
    }

    @Transactional(readOnly = true)
    fun siguientes(): List<ConsumoPendiente> = repositorio.findTop100ByOrderByIdAsc()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun borrar(id: Long) {
        repositorio.deleteById(id)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun anotarFallo(id: Long, error: String?) {
        val fila = repositorio.findById(id).orElse(null) ?: return
        fila.intentos += 1
        fila.ultimoError = error?.take(500)
        repositorio.save(fila)
    }
}
