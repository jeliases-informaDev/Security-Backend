package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.infraestructure.CatalogoRepository
import com.security.modules.operaciones.dto.CatalogosOperacionResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Service

@Service
@Transactional(readOnly = true)
class CatalogoService(private val catalogoRepository: CatalogoRepository) {

    fun catalogos() = CatalogosOperacionResponse(
        monedas = catalogoRepository.monedas(),
        ocupaciones = catalogoRepository.ocupaciones(),
        tiposDocumento = catalogoRepository.tiposDocumento(),
        departamentos = catalogoRepository.departamentos(),
        paises = catalogoRepository.paises()
    )

    fun ocupaciones() = catalogoRepository.ocupaciones()
    fun tiposDocumento() = catalogoRepository.tiposDocumento()
    fun paises() = catalogoRepository.paises()
    fun provincias(idDepartamento: Long): List<com.security.modules.operaciones.dto.ProvinciaOpcionResponse> {
        if (idDepartamento <= 0) {
            throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "idDepartamento debe ser positivo"
            )
        }
        return catalogoRepository.provincias(idDepartamento)
    }
    fun departamentos(idPais: Long? = null) = catalogoRepository.departamentos(idPais)

    @Cacheable("catalogo-monedas")
    fun monedas() = catalogoRepository.monedas()

    @Cacheable("catalogo-tipos-operacion")
    fun tiposOperacion() = catalogoRepository.tiposOperacion()

    @Cacheable("catalogo-estados")
    fun estados() = catalogoRepository.estados()
}
