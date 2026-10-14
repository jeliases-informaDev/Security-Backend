
package com.security.modules.operaciones.domain.port.out

import com.security.modules.operaciones.domain.model.OperacionExportacion

interface OperacionConsultaPort {
    fun obtenerParaExportacion(id: Long): OperacionExportacion?
}
