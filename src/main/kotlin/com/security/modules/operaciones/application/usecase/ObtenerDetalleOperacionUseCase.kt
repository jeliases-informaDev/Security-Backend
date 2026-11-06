package com.security.modules.operaciones.application.usecase

import com.security.modules.operaciones.application.service.CodigosOperaciones
import com.security.modules.operaciones.application.service.ESTADO_PENDIENTE
import com.security.modules.operaciones.application.service.HistorialOperacionService
import com.security.modules.operaciones.application.service.nombreCompleto
import com.security.modules.operaciones.domain.port.out.OperacionConsultaPort
import com.security.modules.operaciones.dto.DetalleOperacionResponse
import com.security.modules.operaciones.dto.UsuarioAsignableResponse
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.repositories.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** Detalle (RO 5): los datos del registro, su estado, su responsable, cuanto se conserva y los 2 eventos mas recientes. */
@Service
class ObtenerDetalleOperacionUseCase(
    private val consulta: OperacionConsultaPort,
    private val operacionRepository: OperacionRepository? = null,
    private val historial: HistorialOperacionService? = null,
    private val usuarioRepository: UsuarioRepository? = null
) {
    @Transactional(readOnly = true)
    fun ejecutar(id: Long): DetalleOperacionResponse {
        if (id <= 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID debe ser mayor que cero")
        val datos = consulta.obtenerParaExportacion(id) ?: throw CodigosOperaciones.noEncontrada(id)
        val registro = operacionRepository?.findById(id)?.orElse(null)
        val responsable = datos.idUsuarioResponsable?.let { rid ->
            val u = usuarioRepository?.findById(rid)?.orElse(null)
            UsuarioAsignableResponse(rid, u?.let { nombreCompleto(it) } ?: rid.toString())
        }
        return DetalleOperacionResponse(
            id = datos.id,
            numeroRegistro = datos.codigoOperacion,
            registro = datos.registro,
            personaFisica = datos.personaFisica,
            personaRepresentada = datos.personaRepresentada,
            operacion = datos.operacion,
            beneficiarios = datos.beneficiarios,
            estado = ESTADO_PENDIENTE,
            responsable = responsable,
            conservarHasta = datos.operacion.fechaOperacion.plusYears(5),
            historialReciente = registro?.let { historial?.eventos(it)?.take(2) }.orEmpty()
        )
    }
}
