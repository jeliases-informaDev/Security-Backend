package com.security.modules.denuncias.application.usecase

import com.security.modules.denuncias.application.dto.DenunciaDetalleResponse
import com.security.modules.denuncias.application.service.AutorizacionDenunciaService
import com.security.modules.denuncias.application.service.CodigosDenuncia
import com.security.modules.denuncias.application.service.DenunciaException
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaHistorialEntity
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaHistorialRepository
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.shared.datos.entities.Usuario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

/**
 * POST /api/denuncias/{id}/derivar-oficial: deriva la denuncia al oficial de cumplimiento (reservado). Puede hacerlo quien
 * ya gestiona esa denuncia. Una vez derivada no se des-deriva; repetir no cambia nada (idempotente). En el historial queda
 * una linea neutra, sin la cuenta que lo hizo, para no revelar nada a quien ve el historial.
 */
@Service
class DerivarDenunciaOficialUseCase(
    private val denunciaRepository: DenunciaRepository,
    private val historialRepository: DenunciaHistorialRepository,
    private val obtenerDenunciaUseCase: ObtenerDenunciaUseCase,
    private val autorizacion: AutorizacionDenunciaService
) {
    companion object {
        const val LINEA_HISTORIAL = "Derivada para revisión reservada"
        const val MOTIVO_MAXIMO = 500
    }

    @Transactional
    fun ejecutar(id: Int, motivo: String?, usuario: Usuario): DenunciaDetalleResponse {
        val denuncia = denunciaRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "La denuncia no existe.")
        }
        autorizacion.validarAccesoDenuncia(usuario, denuncia)

        if (!denuncia.derivadaAlOficial) {
            val texto = motivo?.trim()?.takeIf { it.isNotEmpty() }
            if (texto != null && texto.length > MOTIVO_MAXIMO) {
                throw DenunciaException(HttpStatus.BAD_REQUEST, CodigosDenuncia.MOTIVO_LARGO,
                    "El motivo de la derivación puede tener hasta $MOTIVO_MAXIMO caracteres.")
            }
            val ahora = LocalDateTime.now()
            denuncia.derivadaAlOficial = true
            denuncia.fechaDerivacion = ahora
            denuncia.derivadaPorUsuario = usuario
            denuncia.motivoDerivacion = texto
            denuncia.fechaActualizacion = ahora
            denunciaRepository.save(denuncia)
            historialRepository.save(
                DenunciaHistorialEntity(
                    denuncia = denuncia, estadoAnterior = denuncia.estado, estadoNuevo = denuncia.estado,
                    comentario = LINEA_HISTORIAL, usuario = null, fechaCreacion = ahora
                )
            )
        }
        return obtenerDenunciaUseCase.ejecutar(id, usuario)
    }
}
