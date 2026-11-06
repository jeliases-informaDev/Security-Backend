package com.security.modules.operaciones.domain.port.out

import com.security.modules.operaciones.domain.model.OperacionExportacion
import java.time.LocalDateTime

/** Pie del PDF: cuando y quien lo genero, codigo de verificacion y huella SHA-256 del contenido. */
data class PiePdf(val generadoEn: LocalDateTime, val usuario: String, val codigoVerificacion: String, val huella: String)

interface GeneradorPdfPort {
    fun generar(operacion: OperacionExportacion, pie: PiePdf? = null): ByteArray
}
