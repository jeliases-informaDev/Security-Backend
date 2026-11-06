package com.security.modules.operaciones.application.usecase

import com.security.modules.operaciones.application.service.AutorEvento
import com.security.modules.operaciones.application.service.FotoOperacion
import com.security.modules.operaciones.application.service.HistorialOperacionService
import com.security.modules.operaciones.application.service.ZONA_LIMA
import com.security.modules.operaciones.domain.port.out.GeneradorPdfPort
import com.security.modules.operaciones.domain.port.out.OperacionConsultaPort
import com.security.modules.operaciones.domain.port.out.PiePdf
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** El PDF generado con su codigo de verificacion y la huella del contenido. */
class PdfOperacion(val bytes: ByteArray, val codigoVerificacion: String, val huella: String)

/**
 * PDF de una pagina (RO 9). La huella es el SHA-256 del contenido del registro; el codigo de verificacion es propio de
 * cada PDF (sale de la huella, la fecha-hora, la cuenta y el registro). Cada PDF queda anotado en el historial (DESCARGA)
 * con su codigo y su huella.
 */
@Service
class ExportarOperacionPdfUseCase(
    private val operacionConsultaPort: OperacionConsultaPort,
    private val generadorPdfPort: GeneradorPdfPort,
    private val historial: HistorialOperacionService? = null
) {
    fun ejecutar(id: Long, autor: AutorEvento? = null): PdfOperacion {
        require(id > 0) {
            "El ID de la operación debe ser mayor que cero"
        }

        val operacion = operacionConsultaPort.obtenerParaExportacion(id)
            ?: throw OperacionNoEncontradaException(id)

        val ahora = LocalDateTime.now(ZONA_LIMA).withNano(0)
        val usuario = autor?.usuario ?: operacion.registro.usuario
        val codigo = codigoVerificacion(operacion.huella, ahora, usuario, id)
        val bytes = generadorPdfPort.generar(operacion, PiePdf(ahora, usuario, codigo, operacion.huella))
        historial?.descarga(id, autor, ahora, codigo, operacion.huella)
        return PdfOperacion(bytes, codigo, operacion.huella)
    }

    companion object {
        fun codigoVerificacion(huella: String, cuando: LocalDateTime, usuario: String, id: Long): String {
            val h = FotoOperacion.sha256("$huella|$cuando|$usuario|$id").take(8).uppercase()
            return h.take(4) + "-" + h.drop(4)
        }
    }
}

class OperacionNoEncontradaException(id: Long) :
    RuntimeException("No existe una operación con ID $id")
