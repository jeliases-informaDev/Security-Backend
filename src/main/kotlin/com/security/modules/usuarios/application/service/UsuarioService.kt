package com.security.modules.usuarios.application.service

import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.usuarios.dto.CambiarEmpresaRequest
import com.security.modules.usuarios.dto.CambiarRolRequest
import com.security.modules.usuarios.dto.UsuarioCreateRequest
import com.security.modules.usuarios.dto.RolResponse
import com.security.modules.usuarios.dto.UsuarioResponse
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import com.security.shared.exceptions.ConflictException
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.usuarioConSigla
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UsuarioService(
    private val usuarioRepository: UsuarioRepository,
    private val rolesRepository: RolesRepository,
    private val usuarioRolRepository: UsuarioRolRepository,
    private val passwordEncoder: PasswordEncoder,
    private val empresaService: EmpresaService
) {

    fun listar(): List<UsuarioResponse> {
        return usuarioRepository.findAllConRoles().map { it.toResponse() }
    }

    /** Solo las cuentas de la empresa de quien pide; sin empresa, ninguna. */
    fun listarDeMiEmpresa(username: String): List<UsuarioResponse> {
        val empresa = usuarioRepository.findByUsuario(username)?.empresa?.trim()?.takeIf { it.isNotEmpty() }
            ?: return emptyList()
        return usuarioRepository.findAllConRoles()
            .filter { it.empresa?.trim().equals(empresa, ignoreCase = true) }
            .map { it.toResponse() }
    }

    @Transactional
    fun crear(request: UsuarioCreateRequest): UsuarioResponse {

        // La empresa se elige de la lista de empresas; se guarda con el nombre exacto de la lista.
        val empresaElegida = empresaService.resolverEmpresaActiva(request.empresa)
        val empresa = empresaElegida.nombre
        // El usuario interno lleva la sigla de la empresa delante (hcyo.jperez): unico entre empresas.
        val nombreUsuario = empresaElegida.sigla?.let { usuarioConSigla(it, request.usuario) } ?: request.usuario.trim()

        if (usuarioRepository.existsByUsuario(nombreUsuario)) {
            throw ConflictException("El nombre de usuario ya está en uso")
        }
        if (usuarioRepository.existsByCorreo(request.correo)) {
            throw ConflictException("El correo ya está registrado")
        }

        val rol = rolesRepository.findByCodigo(request.rolCodigo)
            ?: throw ResourceNotFoundException("El rol '${request.rolCodigo}' no existe")

        val now = LocalDateTime.now()

        val usuario = Usuario(
            usuario = nombreUsuario,
            clave = passwordEncoder.encode(request.clave)!!,
            correo = request.correo,
            nombres = request.nombres,
            apePat = request.apePat,
            apeMat = request.apeMat,
            cargo = request.cargo,
            empresa = empresa,
            activo = true,
            fechaCreacion = now,
            fechaActualizacion = now
        )

        val usuarioGuardado = usuarioRepository.save(usuario)

        usuarioRolRepository.save(UsuarioRol(usuario = usuarioGuardado, rol = rol))

        usuarioGuardado.roles = mutableListOf(UsuarioRol(usuario = usuarioGuardado, rol = rol))

        return usuarioGuardado.toResponse()
    }

    @Transactional
    fun cambiarRol(usuarioId: Int, request: CambiarRolRequest): UsuarioResponse {

        val usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow { ResourceNotFoundException("Usuario no encontrado") }

        val rol = rolesRepository.findByCodigo(request.rolCodigo)
            ?: throw ResourceNotFoundException("El rol '${request.rolCodigo}' no existe")

        usuarioRolRepository.deleteByUsuarioId(usuarioId)
        usuarioRolRepository.save(UsuarioRol(usuario = usuario, rol = rol))

        usuario.roles = mutableListOf(UsuarioRol(usuario = usuario, rol = rol))

        return usuario.toResponse()
    }

    /**
     * Pasa la cuenta a otra empresa de la lista. Los registros de operaciones que la cuenta ya hizo
     * van con ella (el modulo de operaciones los muestra segun la empresa de su usuario).
     */
    @Transactional
    fun cambiarEmpresa(usuarioId: Int, request: CambiarEmpresaRequest): UsuarioResponse {

        val usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow { ResourceNotFoundException("Usuario no encontrado") }

        usuario.empresa = empresaService.resolverActiva(request.empresa)
        usuario.fechaActualizacion = LocalDateTime.now()

        return usuarioRepository.save(usuario).toResponse()
    }

    fun listarRoles(): List<RolResponse> {
        return rolesRepository.findAll()
            .filter { it.activo }
            .sortedBy { it.nombre }
            .map { RolResponse(codigo = it.codigo, nombre = it.nombre) }
    }

    @Transactional
    fun cambiarEstado(usuarioId: Int, activo: Boolean): UsuarioResponse {

        val usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow { ResourceNotFoundException("Usuario no encontrado") }

        usuario.activo = activo
        usuario.fechaActualizacion = LocalDateTime.now()

        val usuarioActualizado = usuarioRepository.save(usuario)

        return usuarioRepository.findByUsuario(usuarioActualizado.usuario)?.toResponse()
            ?: usuarioActualizado.toResponse()
    }

    private fun Usuario.toResponse(): UsuarioResponse {
        val rol = this.roles.firstOrNull()?.rol
        return UsuarioResponse(
            id = this.id!!,
            usuario = this.usuario,
            correo = this.correo,
            nombreCompleto = listOfNotNull(this.nombres, this.apePat, this.apeMat).joinToString(" "),
            cargo = this.cargo,
            empresa = this.empresa,
            rolCodigo = rol?.codigo,
            rolNombre = rol?.nombre,
            activo = this.activo
        )
    }
}
