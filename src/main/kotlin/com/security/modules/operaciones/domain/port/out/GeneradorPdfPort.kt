package com.security.modules.operaciones.domain.port.out

import com.security.modules.operaciones.domain.model.OperacionExportacion

interface GeneradorPdfPort {
    fun generar(operacion: OperacionExportacion): ByteArray
}
