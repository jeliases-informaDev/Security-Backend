package com.security.modules.auth.sso

import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.modules.licencias.dominio.TitularRemoto
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Cada rama de quién entra con la cuenta Sulotec, sin red ni base. */
class SsoReglasTest {

    private val uuid = "11111111-2222-3333-4444-555555555555"

    private fun identidad(
        correo: String? = "ana@ejemplo.com",
        verificado: Boolean? = true,
        roles: Set<String> = emptySet(),
        empresaUuid: String? = null,
        empresaNombre: String? = null
    ) = SsoIdentidad(correo, verificado, "Ana", "Torres", roles, empresaUuid, empresaNombre)

    private fun licencia(titular: String? = "ana@ejemplo.com", nombre: String? = "Acme SAC") =
        LicenciaRemota(estado = "ACTIVA", titular = TitularRemoto(email = titular, nombre = "Ana Torres"), empresaNombre = nombre)

    private val sinLicencias: (String) -> LicenciaRemota? = { null }
    private val prohibido: (String) -> LicenciaRemota? = { fail("no debía preguntar la licencia") }

    private fun codigoDe(bloque: () -> Unit): String = assertFailsWith<SsoException> { bloque() }.code

    // ---------- Correo ----------

    @Test
    fun `sin correo no entra`() {
        assertEquals(SsoReglas.SIN_CORREO, codigoDe { SsoReglas.decidir(identidad(correo = " "), null, prohibido) })
    }

    @Test
    fun `solo se rechaza el correo cuando el token dice false explicito`() {
        assertEquals(SsoReglas.CORREO_NO_VERIFICADO, codigoDe { SsoReglas.decidir(identidad(verificado = false), null, prohibido) })
        val local = CuentaLocalSso("acme.ana", true, "USUARIO")
        assertEquals(DecisionSso.Entrar("acme.ana"), SsoReglas.decidir(identidad(verificado = null), local, prohibido))
    }

    // ---------- Con usuario en PLAFT ----------

    @Test
    fun `usuario inactivo no entra`() {
        val local = CuentaLocalSso("acme.ana", false, "USUARIO")
        assertEquals(SsoReglas.CUENTA_INACTIVA, codigoDe { SsoReglas.decidir(identidad(), local, prohibido) })
    }

    @Test
    fun `usuario activo entra con lo de PLAFT aunque el token no traiga nada`() {
        val local = CuentaLocalSso("acme.ana", true, "ADMINISTRADOR_EMPRESA")
        assertEquals(DecisionSso.Entrar("acme.ana"), SsoReglas.decidir(identidad(), local, prohibido))
    }

    @Test
    fun `doble llave para el administrador de Sulotec`() {
        val local = CuentaLocalSso("admin", true, "ADMINISTRADOR")
        assertEquals(SsoReglas.SIN_PERMISO, codigoDe { SsoReglas.decidir(identidad(roles = setOf("producto-plaft")), local, prohibido) })
        assertEquals(DecisionSso.Entrar("admin"), SsoReglas.decidir(identidad(roles = setOf("administrador-general")), local, prohibido))
    }

    @Test
    fun `usuario sin rol no entra`() {
        val local = CuentaLocalSso("acme.ana", true, null)
        assertEquals(SsoReglas.SIN_PERMISO, codigoDe { SsoReglas.decidir(identidad(), local, prohibido) })
    }

    // ---------- Sin usuario en PLAFT ----------

    @Test
    fun `administrador universal sin cuenta se crea solo`() {
        assertEquals(
            DecisionSso.NuevoAdministradorSulotec,
            SsoReglas.decidir(identidad(roles = setOf("administrador-general")), null, prohibido)
        )
    }

    @Test
    fun `el titular de la licencia tiene alta automatica sin distinguir mayusculas`() {
        val preguntadas = mutableListOf<String>()
        val decision = SsoReglas.decidir(
            identidad(correo = "Ana@Ejemplo.com", roles = setOf("producto-plaft"), empresaUuid = uuid.uppercase()),
            null
        ) { preguntadas += it; licencia(titular = "ANA@ejemplo.com") }
        assertEquals(DecisionSso.NuevoTitular(uuid, "Acme SAC"), decision)
        assertEquals(listOf(uuid), preguntadas)
    }

    @Test
    fun `sin nombre en la licencia se usa el del token`() {
        val decision = SsoReglas.decidir(
            identidad(roles = setOf("producto-plaft"), empresaUuid = uuid, empresaNombre = "Acme del token"),
            null
        ) { licencia(nombre = null) }
        assertEquals(DecisionSso.NuevoTitular(uuid, "Acme del token"), decision)
    }

    @Test
    fun `sin licencia o con licencias apagadas no entra`() {
        val conPlaft = identidad(roles = setOf("producto-plaft"), empresaUuid = uuid)
        assertEquals(SsoReglas.SIN_LICENCIA, codigoDe { SsoReglas.decidir(conPlaft, null, sinLicencias) })
        assertEquals(SsoReglas.SIN_LICENCIA, codigoDe { SsoReglas.decidir(conPlaft, null) { LicenciaRemota.SIN_LICENCIA } })
    }

    @Test
    fun `quien no es el titular no tiene cuenta`() {
        val error = assertFailsWith<SsoException> {
            SsoReglas.decidir(identidad(roles = setOf("producto-plaft"), empresaUuid = uuid), null) { licencia(titular = "jefe@ejemplo.com") }
        }
        assertEquals(SsoReglas.SIN_CUENTA, error.code)
        assertEquals(403, error.status.value())
        assertEquals(SsoReglas.MENSAJE_SIN_CUENTA, error.message)
        // Una licencia sin titular tampoco da alta.
        assertEquals(SsoReglas.SIN_CUENTA, codigoDe {
            SsoReglas.decidir(identidad(roles = setOf("producto-plaft"), empresaUuid = uuid), null) { licencia(titular = null) }
        })
    }

    @Test
    fun `sin producto-plaft o sin empresa no tiene cuenta`() {
        assertEquals(SsoReglas.SIN_CUENTA, codigoDe { SsoReglas.decidir(identidad(empresaUuid = uuid), null, prohibido) })
        assertEquals(SsoReglas.SIN_CUENTA, codigoDe { SsoReglas.decidir(identidad(roles = setOf("producto-plaft")), null, prohibido) })
    }

    @Test
    fun `el usuario sale de la parte local del correo`() {
        assertEquals("ana.torres", SsoReglas.usuarioBase("Ana.Torres@ejemplo.com"))
        assertEquals("jose", SsoReglas.usuarioBase("josé@ejemplo.com"))
        assertEquals("usuario", SsoReglas.usuarioBase("@ejemplo.com"))
    }
}
