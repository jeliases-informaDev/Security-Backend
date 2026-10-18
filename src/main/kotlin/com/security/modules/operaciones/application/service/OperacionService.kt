package com.security.modules.operaciones.application.service
import com.security.modules.operaciones.dto.RegistrarOperacionResponse

import com.security.modules.operaciones.dto.ListarOperacionesResponse
import com.security.modules.operaciones.dto.MostrarUsuarioResponse
import com.security.modules.operaciones.dto.OperacionItemDto
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import com.security.modules.operaciones.domain.*
import com.security.shared.datos.repositories.UsuarioRepository
import com.tuempresa.operaciones.dto.RegistrarOperacionRequest
import com.tuempresa.operaciones.dto.PersonaDto
import com.security.modules.operaciones.dto.*
import com.security.shared.datos.entities.Usuario

@Service
class OperacionService(
    private val operacionRepository: OperacionRepository,
    private val usuarioRepository: UsuarioRepository
) {

    private fun empresaActual(username: String): String {
        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no disponible")
        if (!usuario.activo || usuario.empresa.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario sin empresa activa")
        }
        return requireNotNull(usuario.empresa)
    }

    private fun Usuario.asignable() = UsuarioAsignableResponse(
        id = requireNotNull(id),
        nombreCompleto = listOfNotNull(nombres, apePat, apeMat)
            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ").ifBlank { usuario }
    )

    @Transactional(readOnly = true)
    fun listarUsuariosAsignables(username: String): List<UsuarioAsignableResponse> =
        usuarioRepository.findAllByEmpresaAndActivoTrueOrderByNombresAscApePatAsc(empresaActual(username))
            .map { it.asignable() }

    @Transactional(readOnly = true)
    fun listarUsuariosPorEmpresa(empresa: String): List<UsuarioEmpresaResponse> {
        if (empresa.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La empresa es obligatoria")
        }
        return usuarioRepository.findAllByEmpresaOrderByNombresAscApePatAscIdAsc(empresa.trim()).map {
            UsuarioEmpresaResponse(
                id = requireNotNull(it.id), usuario = it.usuario,
                nombreCompleto = it.asignable().nombreCompleto,
                empresa = requireNotNull(it.empresa), activo = it.activo
            )
        }
    }

    @Transactional
    fun cambiarUsuarioOperacion(id: Long, usuarioId: Int): CambiarUsuarioOperacionResponse {
        if (id <= 0 || usuarioId <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Los IDs deben ser positivos")
        }
        val registro = operacionRepository.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Operacion no encontrada")
        val anterior = usuarioRepository.findById(registro.idUsuario).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Usuario actual del registro no encontrado")
        val nuevo = usuarioRepository.findById(usuarioId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario seleccionado no encontrado")
        val empresa = anterior.empresa
        if (empresa.isNullOrBlank() || nuevo.empresa.isNullOrBlank() ||
            !empresa.trim().equals(nuevo.empresa?.trim(), ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario seleccionado debe pertenecer a la misma empresa")
        }
        if (!nuevo.activo) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario seleccionado esta inactivo")
        }
        if (registro.idUsuario != usuarioId &&
            operacionRepository.cambiarUsuario(id, registro.idUsuario, usuarioId) != 1) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "La operacion cambio; actualice la lista y vuelva a intentar")
        }
        return CambiarUsuarioOperacionResponse(id, usuarioId, nuevo.asignable().nombreCompleto)
    }

    @Transactional
    fun asignarResponsable(id: Long, usuarioId: Int, username: String): AsignarResponsableResponse {
        val empresa = empresaActual(username)
        val registro = operacionRepository.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Operacion no encontrada")
        if (registro.empresa != empresa) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Operacion no encontrada")
        }
        val responsable = usuarioRepository.findById(usuarioId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsable no disponible")
        if (!responsable.activo || responsable.empresa != empresa) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsable no disponible en la empresa")
        }
        registro.idUsuarioResponsable = usuarioId
        operacionRepository.flush()
        return AsignarResponsableResponse(id, responsable.asignable())
    }

    @Transactional
    fun registrar(request: RegistrarOperacionRequest): RegistrarOperacionResponse {
        val usuario = operacionRepository.buscarPorUsuario(request.registro.usuario)
            ?: throw ResourceNotFoundException("Usuario no encontrado")
        if (request.registro.usuarioId.toIntOrNull() != usuario.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID no corresponde al usuario indicado")
        }
        val datos = request.operacion
        val monto = try {
            datos.monto.setScale(2, java.math.RoundingMode.UNNECESSARY)
        } catch (_: ArithmeticException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto admite hasta dos decimales")
        }
        if (monto.signum() < 0 || monto.precision() > 15) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Monto fuera del rango permitido")
        }
        // El registro queda en la empresa actual de la cuenta duena (no en el texto que mande el cliente).
        val empresa = usuario.empresa?.trim()?.takeIf { it.isNotEmpty() } ?: request.registro.empresa
        val guardado = operacionRepository.saveAndFlush(RegistroOperacionesEntity(
            numeroRegistro = request.numeroRegistro.trim(),
            idUsuario = usuario.id,
            empresa = empresa,
            oficina = request.registro.oficina,
            fechaRegistro = request.registro.fechaRegistro,
            personaFisica = request.personaFisica.toEntity(),
            personaRepresentada = request.personaRepresentada?.toEntity(),
            operacion = Operacion(
                fechaOperacion = datos.fechaOperacion, lugar = datos.lugar,
                modalidadPago = datos.modalidadPago, moneda = datos.moneda,
                monto = monto, tipoOperacion = datos.tipoOperacion,
                cuenta1 = datos.cuenta1, cuenta2 = datos.cuenta2, cuenta3 = datos.cuenta3
            ),
            beneficiarios = request.beneficiarios.map {
                BeneficiarioEntity(nombre = it.nombre, tipoDocumento = it.tipoDocumento,
                    numeroDocumento = it.numeroDocumento, fechaNacimiento = it.fechaNacimiento)
            }
        ))
        return RegistrarOperacionResponse(
            id = requireNotNull(guardado.id) { "No se genero el ID del registro" },
            numeroRegistro = requireNotNull(guardado.numeroRegistro)
        )
    }

    private fun PersonaDto.toEntity() = Persona().also {
        it.tipoDocumento = tipoDocumento
        it.numeroDocumento = numeroDocumento
        it.nombres = nombres
        it.apellidos = apellidos
        it.fechaNacimiento = fechaNacimiento
        it.nacionalidad = nacionalidad
        it.telefono = telefono
        it.profesionOcupacion = ocupacion
        it.domicilio = domicilio
        it.codigoPostal = codigoPostal
        it.departamento = departamento
        it.provincia = provincia
        it.distrito = distrito
    }

    fun obtenerResumenPaginado(page: Int, size: Int, empresa: String): ListarOperacionesResponse {
        val nombreEmpresa = empresa.trim()
        if (nombreEmpresa.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La empresa es obligatoria")
        }
        if (page < 0 || size <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page debe ser mayor o igual a 0 y size mayor a 0")
        }

        // 3. Configuramos la paginación y llamamos al repositorio filtrando por empresa
        val pageable = PageRequest.of(page, size)
        val resultadoPage = operacionRepository.listarResumenPorEmpresa(nombreEmpresa, pageable)

        // 4. Mapeamos la proyección al DTO de respuesta
        val items = resultadoPage.content.map { proj ->
            OperacionItemDto(
                id = proj.id,
                numeroRegistro = proj.numeroRegistro,
                personaFisica = proj.personaFisica,
                personaNombre = proj.personaNombre,
                oficina = proj.oficina,
                fecha = proj.fecha,
                ///responsable = proj.responsableId?.let { UsuarioAsignableResponse(it, proj.responsableNombre.orEmpty()) }
            )
        }

        return ListarOperacionesResponse(
            content = items,
            totalPages = resultadoPage.totalPages,
            totalElements = resultadoPage.totalElements,
            currentPage = resultadoPage.number
        )
    }

    fun obtenerUsuarioActual(usuario: String): MostrarUsuarioResponse {

        val usuarioEncontrado = operacionRepository.buscarPorUsuario(usuario)
            ?: throw ResourceNotFoundException("Usuario no encontrado")

        return MostrarUsuarioResponse(
            id = usuarioEncontrado.id.toString(),
            usuario = usuarioEncontrado.usuario,
            empresa = usuarioEncontrado.empresa ?: ""
        )
    }
}
