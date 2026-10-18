package com.security.shared

import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.Alcance
import com.security.shared.security.VisibilidadEmpresaService
import com.security.shared.security.mismaEmpresa
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.*

class VisibilidadEmpresaTest {
    private val usuarios = mockk<UsuarioRepository>()
    private val visibilidad = VisibilidadEmpresaService(usuarios)

    private fun cuenta(id: Int, nombre: String, empresa: String?, rol: String = "USUARIO", rolActivo: Boolean = true): Usuario {
        val ahora = LocalDateTime.now()
        val u = Usuario(id = id, usuario = nombre, clave = "x", correo = "$nombre@example.test", empresa = empresa,
            fechaCreacion = ahora, fechaActualizacion = ahora)
        u.roles = mutableListOf(UsuarioRol(usuario = u, rol = Roles(id = 1, codigo = rol, nombre = rol, activo = rolActivo,
            fechaCreacion = ahora, fechaActualizacion = ahora)))
        return u
    }

    @Test
    fun `alcance segun el rol - comun propio, administrador de empresa su empresa, administrador todo`() {
        assertEquals(Alcance.PROPIO, visibilidad.de(cuenta(1, "ana", "Gamma")).alcance)
        assertEquals(Alcance.PROPIO, visibilidad.de(cuenta(1, "ana", "Gamma", rol = "SUPERVISOR")).alcance)
        assertEquals(Alcance.EMPRESA, visibilidad.de(cuenta(2, "jefa", " Gamma ", rol = "ADMINISTRADOR_EMPRESA")).alcance)
        assertEquals("Gamma", visibilidad.de(cuenta(2, "jefa", " Gamma ", rol = "ADMINISTRADOR_EMPRESA")).empresa)
        assertEquals(Alcance.TODO, visibilidad.de(cuenta(3, "sulotec", null, rol = "ADMINISTRADOR")).alcance)
    }

    @Test
    fun `administrador de empresa sin empresa o con rol inactivo queda en propio`() {
        assertEquals(Alcance.PROPIO, visibilidad.de(cuenta(2, "jefa", "  ", rol = "ADMINISTRADOR_EMPRESA")).alcance)
        assertEquals(Alcance.PROPIO, visibilidad.de(cuenta(3, "ex", "Gamma", rol = "ADMINISTRADOR", rolActivo = false)).alcance)
    }

    @Test
    fun `puedeVer respeta el alcance`() {
        val mia = cuenta(1, "ana", "Gamma")
        val colega = cuenta(5, "maria", "gamma")
        val ajeno = cuenta(6, "luis", "Delta")

        val propio = visibilidad.de(mia)
        assertTrue(propio.puedeVer(mia))
        assertFalse(propio.puedeVer(colega))
        assertFalse(propio.puedeVer(null))

        val empresa = visibilidad.de(cuenta(2, "jefa", "Gamma", rol = "ADMINISTRADOR_EMPRESA"))
        assertTrue(empresa.puedeVer(colega))
        assertFalse(empresa.puedeVer(ajeno))
        assertFalse(empresa.puedeVer(cuenta(7, "sin", null)))

        val todo = visibilidad.de(cuenta(3, "sulotec", null, rol = "ADMINISTRADOR"))
        assertTrue(todo.puedeVer(ajeno))
        assertTrue(todo.puedeVer(null))
    }

    @Test
    fun `por nombre de cuenta carga la cuenta o falla si no existe`() {
        every { usuarios.findByUsuario("ana") } returns cuenta(1, "ana", "Gamma")
        every { usuarios.findByUsuario("nadie") } returns null
        assertEquals(1, visibilidad.de("ana").usuario.id)
        assertFailsWith<ResourceNotFoundException> { visibilidad.de("nadie") }
    }

    @Test
    fun `mismaEmpresa ignora mayusculas y espacios y nunca acepta vacios`() {
        assertTrue(mismaEmpresa(" Gamma ", "gamma"))
        assertFalse(mismaEmpresa("Gamma", "Delta"))
        assertFalse(mismaEmpresa(null, null))
        assertFalse(mismaEmpresa("  ", "  "))
    }
}
