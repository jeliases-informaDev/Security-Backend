package com.security.modules.empresas.application.service

import com.security.modules.empresas.dto.EmpresaCreateRequest
import com.security.modules.empresas.dto.EmpresaResponse
import com.security.modules.empresas.dto.EmpresaUpdateRequest
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ConflictException
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@Service
class EmpresaService(
    private val empresaRepository: EmpresaRepository,
    private val usuarioRepository: UsuarioRepository
) {

    @Transactional(readOnly = true)
    fun listar(): List<EmpresaResponse> {
        val cuentas = usuarioRepository.contarCuentasPorEmpresa().associate { it.nombre to it.cuentas }
        return empresaRepository.findAllByOrderByNombreAsc()
            .map { it.toResponse(cuentas[clave(it.nombre)] ?: 0L) }
    }

    @Transactional
    fun crear(request: EmpresaCreateRequest): EmpresaResponse {
        val nombre = request.nombre.trim()
        if (nombre.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de la empresa es obligatorio")
        }
        if (nombre.length > MAX_NOMBRE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de la empresa admite hasta $MAX_NOMBRE caracteres")
        }
        val ruc = normalizarRuc(request.ruc)

        if (empresaRepository.existsByNombreIgnoreCase(nombre)) {
            throw ConflictException("Ya existe una empresa con ese nombre")
        }
        if (ruc != null && empresaRepository.findByRuc(ruc) != null) {
            throw ConflictException("Ya existe una empresa con ese RUC")
        }

        val ahora = LocalDateTime.now()
        val guardada = empresaRepository.save(
            Empresa(nombre = nombre, ruc = ruc, activa = true, fechaCreacion = ahora, fechaActualizacion = ahora)
        )
        return guardada.toResponse(0L)
    }

    @Transactional
    fun actualizar(id: Int, request: EmpresaUpdateRequest): EmpresaResponse {
        val empresa = buscar(id)
        val ruc = normalizarRuc(request.ruc)

        if (ruc != null) {
            val otra = empresaRepository.findByRuc(ruc)
            if (otra != null && otra.id != empresa.id) {
                throw ConflictException("Ya existe una empresa con ese RUC")
            }
        }

        empresa.ruc = ruc
        empresa.fechaActualizacion = LocalDateTime.now()
        return empresaRepository.save(empresa).toResponse(cuentasDe(empresa))
    }

    @Transactional
    fun cambiarEstado(id: Int, activa: Boolean): EmpresaResponse {
        val empresa = buscar(id)
        empresa.activa = activa
        empresa.fechaActualizacion = LocalDateTime.now()
        return empresaRepository.save(empresa).toResponse(cuentasDe(empresa))
    }

    /**
     * Devuelve el nombre exacto con el que la empresa esta en la lista, o lanza 400 si no se indico,
     * no existe o esta inactiva. Se usa al crear una cuenta o cambiarle la empresa, para que el
     * nombre quede siempre escrito igual.
     */
    @Transactional(readOnly = true)
    fun resolverActiva(nombre: String?): String {
        val buscado = nombre?.trim().orEmpty()
        if (buscado.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La empresa es obligatoria")
        }
        val empresa = empresaRepository.findByNombreIgnoreCase(buscado)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La empresa '$buscado' no está en la lista de empresas")
        if (!empresa.activa) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La empresa '${empresa.nombre}' está inactiva")
        }
        return empresa.nombre
    }

    private fun buscar(id: Int): Empresa =
        empresaRepository.findById(id).orElseThrow { ResourceNotFoundException("Empresa no encontrada") }

    private fun cuentasDe(empresa: Empresa): Long =
        usuarioRepository.contarCuentasPorEmpresa().firstOrNull { it.nombre == clave(empresa.nombre) }?.cuentas ?: 0L

    private fun normalizarRuc(ruc: String?): String? {
        val limpio = ruc?.trim().orEmpty()
        if (limpio.isEmpty()) return null
        if (!RUC.matches(limpio)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El RUC debe tener 11 dígitos")
        }
        return limpio
    }

    private fun clave(nombre: String) = nombre.trim().lowercase()

    private fun Empresa.toResponse(cuentas: Long) = EmpresaResponse(
        id = this.id!!,
        nombre = this.nombre,
        ruc = this.ruc,
        activa = this.activa,
        cuentas = cuentas
    )

    companion object {
        private const val MAX_NOMBRE = 255
        private val RUC = Regex("^\\d{11}$")
    }
}
