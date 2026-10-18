package com.security.modules.denuncias

import com.security.modules.denuncias.application.dto.CrearDenunciaRequest
import com.security.modules.denuncias.application.service.AutorizacionDenunciaService
import com.security.modules.denuncias.application.usecase.CrearDenunciaUseCase
import com.security.modules.denuncias.application.usecase.ListarDenunciasUseCase
import com.security.modules.denuncias.application.usecase.ListarReceptoresDenunciaUseCase
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

/** Los clientes no saben unos de otros: receptores, destino y visibilidad de una denuncia solo dentro de la propia empresa. */
class DenunciasEmpresaTest {
    private val usuarios = mockk<UsuarioRepository>()
    private val denuncias = mockk<DenunciaRepository>()
    private val autorizacion = AutorizacionDenunciaService()

    private fun usuario(id: Int, nombre: String, empresa: String?, rol: String = "SUPERVISOR"): Usuario {
        val ahora = LocalDateTime.now()
        val cuenta = Usuario(id = id, usuario = nombre, clave = "x", correo = "$nombre@example.test", nombres = nombre,
            empresa = empresa, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora)
        cuenta.roles = mutableListOf(UsuarioRol(usuario = cuenta, rol = Roles(id = 1, codigo = rol, nombre = rol,
            activo = true, fechaCreacion = ahora, fechaActualizacion = ahora)))
        return cuenta
    }

    private fun denuncia(receptor: Usuario?, creador: Usuario? = null) = mockk<DenunciaEntity> {
        every { receptorUsuario } returns receptor
        every { creadoPorUsuario } returns creador
    }

    private val receptores = listOf(usuario(1, "ana", " Gamma "), usuario(2, "luis", "Delta"), usuario(3, "maria", "gamma"), usuario(4, "solo", null))

    @Test
    fun `una cuenta comun solo ve receptores de su empresa y sin empresa no ve ninguno`() {
        every { usuarios.findAllActivosByRoles(any()) } returns receptores
        val caso = ListarReceptoresDenunciaUseCase(usuarios, autorizacion)

        assertEquals(listOf("ana", "maria"), caso.ejecutar(usuario(9, "gina", "Gamma")).map { it.usuario })
        assertTrue(caso.ejecutar(usuario(9, "nadie", "  ")).isEmpty())
    }

    @Test
    fun `el administrador universal ve a todos los receptores y el de empresa solo a los suyos`() {
        every { usuarios.findAllActivosByRoles(any()) } returns receptores
        val caso = ListarReceptoresDenunciaUseCase(usuarios, autorizacion)

        assertEquals(4, caso.ejecutar(usuario(9, "admin", "Sulotec", rol = "ADMINISTRADOR")).size)
        assertEquals(listOf("ana", "maria"), caso.ejecutar(usuario(9, "jefa", "Gamma", rol = "ADMINISTRADOR_EMPRESA")).map { it.usuario })
    }

    private fun crear(receptor: Usuario): Pair<CrearDenunciaUseCase, CrearDenunciaRequest> {
        every { usuarios.findById(receptor.id!!) } returns Optional.of(receptor)
        val request = mockk<CrearDenunciaRequest>(relaxed = true)
        every { request.receptorUsuarioId } returns receptor.id
        every { request.receptorPropuesto } returns null
        // el resto de validaciones del pedido deben pasar para llegar a la comprobacion de empresa
        every { request.aceptaTerminos } returns true
        every { request.involucrados } returns listOf(mockk(relaxed = true))
        val caso = CrearDenunciaUseCase(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), usuarios, mockk(relaxed = true), autorizacion)
        return caso to request
    }

    @Test
    fun `no se puede dirigir una denuncia a un receptor de otra empresa`() {
        val (caso, request) = crear(receptor = usuario(2, "luis", "Delta"))
        val error = assertFailsWith<ResponseStatusException> { caso.ejecutar(request, usuario(9, "gina", "Gamma")) }
        assertEquals(400, error.statusCode.value())
    }

    @Test
    fun `quien registra sin empresa no puede elegir receptor del sistema`() {
        val (caso, request) = crear(receptor = usuario(2, "luis", "Delta"))
        val error = assertFailsWith<ResponseStatusException> { caso.ejecutar(request, usuario(9, "nadie", null)) }
        assertEquals(403, error.statusCode.value())
    }

    @Test
    fun `el administrador de empresa gestiona solo las denuncias de su empresa`() {
        val jefa = usuario(9, "jefa", "Gamma", rol = "ADMINISTRADOR_EMPRESA")
        autorizacion.validarAccesoAdministrativo(jefa)
        autorizacion.validarAccesoDenuncia(jefa, denuncia(receptor = usuario(1, "ana", "gamma")))
        // sin receptor: cuenta la empresa de quien la registro
        autorizacion.validarAccesoDenuncia(jefa, denuncia(receptor = null, creador = usuario(3, "maria", " Gamma ")))
        assertEquals(404, assertFailsWith<ResponseStatusException> {
            autorizacion.validarAccesoDenuncia(jefa, denuncia(receptor = usuario(2, "luis", "Delta")))
        }.statusCode.value())
        assertEquals(404, assertFailsWith<ResponseStatusException> {
            autorizacion.validarAccesoDenuncia(jefa, denuncia(receptor = null, creador = null))
        }.statusCode.value())
    }

    @Test
    fun `un supervisor sigue viendo solo las asignadas a el y una cuenta comun no gestiona`() {
        val supervisor = usuario(1, "ana", "Gamma")
        autorizacion.validarAccesoDenuncia(supervisor, denuncia(receptor = supervisor))
        assertEquals(404, assertFailsWith<ResponseStatusException> {
            autorizacion.validarAccesoDenuncia(supervisor, denuncia(receptor = usuario(3, "maria", "Gamma")))
        }.statusCode.value())
        assertEquals(403, assertFailsWith<ResponseStatusException> {
            autorizacion.validarAccesoAdministrativo(usuario(8, "comun", "Gamma", rol = "USUARIO"))
        }.statusCode.value())
    }

    @Test
    fun `al listar, el administrador de empresa consulta por su empresa, el universal todo y el supervisor lo suyo`() {
        val caso = ListarDenunciasUseCase(denuncias, autorizacion)
        every { denuncias.buscarPorEmpresa("Gamma") } returns emptyList()
        every { denuncias.buscarPorEmpresaYEstado("Gamma", EstadoDenuncia.values().first()) } returns emptyList()
        every { denuncias.findAllByOrderByFechaCreacionDesc() } returns emptyList()
        every { denuncias.findAllByReceptorUsuario_IdOrderByFechaCreacionDesc(1) } returns emptyList()

        caso.ejecutar(null, usuario(9, "jefa", " Gamma ", rol = "ADMINISTRADOR_EMPRESA"))
        caso.ejecutar(EstadoDenuncia.values().first(), usuario(9, "jefa", "Gamma", rol = "ADMINISTRADOR_EMPRESA"))
        caso.ejecutar(null, usuario(7, "admin", null, rol = "ADMINISTRADOR"))
        caso.ejecutar(null, usuario(1, "ana", "Gamma"))

        verify(exactly = 1) { denuncias.buscarPorEmpresa("Gamma") }
        verify(exactly = 1) { denuncias.buscarPorEmpresaYEstado("Gamma", any()) }
        verify(exactly = 1) { denuncias.findAllByOrderByFechaCreacionDesc() }
        verify(exactly = 1) { denuncias.findAllByReceptorUsuario_IdOrderByFechaCreacionDesc(1) }
    }
}
