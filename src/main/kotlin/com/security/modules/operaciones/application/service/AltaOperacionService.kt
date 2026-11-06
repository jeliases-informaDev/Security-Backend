package com.security.modules.operaciones.application.service

import com.security.modules.licencias.CobroAcciones
import com.security.modules.licencias.conCobro
import com.security.modules.licencias.dominio.TIPO_OPERACION_REGISTRAR
import com.security.modules.operaciones.domain.Persona
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.dto.RegistroCreadoResponse
import com.security.modules.operaciones.dto.UsuarioAsignableResponse
import com.security.modules.operaciones.infraestructure.NumeradorRegistros
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.tuempresa.operaciones.dto.RegistrarOperacionRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

/**
 * Alta de un registro (RO 2 → RO 4). El servidor pone el numero RO-AAAA-NNNNNN (correlativo por empresa y año de Lima),
 * la fecha y la hora de registro, la empresa (la de la cuenta dueña) y el responsable (quien registra). Cobra
 * operacion_registrar; si el guardado falla despues de cobrar, se anula y el numero no se consume.
 */
@Service
class AltaOperacionService(
    private val operacionRepository: OperacionRepository,
    private val usuarioRepository: UsuarioRepository,
    private val catalogos: CatalogosRegistro,
    private val numerador: NumeradorRegistros,
    private val historial: HistorialOperacionService,
    private val cobro: CobroAcciones? = null,
    private val umbrales: UmbralService? = null,
    private val configuracion: ConfiguracionOperacionesService? = null
) {

    /** `request.registro` ya viene resuelto por AccesoEmpresaService.registroDelActor (cuenta dueña y empresa). */
    @Transactional
    fun registrar(request: RegistrarOperacionRequest, autor: AutorEvento?): RegistroCreadoResponse {
        val nombreCuenta = request.registro.usuario?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la cuenta que registra")
        val cuenta = operacionRepository.buscarPorUsuario(nombreCuenta)
            ?: throw ResourceNotFoundException("Usuario no encontrado")
        val usuarioId = request.registro.usuarioId?.trim()?.takeIf { it.isNotEmpty() }
        if (usuarioId != null && usuarioId.toIntOrNull() != cuenta.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID no corresponde al usuario indicado")
        }
        // El registro queda en la empresa actual de la cuenta dueña (no en el texto que mande el cliente).
        val empresa = cuenta.empresa?.trim()?.takeIf { it.isNotEmpty() }
            ?: request.registro.empresa?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta no tiene una empresa asignada")

        val cat = catalogos.cargar()
        val oficinas = configuracion?.de(empresa, cat)?.oficinas.orEmpty()
        val formulario = ValidadorOperacion(cat, oficinas = oficinas).validar(
            request.registro.oficina, request.personaFisica, request.personaRepresentada, request.operacion, request.beneficiarios
        )
        val ahora = LocalDateTime.now(ZONA_LIMA).withNano(0)
        val responsable = usuarioRepository.findById(cuenta.id).orElse(null)
        // Verificacion de umbral: informativa, nunca bloquea; queda guardada con el registro
        val verificacion = umbrales?.evaluar(empresa, formulario.operacion.tipoOperacion, formulario.operacion.moneda,
            formulario.monto, formulario.fechaOperacion, cat)

        val guardado = cobro.conCobro(TIPO_OPERACION_REGISTRAR, "registro de operacion") {
            val registro = operacionRepository.saveAndFlush(
                RegistroOperacionesEntity(
                    numeroRegistro = numerador.siguiente(empresa, ahora.year),
                    idUsuario = cuenta.id,
                    empresa = empresa,
                    oficina = formulario.oficina,
                    fechaRegistro = ahora.toLocalDate(),
                    fechaCreacion = ahora,
                    personaFisica = EscritorOperacion.persona(Persona(), formulario.personaFisica),
                    personaRepresentada = formulario.personaRepresentada?.let { EscritorOperacion.persona(Persona(), it) },
                    operacion = EscritorOperacion.nuevaOperacion(formulario).also { o ->
                        if (verificacion != null) umbrales?.aplicar(o, verificacion, ahora)
                    },
                    idUsuarioResponsable = cuenta.id,
                    beneficiarios = EscritorOperacion.beneficiarios(formulario)
                )
            )
            historial.creacion(requireNotNull(registro.id) { "No se genero el ID del registro" }, autor, ahora)
            if (verificacion != null) {
                historial.anotar(requireNotNull(registro.id), com.security.modules.operaciones.domain.EventoOperacionEntity.UMBRAL,
                    "Verificación de umbral", UmbralService.detalleEvento(verificacion), null, ahora)
            }
            registro
        }
        return RegistroCreadoResponse(
            id = requireNotNull(guardado.id),
            numeroRegistro = requireNotNull(guardado.numeroRegistro),
            fechaRegistro = guardado.fechaRegistro,
            creadoEn = ahora,
            estado = ESTADO_PENDIENTE,
            responsable = UsuarioAsignableResponse(cuenta.id, responsable?.let { nombreCompleto(it) } ?: cuenta.usuario),
            umbral = verificacion
        )
    }
}
