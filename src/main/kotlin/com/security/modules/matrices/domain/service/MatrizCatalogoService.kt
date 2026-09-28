package com.security.modules.matrices.application.service

import com.security.modules.matrices.application.dto.CatalogoMatrizResponse
import com.security.modules.matrices.application.dto.CrearCatalogoMatrizRequest
import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAreaEntity
import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoAreaProcesoEntity
import com.security.modules.matrices.infrastructure.persistence.entity.MatrizRiesgoProcesoEntity
import com.security.modules.matrices.infrastructure.persistence.repository.MatrizRiesgoAreaProcesoRepository
import com.security.modules.matrices.infrastructure.persistence.repository.MatrizRiesgoAreaRepository
import com.security.modules.matrices.infrastructure.persistence.repository.MatrizRiesgoProcesoRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class MatrizCatalogoService(
    private val usuarioRepository: UsuarioRepository,
    private val areaRepository: MatrizRiesgoAreaRepository,
    private val procesoRepository: MatrizRiesgoProcesoRepository,
    private val areaProcesoRepository: MatrizRiesgoAreaProcesoRepository
) {

    @Transactional
    fun crearArea(
        username: String,
        request: CrearCatalogoMatrizRequest
    ): CatalogoMatrizResponse {

        val usuario = obtenerUsuario(username)
        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException("Usuario inválido")

        val nombre = request.nombre.trim()

        if (
            areaRepository.existsByUsuario_IdAndNombreIgnoreCase(
                usuarioId,
                nombre
            )
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Ya existe un área con ese nombre"
            )
        }

        val area = areaRepository.save(
            MatrizRiesgoAreaEntity(
                usuario = usuario,
                nombre = nombre
            )
        )

        return CatalogoMatrizResponse(
            id = area.id!!,
            nombre = area.nombre
        )
    }

    @Transactional(readOnly = true)
    fun listarAreas(
        username: String
    ): List<CatalogoMatrizResponse> {

        val usuario = obtenerUsuario(username)
        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException("Usuario inválido")

        return areaRepository
            .findAllByUsuario_IdAndActivoTrueOrderByNombreAsc(
                usuarioId
            )
            .map {
                CatalogoMatrizResponse(
                    id = it.id!!,
                    nombre = it.nombre
                )
            }
    }

    @Transactional
    fun crearProceso(
        username: String,
        request: CrearCatalogoMatrizRequest
    ): CatalogoMatrizResponse {

        val usuario = obtenerUsuario(username)
        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException("Usuario inválido")

        val nombre = request.nombre.trim()

        if (
            procesoRepository.existsByUsuario_IdAndNombreIgnoreCase(
                usuarioId,
                nombre
            )
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Ya existe un proceso con ese nombre"
            )
        }

        val proceso = procesoRepository.save(
            MatrizRiesgoProcesoEntity(
                usuario = usuario,
                nombre = nombre
            )
        )

        return CatalogoMatrizResponse(
            id = proceso.id!!,
            nombre = proceso.nombre
        )
    }

    @Transactional(readOnly = true)
    fun listarProcesos(
        username: String
    ): List<CatalogoMatrizResponse> {

        val usuario = obtenerUsuario(username)
        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException("Usuario inválido")

        return procesoRepository
            .findAllByUsuario_IdAndActivoTrueOrderByNombreAsc(
                usuarioId
            )
            .map {
                CatalogoMatrizResponse(
                    id = it.id!!,
                    nombre = it.nombre
                )
            }
    }

    @Transactional
    fun vincularProceso(
        username: String,
        areaId: Int,
        procesoId: Int
    ) {

        val usuario = obtenerUsuario(username)
        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException("Usuario inválido")

        val area =
            areaRepository.findByIdAndUsuario_IdAndActivoTrue(
                areaId,
                usuarioId
            )
                ?: throw ResourceNotFoundException(
                    "Área no encontrada"
                )

        val proceso =
            procesoRepository.findByIdAndUsuario_IdAndActivoTrue(
                procesoId,
                usuarioId
            )
                ?: throw ResourceNotFoundException(
                    "Proceso no encontrado"
                )

        if (
            areaProcesoRepository.existsByArea_IdAndProceso_Id(
                areaId,
                procesoId
            )
        ) {
            return
        }

        areaProcesoRepository.save(
            MatrizRiesgoAreaProcesoEntity(
                area = area,
                proceso = proceso,
                usuario = usuario
            )
        )
    }

    @Transactional(readOnly = true)
    fun listarProcesosPorArea(
        username: String,
        areaId: Int
    ): List<CatalogoMatrizResponse> {

        val usuario = obtenerUsuario(username)
        val usuarioId = usuario.id
            ?: throw ResourceNotFoundException("Usuario inválido")

        areaRepository.findByIdAndUsuario_IdAndActivoTrue(
            areaId,
            usuarioId
        )
            ?: throw ResourceNotFoundException(
                "Área no encontrada"
            )

        return areaProcesoRepository
            .findAllByArea_IdAndUsuario_Id(
                areaId,
                usuarioId
            )
            .map { it.proceso }
            .filter { it.activo }
            .sortedBy { it.nombre.lowercase() }
            .map {
                CatalogoMatrizResponse(
                    id = it.id!!,
                    nombre = it.nombre
                )
            }
    }

    private fun obtenerUsuario(
        username: String
    ) =
        usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException(
                "Usuario no encontrado"
            )
}