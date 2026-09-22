package com.security.modules.listas.application.service

import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.dto.ManchaResponse
import com.security.modules.listas.dto.ResultadoBusquedaResponse
import com.security.modules.listas.infrastructure.EntidadRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ListaNegativaService(
    private val entidadRepository: EntidadRepository
) {

    @Transactional(readOnly = true)
    fun buscar(
        documento: String?,
        nombres: String?,
        apellidoPaterno: String?,
        apellidoMaterno: String?
    ): List<ResultadoBusquedaResponse> {

        val criterios = listOf(documento, nombres, apellidoPaterno, apellidoMaterno)
        if (criterios.all { it.isNullOrBlank() }) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Debe indicar al menos un criterio de búsqueda (documento, nombres o apellidos)"
            )
        }

        return entidadRepository
            .buscar(
                documento = documento?.trim()?.ifBlank { null },
                nombres = nombres?.trim()?.ifBlank { null },
                apellidoPaterno = apellidoPaterno?.trim()?.ifBlank { null },
                apellidoMaterno = apellidoMaterno?.trim()?.ifBlank { null }
            )
            .map { it.toResponse() }
    }

    private fun Entidad.toResponse(): ResultadoBusquedaResponse {
        val nombreCompleto = when {
            this.personaNatural != null -> listOfNotNull(
                this.personaNatural?.nombre,
                this.personaNatural?.segundoNombre,
                this.personaNatural?.apePat,
                this.personaNatural?.apeMat
            ).joinToString(" ")
            this.personaJuridica != null -> this.personaJuridica?.razonSocial ?: ""
            else -> ""
        }

        val manchas = this.manchas
            .sortedByDescending { it.fechaRegistro }
            .map { mancha ->
                ManchaResponse(
                    id = mancha.id!!,
                    tipoListaCodigo = mancha.tipoLista.codigo,
                    tipoListaNombre = mancha.tipoLista.nombre,
                    descripcion = mancha.descripcion,
                    link = mancha.link,
                    fechaRegistro = mancha.fechaRegistro,
                    fechaHasta = mancha.fechaHasta,
                    institucion = mancha.institucion,
                    cargo = mancha.cargo,
                    tipoPep = mancha.tipoPep,
                    periodoDesde = mancha.periodoDesde,
                    periodoHasta = mancha.periodoHasta
                )
            }

        return ResultadoBusquedaResponse(
            entidadId = this.id!!,
            tipoEntidad = this.tipoEntidad,
            documento = this.documento,
            tipoDocumento = this.tipoDocumento.nombre,
            nombreCompleto = nombreCompleto,
            pais = this.pais?.nombre,
            manchas = manchas
        )
    }
}
