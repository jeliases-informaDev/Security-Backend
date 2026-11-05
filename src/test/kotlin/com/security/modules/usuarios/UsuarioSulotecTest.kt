package com.security.modules.usuarios

import com.security.modules.auth.sso.CuentasSulotec
import com.security.modules.auth.sso.SsoException
import com.security.modules.auth.sso.SsoProperties
import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.licencias.ContextoLicencia
import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaController
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.cliente.LicenciasCliente
import com.security.modules.licencias.cliente.LicenciasNoDisponibleException
import com.security.modules.licencias.dominio.CuentaRemota
import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.modules.usuarios.application.service.UsuarioService
import com.security.modules.usuarios.dto.UsuarioCreateRequest
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import com.security.shared.exceptions.ResourceNotFoundException
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

/** Alta y reinvitación de usuarios con cuentas Sulotec: Sulotec crea la cuenta; en PLAFT no vive ninguna contraseña nueva. */
class UsuarioSulotecTest {
    private val usuarios = mockk<UsuarioRepository>()
    private val roles = mockk<RolesRepository>()
    private val usuarioRoles = mockk<UsuarioRolRepository>()
    private val encoder = mockk<PasswordEncoder>()
    private val empresaService = mockk<EmpresaService>()
    private val licencias = mockk<LicenciaService>(relaxed = true)
    private val contexto = mockk<LicenciaContexto>()
    private val cliente = mockk<LicenciasCliente>()
    private val ssoActivo = SsoProperties(issuer = "https://cuenta.ejemplo.com/realms/sulotec", clientId = "plaft", redirectUri = "https://plaft.ejemplo.com/sso")

    private fun servicio(sso: SsoProperties = ssoActivo) =
        UsuarioService(usuarios, roles, usuarioRoles, encoder, empresaService, licencias, contexto, CuentasSulotec(sso, licencias, cliente))

    private val ahora = LocalDateTime.now()
    private val claves = mutableListOf<String>()
    private val guardados = mutableListOf<Usuario>()

    init {
        every { licencias.activo } returns true
        every { licencias.licenciaDe("u", any()) } returns LicenciaRemota(estado = "ACTIVA")
        every { empresaService.resolverEmpresaActiva("peru luz") } returns Empresa(id = 1, nombre = "Peru Luz S.A.C", sigla = "PLZ", uuid = "u")
        every { usuarios.existsByUsuario(any()) } returns false
        every { usuarios.existsByCorreo(any()) } returns false
        every { roles.findByCodigo("USUARIO") } returns Roles(id = 3, codigo = "USUARIO", nombre = "Usuario", activo = true, fechaCreacion = ahora, fechaActualizacion = ahora)
        every { encoder.encode(any()) } answers { claves += firstArg<CharSequence>().toString(); "hash" }
        every { usuarios.save(any<Usuario>()) } answers { firstArg<Usuario>().also { it.id = 5; guardados += it } }
        every { usuarioRoles.save(any<UsuarioRol>()) } answers { firstArg() }
    }

    private fun solicitud(clave: String? = null) = UsuarioCreateRequest(
        usuario = "ana", clave = clave, correo = "ana@ejemplo.com", nombres = "Ana", apePat = "Perez",
        empresa = "peru luz", rolCodigo = "USUARIO"
    )

    private fun sulotecResponde(estado: String, invitacion: String = "correo", clave: String? = null, mensaje: String? = "Listo") {
        every { cliente.asegurarCuenta("u", "ana@ejemplo.com", "Ana Perez", false) } returns
            CuentaRemota(estado = estado, invitacion = invitacion, claveTemporal = clave, mensaje = mensaje)
    }

    @Test
    fun `creada guarda la cuenta con una clave aleatoria e ignora la que manda la web`() {
        sulotecResponde("creada", invitacion = "clave_temporal", clave = "Temporal-123")

        val respuesta = servicio().crear(solicitud(clave = "LaDeLaWeb123"))

        assertEquals("plz.ana", guardados.single().usuario)
        assertNotEquals("LaDeLaWeb123", claves.single())
        assertTrue(claves.single().length >= 40)
        val cuenta = assertNotNull(respuesta.cuentaSulotec)
        assertEquals("creada", cuenta.estado)
        assertEquals("clave_temporal", cuenta.invitacion)
        assertEquals("Temporal-123", cuenta.claveTemporal)
        assertEquals("Listo", cuenta.mensaje)
        verifyOrder {
            licencias.verificarCupo("u", 1, false)
            cliente.asegurarCuenta("u", "ana@ejemplo.com", "Ana Perez", false)
            usuarios.save(any<Usuario>())
            licencias.reportarActivosLuego("Peru Luz S.A.C")
        }
    }

    @Test
    fun `existente tambien guarda la cuenta sin pedir clave`() {
        sulotecResponde("existente", invitacion = "ninguna")
        val respuesta = servicio().crear(solicitud())
        assertEquals(1, guardados.size)
        assertEquals("existente", respuesta.cuentaSulotec?.estado)
        assertNull(respuesta.cuentaSulotec?.claveTemporal)
    }

    @Test
    fun `otra empresa y sin empresa son 409 con el mensaje de Sulotec y no guardan nada`() {
        sulotecResponde("otra_empresa", mensaje = "Ese correo es de otra empresa")
        val otra = assertFailsWith<SsoException> { servicio().crear(solicitud()) }
        assertEquals(409, otra.status.value())
        assertEquals(CuentasSulotec.CODE_OTRA_EMPRESA, otra.code)
        assertEquals("Ese correo es de otra empresa", otra.message)

        sulotecResponde("sin_empresa")
        val sin = assertFailsWith<SsoException> { servicio().crear(solicitud()) }
        assertEquals(409, sin.status.value())
        assertEquals(CuentasSulotec.CODE_SIN_EMPRESA, sin.code)
        assertTrue(guardados.isEmpty())
    }

    @Test
    fun `error sin_keycloak o Sulotec caido son 424 y no guardan nada`() {
        for (estado in listOf("error", "sin_keycloak")) {
            sulotecResponde(estado)
            val e = assertFailsWith<SsoException> { servicio().crear(solicitud()) }
            assertEquals(424, e.status.value())
            assertEquals(CuentasSulotec.CODE_NO_DISPONIBLE, e.code)
        }
        every { cliente.asegurarCuenta(any(), any(), any(), any()) } throws LicenciasNoDisponibleException("RED", "sin respuesta")
        val caido = assertFailsWith<SsoException> { servicio().crear(solicitud()) }
        assertEquals(424, caido.status.value())
        assertEquals(CuentasSulotec.MENSAJE_NO_DISPONIBLE, caido.message)
        assertTrue(guardados.isEmpty())
    }

    @Test
    fun `fuera del modo Sulotec la clave sigue siendo obligatoria y no se llama a Sulotec`() {
        val apagado = servicio(SsoProperties())
        val error = assertFailsWith<ResponseStatusException> { apagado.crear(solicitud(clave = null)) }
        assertEquals(400, error.statusCode.value())

        val respuesta = apagado.crear(solicitud(clave = "Clave12345"))
        assertEquals("Clave12345", claves.single())
        assertNull(respuesta.cuentaSulotec)
        verify(exactly = 0) { cliente.asegurarCuenta(any(), any(), any(), any()) }
    }

    @Test
    fun `una empresa sin licencia en Sulotec no esta en modo Sulotec`() {
        every { licencias.licenciaDe("u", any()) } returns LicenciaRemota.SIN_LICENCIA
        assertNull(servicio().crear(solicitud(clave = "Clave12345")).cuentaSulotec)
        verify(exactly = 0) { cliente.asegurarCuenta(any(), any(), any(), any()) }
    }

    // ---------- Reinvitación ----------

    private fun cuenta(empresa: String) = Usuario(id = 9, usuario = "plz.luis", clave = "x", correo = "luis@ejemplo.com",
        nombres = "Luis", apePat = "Rojas", empresa = empresa, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora)

    private fun jefa(empresa: String) = UsernamePasswordAuthenticationToken("jefa", null, listOf(SimpleGrantedAuthority("ROLE_ADMINISTRADOR_EMPRESA"))).also {
        every { usuarios.findByUsuario("jefa") } returns cuenta(empresa).apply { usuario = "jefa" }
    }

    @Test
    fun `reinvitar vuelve a pedir la cuenta con reinvitar true`() {
        every { usuarios.findById(9) } returns Optional.of(cuenta("Peru Luz S.A.C"))
        every { contexto.uuidDeEmpresa("Peru Luz S.A.C") } returns "u"
        every { cliente.asegurarCuenta("u", "luis@ejemplo.com", "Luis Rojas", true) } returns
            CuentaRemota(estado = "existente", invitacion = "correo", mensaje = "Se volvió a enviar la invitación")

        val respuesta = servicio().reinvitarSulotec(9, jefa(" peru luz s.a.c "))

        assertEquals("correo", respuesta.cuentaSulotec.invitacion)
        assertEquals("Se volvió a enviar la invitación", respuesta.cuentaSulotec.mensaje)
    }

    @Test
    fun `reinvitar una cuenta de otra empresa responde como si no existiera`() {
        every { usuarios.findById(9) } returns Optional.of(cuenta("Otra SAC"))
        assertFailsWith<ResourceNotFoundException> { servicio().reinvitarSulotec(9, jefa("Peru Luz S.A.C")) }
        verify(exactly = 0) { cliente.asegurarCuenta(any(), any(), any(), any()) }
    }

    @Test
    fun `reinvitar fuera del modo Sulotec es 409 CUENTA_SULOTEC_APAGADA`() {
        every { usuarios.findById(9) } returns Optional.of(cuenta("Peru Luz S.A.C"))
        every { contexto.uuidDeEmpresa("Peru Luz S.A.C") } returns "u"
        val error = assertFailsWith<SsoException> { servicio(SsoProperties()).reinvitarSulotec(9, jefa("Peru Luz S.A.C")) }
        assertEquals(409, error.status.value())
        assertEquals(CuentasSulotec.CODE_APAGADA, error.code)
    }

    // ---------- GET /api/licencia ----------

    @Test
    fun `la licencia dice si la empresa usa cuentas Sulotec`() {
        val controller = LicenciaController(contexto, licencias, CuentasSulotec(ssoActivo, licencias, cliente))
        val auth = jefa("Peru Luz S.A.C")
        every { contexto.de(auth) } returns ContextoLicencia("jefa", "Peru Luz S.A.C", "u", false)
        assertTrue(controller.obtener(auth, null).cuentasSulotec)

        val admin = UsernamePasswordAuthenticationToken("admin", null, listOf(SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
        every { contexto.de(admin) } returns ContextoLicencia("admin", null, null, true)
        assertTrue(controller.obtener(admin, null).cuentasSulotec)

        val apagado = LicenciaController(contexto, licencias, CuentasSulotec(SsoProperties(), licencias, cliente))
        assertFalse(apagado.obtener(auth, null).cuentasSulotec)
    }
}
