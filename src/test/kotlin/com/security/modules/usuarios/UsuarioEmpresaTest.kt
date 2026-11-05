package com.security.modules.usuarios

import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaReglas
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.LicenciaSinCupoException
import com.security.modules.usuarios.application.service.UsuarioService
import com.security.modules.usuarios.dto.CambiarEmpresaRequest
import com.security.modules.usuarios.dto.UsuarioCreateRequest
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import com.security.shared.exceptions.ResourceNotFoundException
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

class UsuarioEmpresaTest {
    private val usuarios = mockk<UsuarioRepository>()
    private val roles = mockk<RolesRepository>()
    private val usuarioRoles = mockk<UsuarioRolRepository>()
    private val encoder = mockk<PasswordEncoder>()
    private val empresaService = mockk<EmpresaService>()
    private val licencias = mockk<LicenciaService>(relaxed = true)
    private val contexto = mockk<LicenciaContexto>()
    private val service = UsuarioService(usuarios, roles, usuarioRoles, encoder, empresaService, licencias, contexto)

    private fun rol(codigo: String = "USUARIO", nombre: String = "Usuario", activo: Boolean = true) =
        Roles(id = 3, codigo = codigo, nombre = nombre, activo = activo,
            fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now())

    private fun solicitud(empresa: String? = "peru luz") = UsuarioCreateRequest(
        usuario = "ana", clave = "Clave12345", correo = "ana@example.test", nombres = "Ana", apePat = "Perez",
        empresa = empresa, rolCodigo = "USUARIO"
    )

    private fun prepararCreacion() {
        every { usuarios.existsByUsuario("ana") } returns false
        every { usuarios.existsByCorreo("ana@example.test") } returns false
        every { roles.findByCodigo("USUARIO") } returns rol()
        every { encoder.encode(any()) } returns "hash"
        every { usuarioRoles.save(any()) } answers { firstArg() }
    }

    @Test
    fun `crear guarda la cuenta con el nombre exacto de la empresa de la lista`() {
        prepararCreacion()
        every { empresaService.resolverEmpresaActiva("peru luz") } returns Empresa(id = 1, nombre = "Peru Luz S.A.C", sigla = "PLZ", uuid = "u")
        every { usuarios.existsByUsuario("plz.ana") } returns false
        val guardado = slot<Usuario>()
        every { usuarios.save(capture(guardado)) } answers { guardado.captured.also { it.id = 5 } }

        val respuesta = service.crear(solicitud())

        // el usuario interno lleva la sigla de la empresa delante
        assertEquals("plz.ana", guardado.captured.usuario)
        assertEquals("Peru Luz S.A.C", guardado.captured.empresa)
        assertEquals("Peru Luz S.A.C", respuesta.empresa)
        assertEquals("USUARIO", respuesta.rolCodigo)
        verify { licencias.verificarCupo("u", 1, false) }
        verify { licencias.reportarActivosLuego("Peru Luz S.A.C") }
    }

    @Test
    fun `crear responde 409 y no guarda nada si el plan no tiene cupo`() {
        prepararCreacion()
        every { empresaService.resolverEmpresaActiva("peru luz") } returns Empresa(id = 1, nombre = "Peru Luz S.A.C", sigla = "PLZ", uuid = "u")
        every { usuarios.existsByUsuario("plz.ana") } returns false
        every { licencias.verificarCupo("u", 1, false) } throws
            LicenciaSinCupoException(activos = 5, usuariosMax = 5, enlace = LicenciaReglas.ENLACE_MI_LICENCIA)

        val error = assertFailsWith<LicenciaSinCupoException> { service.crear(solicitud()) }

        assertEquals(LicenciaReglas.CODE_SIN_CUPO, error.code)
        assertEquals(5L, error.usuariosMax)
        verify(exactly = 0) { usuarios.save(any()) }
        verify(exactly = 0) { usuarioRoles.save(any()) }
    }

    private fun adminDeEmpresa(nombre: String = "jefa") =
        UsernamePasswordAuthenticationToken(nombre, null, listOf(SimpleGrantedAuthority("ROLE_ADMINISTRADOR_EMPRESA")))

    private fun cuentaDe(id: Int, nombre: String, empresa: String?, activo: Boolean = true) =
        Usuario(id = id, usuario = nombre, clave = "x", correo = "$nombre@example.test", empresa = empresa, activo = activo,
            fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now())

    @Test
    fun `cambiarEstado no deja a un administrador de empresa tocar cuentas de otra empresa`() {
        val ajena = cuentaDe(3, "luis", "Otra")
        every { usuarios.findById(3) } returns Optional.of(ajena)
        every { usuarios.findByUsuario("jefa") } returns cuentaDe(1, "jefa", "Peru Luz S.A.C")

        assertFailsWith<ResourceNotFoundException> { service.cambiarEstado(3, false, adminDeEmpresa()) }

        assertTrue(ajena.activo)
        verify(exactly = 0) { usuarios.save(any()) }
    }

    @Test
    fun `cambiarEstado desactiva una cuenta de su empresa y reporta las cuentas activas`() {
        val propia = cuentaDe(3, "luis", " peru luz s.a.c ")
        every { usuarios.findById(3) } returns Optional.of(propia)
        every { usuarios.findByUsuario("jefa") } returns cuentaDe(1, "jefa", "Peru Luz S.A.C")
        every { usuarios.save(any()) } answers { firstArg() }
        every { usuarios.findByUsuario("luis") } returns null

        val respuesta = service.cambiarEstado(3, false, adminDeEmpresa())

        assertFalse(respuesta.activo)
        verify(exactly = 0) { licencias.verificarCupo(any(), any(), any()) }
        verify { licencias.reportarActivosLuego(" peru luz s.a.c ") }
    }

    @Test
    fun `reactivar una cuenta sin cupo en el plan responde 409`() {
        val inactiva = cuentaDe(3, "luis", "Peru Luz S.A.C", activo = false)
        every { usuarios.findById(3) } returns Optional.of(inactiva)
        every { usuarios.findByUsuario("jefa") } returns cuentaDe(1, "jefa", "Peru Luz S.A.C")
        every { contexto.uuidDeEmpresa("Peru Luz S.A.C") } returns "u"
        every { licencias.verificarCupo("u", 1, false) } throws
            LicenciaSinCupoException(activos = 5, usuariosMax = 5, enlace = LicenciaReglas.ENLACE_MI_LICENCIA)

        assertFailsWith<LicenciaSinCupoException> { service.cambiarEstado(3, true, adminDeEmpresa()) }

        assertFalse(inactiva.activo)
        verify(exactly = 0) { usuarios.save(any()) }
    }

    @Test
    fun `crear no guarda nada si la empresa no esta en la lista o no se indica`() {
        prepararCreacion()
        every { empresaService.resolverEmpresaActiva(any()) } throws ResponseStatusException(HttpStatus.BAD_REQUEST, "La empresa es obligatoria")

        listOf(null, "", "Fantasma").forEach {
            val error = assertFailsWith<ResponseStatusException> { service.crear(solicitud(it)) }
            assertEquals(400, error.statusCode.value())
        }
        verify(exactly = 0) { usuarios.save(any()) }
        verify(exactly = 0) { usuarioRoles.save(any()) }
    }

    @Test
    fun `cambiarEmpresa pasa la cuenta a la empresa de la lista`() {
        val cuenta = Usuario(id = 3, usuario = "ana", clave = "x", correo = "ana@example.test", empresa = "Otra",
            fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now())
        every { usuarios.findById(3) } returns Optional.of(cuenta)
        every { empresaService.resolverActiva("peru luz") } returns "Peru Luz S.A.C"
        every { usuarios.save(any()) } answers { firstArg() }
        every { contexto.uuidDeEmpresa("Peru Luz S.A.C") } returns "u"

        val respuesta = service.cambiarEmpresa(3, CambiarEmpresaRequest("peru luz"), true)

        assertEquals("Peru Luz S.A.C", respuesta.empresa)
        assertEquals("Peru Luz S.A.C", cuenta.empresa)
        // la cuenta esta activa: se pide el cupo de la empresa destino y se reportan las dos
        verify { licencias.verificarCupo("u", 1, true) }
        verify { licencias.reportarActivosLuego("Otra", "Peru Luz S.A.C") }
    }

    @Test
    fun `cambiarEmpresa rechaza una empresa no valida y una cuenta que no existe`() {
        val cuenta = Usuario(id = 3, usuario = "ana", clave = "x", correo = "ana@example.test", empresa = "Otra",
            fechaCreacion = LocalDateTime.now(), fechaActualizacion = LocalDateTime.now())
        every { usuarios.findById(3) } returns Optional.of(cuenta)
        every { empresaService.resolverActiva("Fantasma") } throws ResponseStatusException(HttpStatus.BAD_REQUEST, "no esta en la lista")
        assertFailsWith<ResponseStatusException> { service.cambiarEmpresa(3, CambiarEmpresaRequest("Fantasma")) }
        assertEquals("Otra", cuenta.empresa)

        every { usuarios.findById(99) } returns Optional.empty()
        assertFailsWith<ResourceNotFoundException> { service.cambiarEmpresa(99, CambiarEmpresaRequest("peru luz")) }
        verify(exactly = 0) { usuarios.save(any()) }
    }

    @Test
    fun `listarDeMiEmpresa devuelve solo las cuentas de la empresa de quien pide y nada si no tiene`() {
        val ahora = LocalDateTime.now()
        val cuenta = { id: Int, nombre: String, empresa: String? ->
            Usuario(id = id, usuario = nombre, clave = "x", correo = "$nombre@example.test", empresa = empresa,
                fechaCreacion = ahora, fechaActualizacion = ahora)
        }
        every { usuarios.findByUsuario("ana") } returns cuenta(1, "ana", " Gamma ")
        every { usuarios.findByUsuario("solo") } returns cuenta(4, "solo", null)
        every { usuarios.findAllConRoles() } returns listOf(cuenta(1, "ana", "Gamma"), cuenta(2, "luis", "Delta"), cuenta(3, "maria", "gamma"))

        assertEquals(listOf("ana", "maria"), service.listarDeMiEmpresa("ana").map { it.usuario })
        assertTrue(service.listarDeMiEmpresa("solo").isEmpty())
    }

    @Test
    fun `listarRoles devuelve solo los roles activos ordenados por nombre`() {
        every { roles.findAll() } returns listOf(
            rol("USUARIO", "Usuario"),
            rol("ANTIGUO", "Antiguo", activo = false),
            rol("ADMINISTRADOR", "Administrador")
        )

        val lista = service.listarRoles()

        assertEquals(listOf("ADMINISTRADOR", "USUARIO"), lista.map { it.codigo })
        assertEquals(listOf("Administrador", "Usuario"), lista.map { it.nombre })
    }
}
