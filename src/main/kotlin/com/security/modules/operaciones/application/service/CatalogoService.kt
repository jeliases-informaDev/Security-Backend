package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.infraestructure.CatalogoRepository
import com.security.modules.operaciones.dto.CatalogosOperacionResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Service

@Service
@Transactional(readOnly = true)
class CatalogoService(
    private val catalogoRepository: CatalogoRepository,
    private val catalogosRegistro: CatalogosRegistro? = null
) {

    fun catalogos(): CatalogosOperacionResponse {
        val registro = catalogosRegistro?.cargar()
        fun lista(catalogo: String) = registro?.de(catalogo).orEmpty().map {
            com.security.modules.operaciones.dto.OpcionRegistroResponse(it.codigo, it.nombre, it.pideTexto, it.sector)
        }
        return CatalogosOperacionResponse(
            monedas = catalogoRepository.monedas(),
            ocupaciones = catalogoRepository.ocupaciones(),
            tiposDocumento = catalogoRepository.tiposDocumento(),
            departamentos = catalogoRepository.departamentos(),
            paises = catalogoRepository.paises(),
            tiposDocumentoOperacion = lista(CatalogosOperacion.TIPO_DOCUMENTO),
            estadosCiviles = lista(CatalogosOperacion.ESTADO_CIVIL),
            modalidadesPago = lista(CatalogosOperacion.MODALIDAD_PAGO),
            origenesFondos = lista(CatalogosOperacion.ORIGEN_FONDOS),
            // Hasta que la empresa tenga sector (entrega B), los del sector inmobiliario.
            tiposOperacion = lista(CatalogosOperacion.TIPO_OPERACION)
                .filter { it.sector == null || it.sector == CatalogosOperacion.SECTOR_POR_DEFECTO },
            tipoDocumentoInicial = CatalogosOperacion.DOCUMENTO_INICIAL
        )
    }

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
