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
import java.util.UUID

@Service
class OperacionService(
    private val operacionRepository: OperacionRepository,
    private val usuarioRepository: UsuarioRepository
) {

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
        val guardado = operacionRepository.saveAndFlush(RegistroOperacionesEntity(
            numeroRegistro = "OP-${UUID.randomUUID()}",
            idUsuario = usuario.id,
            empresa = request.registro.empresa,
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

    fun obtenerResumenPaginado(page: Int, size: Int, username: String): ListarOperacionesResponse {
        // 1. Buscamos el usuario en tu base de datos usando el método que ya soporta tu repositorio
        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw RuntimeException("Usuario no encontrado")

        // 2. Obtenemos el nombre de la empresa directamente de la entidad Usuario
        val nombreEmpresa = usuario.empresa ?: ""

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
                fecha = proj.fecha
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
