package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.AccesoEmpresaService
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.tuempresa.operaciones.dto.RegistroDto
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.Optional
import kotlin.test.*

/** La empresa sale de la cuenta autenticada; lo ajeno responde "no encontrado". */
class AislamientoEmpresaTest {
    private val usuarios = mockk<UsuarioRepository>()
    private val operaciones = mockk<OperacionRepository>()
    private val acceso = AccesoEmpresaService(usuarios, operaciones)

    private fun usuario(id: Int, nombre: String, empresa: String?, activo: Boolean = true) =
        Usuario(id = id, usuario = nombre, clave = "x", correo = "$nombre@example.test", nombres = "N", empresa = empresa, activo = activo)

    private fun entrar(nombre: String, empresa: String?, rol: String = "USUARIO", activo: Boolean = true) {
        every { usuarios.findByUsuario(nombre) } returns usuario(1, nombre, empresa, activo)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(nombre, null, listOf(SimpleGrantedAuthority("ROLE_$rol")))
    }

    private fun registro(idDuenio: Int, empresaGuardada: String = "Gamma") = mockk<RegistroOperacionesEntity> {
        every { idUsuario } returns idDuenio
        every { empresa } returns empresaGuardada
    }

    @AfterEach
    fun limpiar() = SecurityContextHolder.clearContext()

    @Test
    fun `una cuenta comun siempre trabaja con su propia empresa aunque pida otra`() {
        entrar("ana", " Gamma ")
        assertEquals("Gamma", acceso.empresaPermitida("Delta"))
        assertEquals("Gamma", acceso.empresaPermitida(null))
        assertEquals("ana", acceso.usuarioPermitido("otro"))
    }

    @Test
    fun `un administrador puede pedir otra empresa u otra cuenta, y sin indicarla usa la suya`() {
        entrar("admin", "Sulotec", rol = "ADMINISTRADOR")
        assertEquals("Delta", acceso.empresaPermitida(" Delta "))
        assertEquals("Sulotec", acceso.empresaPermitida(""))
        assertEquals("otro", acceso.usuarioPermitido("otro"))
        assertEquals("admin", acceso.usuarioPermitido(null))
    }

    @Test
    fun `administrador sin empresa debe indicarla`() {
        entrar("admin", null, rol = "ADMINISTRADOR")
        assertEquals(400, assertFailsWith<ResponseStatusException> { acceso.empresaPermitida(null) }.statusCode.value())
    }

    @Test
    fun `cuenta comun sin empresa, inactiva, inexistente o sin sesion no accede`() {
        entrar("ana", "  ")
        assertEquals(403, assertFailsWith<ResponseStatusException> { acceso.empresaPermitida("Gamma") }.statusCode.value())
        entrar("ana", "Gamma", activo = false)
        assertEquals(403, assertFailsWith<ResponseStatusException> { acceso.empresaPermitida(null) }.statusCode.value())
        every { usuarios.findByUsuario("fantasma") } returns null
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("fantasma", null, emptyList())
        assertEquals(403, assertFailsWith<ResponseStatusException> { acceso.actor() }.statusCode.value())
        SecurityContextHolder.clearContext()
        assertEquals(401, assertFailsWith<ResponseStatusException> { acceso.actor() }.statusCode.value())
    }

    @Test
    fun `un registro nuevo queda a nombre de quien lo crea y en su empresa`() {
        entrar("ana", "Gamma")
        val pedido = RegistroDto(usuarioId = "99", usuario = "victima", empresa = "Delta", oficina = "Lima", fechaRegistro = LocalDate.of(2026, 10, 17))
        val propio = acceso.registroDelActor(pedido)
        assertEquals("1", propio.usuarioId)
        assertEquals("ana", propio.usuario)
        assertEquals("Gamma", propio.empresa)
        assertEquals("Lima", propio.oficina)

        entrar("admin", "Sulotec", rol = "ADMINISTRADOR")
        assertSame(pedido, acceso.registroDelActor(pedido))
    }

    @Test
    fun `un registro de otra empresa responde no encontrado y uno propio pasa`() {
        entrar("ana", "Gamma")
        every { operaciones.findById(7L) } returns Optional.of(registro(idDuenio = 5))
        every { usuarios.findById(5) } returns Optional.of(usuario(5, "luis", "Delta"))
        assertFailsWith<ResourceNotFoundException> { acceso.comprobarRegistro(7L) }

        every { operaciones.findById(8L) } returns Optional.of(registro(idDuenio = 6))
        every { usuarios.findById(6) } returns Optional.of(usuario(6, "maria", " gamma "))
        acceso.comprobarRegistro(8L)
    }

    @Test
    fun `si la cuenta duena ya no existe se usa la empresa guardada en el registro`() {
        entrar("ana", "Gamma")
        every { operaciones.findById(9L) } returns Optional.of(registro(idDuenio = 77, empresaGuardada = "Delta"))
        every { usuarios.findById(77) } returns Optional.empty()
        assertFailsWith<ResourceNotFoundException> { acceso.comprobarRegistro(9L) }

        every { operaciones.findById(10L) } returns Optional.of(registro(idDuenio = 77, empresaGuardada = "Gamma"))
        acceso.comprobarRegistro(10L)
    }

    @Test
    fun `un registro inexistente no se bloquea aqui (el caso de uso da su 404) y el administrador pasa siempre`() {
        entrar("ana", "Gamma")
        every { operaciones.findById(404L) } returns Optional.empty()
        acceso.comprobarRegistro(404L)

        entrar("admin", null, rol = "ADMINISTRADOR")
        acceso.comprobarRegistro(7L)
        verify(exactly = 0) { operaciones.findById(7L) }
    }
}
