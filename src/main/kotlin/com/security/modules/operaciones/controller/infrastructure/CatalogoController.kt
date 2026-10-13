package com.security.modules.operaciones.controller.infrastructure

import com.security.modules.operaciones.application.service.CatalogoService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/operaciones/catalogos")
class CatalogoController(private val service: CatalogoService) {
    @GetMapping
    fun catalogos() = service.catalogos()

    @GetMapping("/monedas")
    fun monedas() = service.monedas()

    @GetMapping("/ocupaciones")
    fun ocupaciones() = service.ocupaciones()

    @GetMapping("/tipos-documento")
    fun tiposDocumento() = service.tiposDocumento()

    @GetMapping("/paises")
    fun paises() = service.paises()

    @GetMapping("/departamentos")
    fun departamentos(@RequestParam(required = false) idPais: Long?) = service.departamentos(idPais)

    @GetMapping("/provincias")
    fun provincias(@RequestParam("idDepartamento") idDepartamento: Long) = service.provincias(idDepartamento)
}
