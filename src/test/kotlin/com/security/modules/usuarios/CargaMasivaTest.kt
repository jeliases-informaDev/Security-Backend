package com.security.modules.usuarios

import com.security.modules.auth.application.service.NotificationService
import com.security.modules.auth.application.service.PasswordResetService
import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.usuarios.application.service.CargaMasivaService
import com.security.modules.usuarios.dto.CargaMasivaRequest
import com.security.modules.usuarios.dto.CuentaMasivaRequest
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import com.security.shared.security.siglaLibre
import com.security.shared.security.siglaSugerida
import com.security.shared.security.usuarioConSigla
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

class CargaMasivaTest {
    private val usuarios = mockk<UsuarioRepository>()
    private val roles = mockk<RolesRepository>()
    private val usuarioRoles = mockk<UsuarioRolRepository>(relaxed = true)
    private val encoder = mockk<PasswordEncoder>()
    private val empresaService = mockk<EmpresaService>()
    private val reset = mockk<PasswordResetService>()
    private val correo = mockk<NotificationService>()
    private val service = CargaMasivaService(usuarios, roles, usuarioRoles, encoder, empresaService, reset, correo)

    private val ahora = LocalDateTime.now()
    private fun rol(codigo: String) = Roles(id = 1, codigo = codigo, nombre = codigo, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora)

    private fun preparar(porCorreo: Boolean) {
        every { empresaService.resolverEmpresaActiva("Caja Huancayo") } returns Empresa(id = 7, nombre = "Caja Huancayo", sigla = "CH", uuid = "u")
        every { roles.findAll() } returns listOf(rol("USUARIO"), rol("SUPERVISOR"))
        every { usuarios.existsByCorreo(any()) } returns false
        every { usuarios.existsByUsuario(any()) } returns false
        every { encoder.encode(any()) } returns "hash"
        every { usuarioRoles.save(any()) } answers { firstArg() }
        every { correo.invitacionesPorCorreo() } returns porCorreo
    }

    private fun fila(correo: String, rol: String = "USUARIO", nombres: String = "Juan", apePat: String = "Perez") =
        CuentaMasivaRequest(nombres = nombres, apePat = apePat, correo = correo, rolCodigo = rol)

    @Test
    fun `solo validar revisa fila por fila sin crear nada`() {
        preparar(porCorreo = false)
        every { usuarios.existsByCorreo("ya@caja.pe") } returns true
        val respuesta = service.cargar(CargaMasivaRequest("Caja Huancayo", soloValidar = true, cuentas = listOf(
            fila("JPerez@Caja.pe"), fila("jperez@caja.pe"), fila("malo"), fila("ya@caja.pe"), fila("otro@caja.pe", rol = "NOEXISTE"), fila("sin@caja.pe", nombres = " ")
        )))
        assertEquals(6, respuesta.total)
        assertEquals(1, respuesta.listas)
        assertEquals(5, respuesta.conError)
        assertEquals(listOf("ok", "error", "error", "error", "error", "error"), respuesta.filas.map { it.estado })
        assertEquals("ch.jperez", respuesta.filas[0].usuario)
        assertEquals("El correo se repite en el archivo", respuesta.filas[1].mensaje)
        assertEquals("El correo no es válido", respuesta.filas[2].mensaje)
        assertEquals("Ya existe una cuenta con ese correo", respuesta.filas[3].mensaje)
        assertEquals("El rol 'NOEXISTE' no existe", respuesta.filas[4].mensaje)
        assertEquals("Faltan los nombres", respuesta.filas[5].mensaje)
        verify(exactly = 0) { usuarios.save(any()) }
    }

    @Test
    fun `con un error no se crea ninguna cuenta (todo o nada)`() {
        preparar(porCorreo = false)
        val respuesta = service.cargar(CargaMasivaRequest("Caja Huancayo", cuentas = listOf(fila("a@caja.pe"), fila("malo"))))
        assertEquals(0, respuesta.creadas)
        assertEquals(1, respuesta.conError)
        verify(exactly = 0) { usuarios.save(any()) }
    }

    @Test
    fun `sin correo configurado crea las cuentas con usuario sigla punto correo y devuelve clave temporal`() {
        preparar(porCorreo = false)
        every { usuarios.existsByUsuario("ch.jperez") } returns true
        val guardados = mutableListOf<Usuario>()
        every { usuarios.save(capture(guardados)) } answers { firstArg<Usuario>().also { it.id = guardados.size } }

        val respuesta = service.cargar(CargaMasivaRequest("Caja Huancayo", cuentas = listOf(fila("jperez@caja.pe"), fila("ana.lopez@caja.pe", rol = "SUPERVISOR"))))

        assertEquals(2, respuesta.creadas)
        assertFalse(respuesta.invitacionesPorCorreo)
        // "ch.jperez" ya existia: se agrega un numero
        assertEquals(listOf("ch.jperez2", "ch.ana.lopez"), guardados.map { it.usuario })
        assertEquals("Caja Huancayo", guardados[0].empresa)
        assertEquals(listOf("creada", "creada"), respuesta.filas.map { it.estado })
        assertTrue(respuesta.filas.all { !it.invitacionEnviada && it.claveTemporal != null && it.claveTemporal!!.length == 12 })
        verify(exactly = 0) { reset.crearTokenInvitacion(any(), any()) }
    }

    @Test
    fun `con correo configurado manda la invitacion y no devuelve clave`() {
        preparar(porCorreo = true)
        every { usuarios.save(any()) } answers { firstArg<Usuario>().also { it.id = 9 } }
        every { reset.crearTokenInvitacion(any(), 72L) } returns "token-1"
        every { correo.enviarInvitacion("jperez@caja.pe", "Juan", "Caja Huancayo", "ch.jperez", "token-1", 72L) } just Runs

        val respuesta = service.cargar(CargaMasivaRequest("Caja Huancayo", cuentas = listOf(fila("jperez@caja.pe"))))

        assertTrue(respuesta.filas.single().invitacionEnviada)
        assertNull(respuesta.filas.single().claveTemporal)
        verify(exactly = 1) { correo.enviarInvitacion("jperez@caja.pe", "Juan", "Caja Huancayo", "ch.jperez", "token-1", 72L) }
    }

    @Test
    fun `si el correo falla al enviarse la cuenta igual se crea y vuelve la clave temporal`() {
        preparar(porCorreo = true)
        every { usuarios.save(any()) } answers { firstArg<Usuario>().also { it.id = 9 } }
        every { reset.crearTokenInvitacion(any(), 72L) } returns "token-1"
        every { correo.enviarInvitacion(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("smtp caido")

        val fila = service.cargar(CargaMasivaRequest("Caja Huancayo", cuentas = listOf(fila("jperez@caja.pe")))).filas.single()

        assertEquals("creada", fila.estado)
        assertFalse(fila.invitacionEnviada)
        assertNotNull(fila.claveTemporal)
    }

    @Test
    fun `invitar reenvia el enlace o explica por que no`() {
        val cuenta = Usuario(id = 3, usuario = "ch.ana", clave = "x", correo = "ana@caja.pe", nombres = "Ana", empresa = "Caja Huancayo",
            fechaCreacion = ahora, fechaActualizacion = ahora)
        every { usuarios.findById(3) } returns Optional.of(cuenta)
        every { correo.invitacionesPorCorreo() } returns false
        assertFalse(service.invitar(3).enviada)

        every { correo.invitacionesPorCorreo() } returns true
        every { reset.crearTokenInvitacion(cuenta, 72L) } returns "t"
        every { correo.enviarInvitacion("ana@caja.pe", "Ana", "Caja Huancayo", "ch.ana", "t", 72L) } just Runs
        assertTrue(service.invitar(3).enviada)

        every { usuarios.findById(99) } returns Optional.empty()
        assertFailsWith<com.security.shared.exceptions.ResourceNotFoundException> { service.invitar(99) }
    }

    @Test
    fun `siglas - propuesta, sin choques y usuario con sigla`() {
        assertEquals("CH", siglaSugerida("Caja Huancayo"))
        assertEquals("PL", siglaSugerida("Perú Luz S.A.C"))
        assertEquals("SULO", siglaSugerida("Sulotec"))
        assertEquals("CMAC", siglaSugerida("CMAC"))
        assertEquals("CH2", siglaLibre("CH") { it == "CH" })
        assertEquals("ch.jperez", usuarioConSigla("CH", " JPerez "))
        assertEquals("ch.jperez", usuarioConSigla("ch", "ch.jperez"))
    }
}
