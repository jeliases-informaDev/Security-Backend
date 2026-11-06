package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.domain.BeneficiarioEntity
import com.security.modules.operaciones.domain.Persona
import com.security.modules.operaciones.dto.ActualizarOperacionRequest
import com.security.modules.operaciones.dto.CambioResponse
import com.security.modules.operaciones.dto.EdicionOperacionResponse
import com.security.modules.operaciones.dto.ResponsablePosibleResponse
import com.security.modules.operaciones.dto.UsuarioAsignableResponse
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.repositories.UsuarioRepository
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Editar con motivo (RO 6). Reemplaza el formulario completo, calcula los cambios campo por campo comparando lo legible
 * antes y despues, y los anota en el historial con el motivo. No toca el numero, la fecha y hora de registro, la cuenta
 * dueña, la empresa ni el responsable. Nada se borra: el valor anterior queda en el historial.
 * El acceso (registro de la propia empresa) lo comprueba el controlador antes de llamar.
 */
@Service
class EdicionOperacionService(
    private val repository: OperacionRepository,
    private val catalogos: CatalogosRegistro,
    private val historial: HistorialOperacionService,
    private val em: EntityManager
) {

    @Transactional
    fun editar(id: Long, request: ActualizarOperacionRequest, autor: AutorEvento?): EdicionOperacionResponse {
        if (id <= 0) throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.DATOS_INVALIDOS, "El ID debe ser mayor que cero")
        val motivo = request.motivo?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw motivoInvalido("Escribe el motivo del cambio.")
        if (motivo.length > MOTIVO_MAXIMO) throw motivoInvalido("El motivo admite hasta $MOTIVO_MAXIMO caracteres.")

        val registro = repository.findById(id).orElse(null) ?: throw CodigosOperaciones.noEncontrada(id)
        val cat = catalogos.cargar()
        val antes = FotoOperacion.campos(registro, cat)
        val f = ValidadorOperacion(cat, FotoOperacion.valores(antes)).validar(
            request.registro.oficina, request.personaFisica, request.personaRepresentada, request.operacion, request.beneficiarios
        )

        registro.oficina = f.oficina
        EscritorOperacion.persona(registro.personaFisica, f.personaFisica)
        val representadaAnterior = registro.personaRepresentada
        registro.personaRepresentada = f.personaRepresentada?.let { dto ->
            (representadaAnterior ?: Persona()).also {
                EscritorOperacion.persona(it, dto)
                if (representadaAnterior == null) em.persist(it)
            }
        }
        EscritorOperacion.aplicar(registro.operacion, f)
        // Se reemplaza la lista completa de beneficiarios (orphanRemoval borra las filas de la lista vieja; su contenido
        // queda en el historial como «antes»).
        val nuevos = EscritorOperacion.beneficiarios(f)
        registro.beneficiarios.clear()
        nuevos.forEach { b: BeneficiarioEntity ->
            em.persist(b)
            registro.beneficiarios.add(b)
        }

        val cambios = FotoOperacion.diferencias(antes, FotoOperacion.campos(registro, cat))
        if (cambios.isEmpty()) {
            // La transaccion se deshace: no queda nada escrito.
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.SIN_CAMBIOS, "No hay cambios que guardar.")
        }
        repository.flush()
        if (representadaAnterior != null && f.personaRepresentada == null) {
            em.remove(representadaAnterior)
            repository.flush()
        }
        historial.edicion(id, autor, LocalDateTime.now(ZONA_LIMA).withNano(0), motivo, cambios)
        return EdicionOperacionResponse(
            id = id,
            numeroRegistro = registro.numeroRegistro.orEmpty(),
            mensaje = "Operacion actualizada correctamente",
            cambios = cambios.map { CambioResponse(it.campo, it.etiqueta, it.antes, it.despues) }
        )
    }

    private fun motivoInvalido(mensaje: String) =
        OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.MOTIVO_INVALIDO, mensaje, mapOf("motivo" to mensaje))

    companion object {
        const val MOTIVO_MAXIMO = 300
    }
}

/**
 * Responsable de un registro (RO 8): solo un usuario activo de la empresa del registro (para el administrador universal,
 * la del registro, no la suya). Cada cambio va al historial. No cobra tokens.
 */
@Service
class ResponsableOperacionService(
    private val repository: OperacionRepository,
    private val usuarioRepository: UsuarioRepository,
    private val acceso: AccesoEmpresaService,
    private val historial: HistorialOperacionService
) {

    @Transactional(readOnly = true)
    fun posibles(id: Long): List<ResponsablePosibleResponse> {
        val registro = repository.findById(id).orElse(null) ?: throw CodigosOperaciones.noEncontrada(id)
        val empresa = acceso.empresaDelRegistro(registro)
        return usuarioRepository.findAllByEmpresaOrderByNombresAscApePatAscIdAsc(empresa)
            .filter { it.activo }
            .map { ResponsablePosibleResponse(requireNotNull(it.id), nombreCompleto(it), it.id == registro.idUsuarioResponsable) }
    }

    @Transactional
    fun cambiar(id: Long, usuarioId: Int, autor: AutorEvento?): com.security.modules.operaciones.dto.AsignarResponsableResponse {
        val registro = repository.findById(id).orElse(null) ?: throw CodigosOperaciones.noEncontrada(id)
        val empresa = acceso.empresaDelRegistro(registro)
        val nuevo = usuarioRepository.findById(usuarioId).orElse(null)
        if (nuevo == null || !nuevo.activo || !empresa.equals(nuevo.empresa?.trim(), ignoreCase = true)) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.RESPONSABLE_INVALIDO,
                "Elige un usuario activo de la empresa del registro.")
        }
        if (registro.idUsuarioResponsable == usuarioId) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.SIN_CAMBIOS, "Ese usuario ya es el responsable.")
        }
        val anterior = registro.idUsuarioResponsable?.let { usuarioRepository.findById(it).orElse(null) }
        registro.idUsuarioResponsable = usuarioId
        repository.flush()
        val nombre = nombreCompleto(nuevo)
        historial.responsable(id, autor, LocalDateTime.now(ZONA_LIMA).withNano(0), anterior?.let { nombreCompleto(it) }, nombre)
        return com.security.modules.operaciones.dto.AsignarResponsableResponse(id, UsuarioAsignableResponse(usuarioId, nombre))
    }
}
