
package com.security.modules.operaciones.application.usecase

import com.security.modules.operaciones.domain.port.out.GeneradorPdfPort
import com.security.modules.operaciones.domain.port.out.OperacionConsultaPort
import org.springframework.stereotype.Service

@Service
class ExportarOperacionPdfUseCase(
    private val operacionConsultaPort: OperacionConsultaPort,
    private val generadorPdfPort: GeneradorPdfPort
) {
    fun ejecutar(id: Long): ByteArray {
        require(id > 0) {
            "El ID de la operación debe ser mayor que cero"
        }

        val operacion = operacionConsultaPort.obtenerParaExportacion(id)
            ?: throw OperacionNoEncontradaException(id)

        return generadorPdfPort.generar(operacion)
    }
}

class OperacionNoEncontradaException(id: Long) :
    RuntimeException("No existe una operación con ID $id")
