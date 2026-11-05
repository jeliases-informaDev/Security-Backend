package com.security.modules.auth.sso

import com.security.modules.auth.AuthService
import com.security.modules.auth.dto.LoginResponse
import com.security.modules.auth.dto.UsuarioLoginResponse
import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.empresas.dto.EmpresaCreateRequest
import com.security.modules.empresas.dto.EmpresaResponse
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.cliente.LicenciasNoDisponibleException
import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.modules.licencias.dominio.TitularRemoto
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime
import kotlin.test.*

/** SsoService con dobles del canje (SsoProveedor), del servicio de licencias y de la base. */
class SsoServiceTest {
    private val uuid = "11111111-2222-3333-4444-555555555555"
    private val ahora = LocalDateTime.now()

    private var identidad = SsoIdentidad("ana@ejemplo.com", true, "Ana", "Torres", emptySet(), null, null)
    private val canjes = mutableListOf<Pair<String, String>>()
    private val proveedor = object : SsoProveedor {
        override fun canjear(code: String, codeVerifier: String): SsoIdentidad {
            canjes += code to codeVerifier
            return identidad
        }
    }

    private val usuarios = mockk<UsuarioRepository>()
    private val roles = mockk<RolesRepository>()
    private val usuarioRoles = mockk<UsuarioRolRepository>()
    private val empresas = mockk<EmpresaRepository>()
    private val empresaService = mockk<EmpresaService>()
    private val claves = mockk<PasswordEncoder>()
    private val licencias = mockk<LicenciaService>(relaxed = true)
    private val auth = mockk<AuthService>()
    private val activas = SsoProperties(issuer = "https://cuenta.ejemplo.com/realms/sulotec", clientId = "plaft", redirectUri = "https://plaft.ejemplo.com/sso")

    private fun servicio(props: SsoProperties = activas) = SsoService(
        props, proveedor, usuarios, roles, usuarioRoles, empresas, empresaService, claves, licencias, auth,
        mockk<PlatformTransactionManager>(relaxed = true)
    )

    private fun rol(codigo: String) = Roles(id = 1, codigo = codigo, nombre = codigo, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora)

    private fun usuario(nombre: String, rolCodigo: String, activo: Boolean = true, empresa: String? = "Acme SAC"): Usuario {
        val cuenta = Usuario(id = 7, usuario = nombre, clave = "x", correo = "ana@ejemplo.com", empresa = empresa, activo = activo,
            fechaCreacion = ahora, fechaActualizacion = ahora)
        cuenta.roles = mutableListOf(UsuarioRol(usuario = cuenta, rol = rol(rolCodigo)))
        return cuenta
    }

    private val guardados = mutableListOf<Usuario>()

    init {
        every { auth.respuestaDe(any()) } answers {
            val u = firstArg<Usuario>()
            LoginResponse("jwt-de-plaft", "Bearer", 900, UsuarioLoginResponse(u.nombres ?: "", u.usuario, u.roles.first().rol.codigo, "rol"))
        }
        every { claves.encode(any()) } returns "hash"
        every { usuarios.save(any<Usuario>()) } answers { firstArg<Usuario>().also { it.id = 50; guardados += it } }
        every { usuarioRoles.save(any<UsuarioRol>()) } answers { firstArg() }
        every { usuarios.existsByUsuario(any()) } returns false
        every { roles.findByCodigo(any()) } answers { rol(firstArg()) }
    }

    private fun entrar(s: SsoService = servicio()) = s.entrar(SsoLoginRequest(code = "codigo-1", codeVerifier = "verificador-1"))

    private fun titular() {
        identidad = identidad.copy(roles = setOf("producto-plaft"), empresaUuid = uuid)
        every { usuarios.findByCorreoIgnoreCase("ana@ejemplo.com") } returns null
        every { licencias.licenciaEnVivo(uuid) } returns LicenciaRemota(
            estado = "ACTIVA", titular = TitularRemoto(email = "ANA@ejemplo.com"), empresaNombre = "Acme SAC"
        )
    }

    @Test
    fun `apagado responde activo false y 404 sin hablar con Sulotec`() {
        val apagado = servicio(SsoProperties())
        assertFalse(apagado.config().activo)
        assertNull(apagado.config().issuer)
        val error = assertFailsWith<SsoException> { entrar(apagado) }
        assertEquals(SsoReglas.INACTIVO, error.code)
        assertEquals(404, error.status.value())
        assertTrue(canjes.isEmpty())
    }

    @Test
    fun `encendido publica emisor cliente y retorno`() {
        val config = servicio().config()
        assertEquals(SsoConfigResponse(true, "https://cuenta.ejemplo.com/realms/sulotec", "plaft", "https://plaft.ejemplo.com/sso"), config)
    }

    @Test
    fun `sin codigo o sin verificador es SSO_DATOS`() {
        val s = servicio()
        assertEquals(SsoReglas.DATOS, assertFailsWith<SsoException> { s.entrar(SsoLoginRequest(code = "c")) }.code)
        assertEquals(SsoReglas.DATOS, assertFailsWith<SsoException> { s.entrar(SsoLoginRequest(codeVerifier = "v")) }.code)
        assertTrue(canjes.isEmpty())
    }

    @Test
    fun `un usuario que ya existe entra con su cuenta y la misma respuesta del login`() {
        val ana = usuario("acme.ana", "SUPERVISOR")
        every { usuarios.findByCorreoIgnoreCase("ana@ejemplo.com") } returns ana

        val respuesta = entrar()

        assertEquals("jwt-de-plaft", respuesta.accessToken)
        assertEquals("acme.ana", respuesta.usuario.usuario)
        assertEquals(listOf("codigo-1" to "verificador-1"), canjes)
        verify(exactly = 1) { auth.respuestaDe(ana) }
        verify(exactly = 0) { usuarios.save(any<Usuario>()) }
    }

    @Test
    fun `un usuario inactivo no entra`() {
        every { usuarios.findByCorreoIgnoreCase("ana@ejemplo.com") } returns usuario("acme.ana", "USUARIO", activo = false)
        assertEquals(SsoReglas.CUENTA_INACTIVA, assertFailsWith<SsoException> { entrar() }.code)
    }

    @Test
    fun `el titular entra solo y se crea su empresa con el UUID de Sulotec`() {
        titular()
        every { empresas.findByUuid(uuid) } returns null
        every { empresas.findByNombreIgnoreCase("Acme SAC") } returns null
        val pedido = slot<EmpresaCreateRequest>()
        every { empresaService.crear(capture(pedido)) } returns EmpresaResponse(3, uuid, "AS", "Acme SAC", null, true, 0)

        val respuesta = entrar()

        assertEquals("Acme SAC", pedido.captured.nombre)
        assertEquals(uuid, pedido.captured.uuid)
        val creado = guardados.single()
        assertEquals("as.ana", creado.usuario)
        assertEquals("Acme SAC", creado.empresa)
        assertEquals("Ana", creado.nombres)
        assertEquals("Torres", creado.apePat)
        assertEquals("ana@ejemplo.com", creado.correo)
        assertEquals("ADMINISTRADOR_EMPRESA", creado.roles.single().rol.codigo)
        assertEquals("as.ana", respuesta.usuario.usuario)
        // El dueño de la licencia no pasa por el cupo, pero sí se reportan las cuentas activas.
        verify(exactly = 0) { licencias.verificarCupo(any(), any(), any()) }
        verify(exactly = 1) { licencias.reportarActivosLuego("Acme SAC") }
        // La clave es aleatoria y larga (nadie la conoce).
        verify { claves.encode(match { (it?.length ?: 0) >= 40 }) }
    }

    @Test
    fun `el titular de una empresa que ya existe usa su sigla y un usuario libre`() {
        titular()
        every { empresas.findByUuid(uuid) } returns Empresa(id = 3, nombre = "Acme SAC", uuid = uuid, sigla = "ACME")
        every { usuarios.existsByUsuario("acme.ana") } returns true

        entrar()

        assertEquals("acme.ana2", guardados.single().usuario)
        verify(exactly = 0) { empresaService.crear(any()) }
    }

    @Test
    fun `una empresa con el mismo nombre y otro UUID es SSO_EMPRESA_EN_CONFLICTO`() {
        titular()
        every { empresas.findByUuid(uuid) } returns null
        every { empresas.findByNombreIgnoreCase("Acme SAC") } returns Empresa(id = 3, nombre = "Acme SAC", uuid = "99999999-2222-3333-4444-555555555555")

        val error = assertFailsWith<SsoException> { entrar() }
        assertEquals(SsoReglas.EMPRESA_EN_CONFLICTO, error.code)
        assertEquals(409, error.status.value())
        assertTrue(guardados.isEmpty())
    }

    @Test
    fun `si Sulotec no responde la licencia es 424 y no se crea nada`() {
        titular()
        every { licencias.licenciaEnVivo(uuid) } throws LicenciasNoDisponibleException("RED", "sin respuesta")

        val error = assertFailsWith<SsoException> { entrar() }
        assertEquals(SsoReglas.EMISOR, error.code)
        assertEquals(424, error.status.value())
        assertTrue(guardados.isEmpty())
    }

    @Test
    fun `el administrador universal sin cuenta se crea sin empresa con rol ADMINISTRADOR`() {
        identidad = identidad.copy(roles = setOf("administrador-general"))
        every { usuarios.findByCorreoIgnoreCase("ana@ejemplo.com") } returns null

        entrar()

        val creado = guardados.single()
        assertEquals("ana", creado.usuario)
        assertNull(creado.empresa)
        assertEquals("ADMINISTRADOR", creado.roles.single().rol.codigo)
        verify(exactly = 0) { licencias.licenciaEnVivo(any()) }
    }

    @Test
    fun `quien no es titular ni tiene cuenta recibe SSO_SIN_CUENTA`() {
        titular()
        every { licencias.licenciaEnVivo(uuid) } returns LicenciaRemota(estado = "ACTIVA", titular = TitularRemoto(email = "jefe@ejemplo.com"))
        assertEquals(SsoReglas.SIN_CUENTA, assertFailsWith<SsoException> { entrar() }.code)
        assertTrue(guardados.isEmpty())
    }
}
