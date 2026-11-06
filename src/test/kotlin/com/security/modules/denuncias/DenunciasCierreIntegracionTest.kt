package com.security.modules.denuncias

import com.jayway.jsonpath.JsonPath
import com.security.modules.auth.security.JwtService
import com.security.modules.denuncias.application.service.EsquemaDenunciasInicializador
import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import jakarta.servlet.Filter
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.lessThan
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cierre con resultado, derivacion reservada al oficial y plazo de atencion (docs/DENUNCIAS-CIERRE.md), de punta a punta
 * contra la MySQL del CI. Cada prueba crea su empresa y sus cuentas (datos ficticios).
 */
@SpringBootTest(properties = ["OC_CLAVE_CIFRADO=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="])
class DenunciasCierreIntegracionTest {

    @Autowired lateinit var wac: WebApplicationContext
    @Autowired lateinit var usuarios: UsuarioRepository
    @Autowired lateinit var roles: RolesRepository
    @Autowired lateinit var usuarioRoles: UsuarioRolRepository
    @Autowired lateinit var jwt: JwtService
    @Autowired lateinit var denuncias: DenunciaRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var esquema: EsquemaDenunciasInicializador

    private lateinit var mvc: MockMvc

    @BeforeEach
    fun preparar() {
        val cadena = wac.getBean("springSecurityFilterChain", Filter::class.java)
        mvc = MockMvcBuilders.webAppContextSetup(wac)
            .addFilters<DefaultMockMvcBuilder>(cadena)
            .defaultResponseCharacterEncoding<DefaultMockMvcBuilder>(StandardCharsets.UTF_8)
            .build()
    }

    // ------------------------------------------------------------ ayudas

    private fun sufijo() = UUID.randomUUID().toString().replace("-", "").take(10)

    private fun rol(codigo: String): Roles {
        val ahora = LocalDateTime.now()
        return roles.findByCodigo(codigo)
            ?: roles.save(Roles(codigo = codigo, nombre = codigo, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora))
    }

    private fun cuenta(empresa: String?, rolCodigo: String): Pair<Usuario, String> {
        val ahora = LocalDateTime.now()
        val s = sufijo()
        val u = usuarios.save(
            Usuario(usuario = "den.$s", clave = "x", correo = "den.$s@example.test", nombres = "Prueba", apePat = "Ficticia",
                empresa = empresa, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora)
        )
        val r = rol(rolCodigo)
        usuarioRoles.save(UsuarioRol(usuario = u, rol = r))
        u.roles = mutableListOf(UsuarioRol(usuario = u, rol = r))
        return u to jwt.generateToken(u)
    }

    private fun denuncia(receptor: Usuario, creada: LocalDateTime = LocalDateTime.now(), estado: EstadoDenuncia = EstadoDenuncia.RECIBIDO): Int =
        denuncias.save(
            DenunciaEntity(causa = CausaDenuncia.FRAUDE, relacionEmpresa = RelacionEmpresa.TRABAJADOR, descripcion = "Denuncia ficticia de prueba",
                codigoSeguimiento = "DEN-T${sufijo().uppercase()}", receptorUsuario = receptor, creadoPorUsuario = receptor, anonima = true,
                aceptaTerminos = true, estado = estado, fechaCreacion = creada, fechaActualizacion = creada)
        ).id!!

    private fun get(url: String, t: String): ResultActions =
        mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer $t"))

    private fun patch(url: String, t: String, json: String): ResultActions =
        mvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer $t").contentType(MediaType.APPLICATION_JSON).content(json))

    private fun post(url: String, t: String, json: String): ResultActions =
        mvc.perform(MockMvcRequestBuilders.post(url).header("Authorization", "Bearer $t").contentType(MediaType.APPLICATION_JSON).content(json))

    private fun cuerpo(r: ResultActions) = r.andReturn().response.getContentAsString(StandardCharsets.UTF_8)

    // ------------------------------------------------------------ pruebas

    @Test
    fun `el catalogo trae las 6 situaciones y los resultados con sus etiquetas`() {
        val (_, t) = cuenta("Empresa Ficticia Den ${sufijo()}", "ADMINISTRADOR_EMPRESA")
        get("/api/denuncias/catalogo", t)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.situaciones.length()").value(6))
            .andExpect(jsonPath("$.situaciones[3].codigo").value("FRAUDE"))
            .andExpect(jsonPath("$.situaciones[3].nombre").value("Fraude"))
            .andExpect(jsonPath("$.resultadosCierre[2].codigo").value("ARCHIVADA"))
            .andExpect(jsonPath("$.resultadosCierre[2].pideMotivo").value(true))
            .andExpect(jsonPath("$.plazoAtencionDias").value(30))
    }

    @Test
    fun `cerrar exige resultado y el motivo solo con archivada`() {
        val empresa = "Empresa Ficticia Den ${sufijo()}"
        val (supervisor, _) = cuenta(empresa, "SUPERVISOR")
        val (_, admin) = cuenta(empresa, "ADMINISTRADOR_EMPRESA")
        val id = denuncia(supervisor)
        val url = "/api/denuncias/$id/estado"

        patch(url, admin, """{ "estado": "EN_PROCESO", "resultadoCierre": "FUNDADA" }""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("DENUNCIA_RESULTADO_SOLO_AL_CERRAR"))
        patch(url, admin, """{ "estado": "EN_PROCESO" }""").andExpect(status().isOk)
            .andExpect(jsonPath("$.diasParaVencer").value(30)).andExpect(jsonPath("$.vencida").value(false))
        patch(url, admin, """{ "estado": "CERRADO" }""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("DENUNCIA_RESULTADO_OBLIGATORIO"))
        patch(url, admin, """{ "estado": "CERRADO", "resultadoCierre": "ARCHIVADA", "motivoCierre": "  " }""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("DENUNCIA_MOTIVO_OBLIGATORIO"))
        patch(url, admin, """{ "estado": "CERRADO", "resultadoCierre": "ARCHIVADA", "motivoCierre": "${"x".repeat(1001)}" }""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("DENUNCIA_MOTIVO_LARGO"))
        patch(url, admin, """{ "estado": "CERRADO", "resultadoCierre": "ARCHIVADA", "motivoCierre": "Sin elementos para continuar" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("CERRADO"))
            .andExpect(jsonPath("$.estadoEtiqueta").value("Cerrada"))
            .andExpect(jsonPath("$.resultadoCierre").value("ARCHIVADA"))
            .andExpect(jsonPath("$.resultadoCierreEtiqueta").value("Archivada"))
            .andExpect(jsonPath("$.motivoCierre").value("Sin elementos para continuar"))
            .andExpect(jsonPath("$.atendidaATiempo").value(true))
            .andExpect(jsonPath("$.diasParaVencer").doesNotExist())
            .andExpect(jsonPath("$.causaEtiqueta").value("Fraude"))
            .andExpect(jsonPath("$.historial[1].comentario").value(containsString("Resultado del cierre: Archivada")))
    }

    @Test
    fun `derivar al oficial es reservado e idempotente`() {
        val empresa = "Empresa Ficticia Den ${sufijo()}"
        val (supervisor, tSupervisor) = cuenta(empresa, "SUPERVISOR")
        val (_, otroSupervisor) = cuenta(empresa, "SUPERVISOR")
        val (_, usuarioComun) = cuenta(empresa, "USUARIO")
        val (_, admin) = cuenta(empresa, "ADMINISTRADOR_EMPRESA")
        val (_, ajeno) = cuenta("Otra Empresa Ficticia ${sufijo()}", "ADMINISTRADOR_EMPRESA")
        val id = denuncia(supervisor)
        val url = "/api/denuncias/$id/derivar-oficial"

        post(url, otroSupervisor, "{}").andExpect(status().isNotFound)
        post(url, ajeno, "{}").andExpect(status().isNotFound)
        post(url, usuarioComun, "{}").andExpect(status().isForbidden)

        post(url, admin, """{ "motivo": "Posible relación con una operación inusual" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.derivadaAlOficial").value(true))
            .andExpect(jsonPath("$.derivacion.motivo").value("Posible relación con una operación inusual"))
            .andExpect(jsonPath("$.derivacion.fechaDerivacion").exists())
            .andExpect(jsonPath("$.derivacion.derivadaPor").value("Prueba Ficticia"))
        // Idempotente: no cambia ni duplica la linea del historial.
        val segunda = cuerpo(post(url, admin, """{ "motivo": "otro" }""").andExpect(status().isOk))
        assertEquals("Posible relación con una operación inusual", JsonPath.read<String>(segunda, "$.derivacion.motivo"))
        val lineas = JsonPath.read<List<Any?>>(segunda, "$.historial[?(@.comentario == 'Derivada para revisión reservada')].usuario")
        assertEquals(1, lineas.size)
        assertEquals(null, lineas[0])

        // El receptor (supervisor) gestiona la denuncia pero no ve lo reservado, ni en el detalle ni en el listado.
        get("/api/denuncias/$id", tSupervisor).andExpect(status().isOk)
            .andExpect(jsonPath("$.derivadaAlOficial").doesNotExist())
            .andExpect(jsonPath("$.derivacion").doesNotExist())
        get("/api/denuncias", tSupervisor).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].derivadaAlOficial").doesNotExist())
            .andExpect(jsonPath("$[0].causa").value("FRAUDE"))
            .andExpect(jsonPath("$[0].fechaLimiteAtencion").exists())
        val lista = cuerpo(get("/api/denuncias", admin).andExpect(status().isOk))
        assertEquals(listOf(true), JsonPath.read<List<Boolean>>(lista, "$[?(@.id == $id)].derivadaAlOficial"))
        assertTrue(!lista.contains("Posible relación"), "el listado no trae el motivo reservado")
    }

    @Test
    fun `plazo vencido`() {
        val empresa = "Empresa Ficticia Den ${sufijo()}"
        val (supervisor, _) = cuenta(empresa, "SUPERVISOR")
        val (_, admin) = cuenta(empresa, "ADMINISTRADOR_EMPRESA")
        val id = denuncia(supervisor, LocalDateTime.now().minusDays(40), EstadoDenuncia.EN_PROCESO)
        get("/api/denuncias/$id", admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.vencida").value(true))
            .andExpect(jsonPath("$.diasParaVencer").value(lessThan(0)))
            .andExpect(jsonPath("$.atendidaATiempo").doesNotExist())
    }

    @Test
    fun `una columna ENUM vieja pasa a texto sin perder datos`() {
        val tabla = "den_prueba_enum_${sufijo()}"
        jdbc.execute("CREATE TABLE $tabla (id INT PRIMARY KEY, causa ENUM('SOBORNO','CONFLICTO','ACOSO') NOT NULL)")
        try {
            jdbc.update("INSERT INTO $tabla (id, causa) VALUES (1, 'CONFLICTO')")
            assertTrue(esquema.aTexto(tabla, "causa", 50))
            assertTrue(!esquema.aTexto(tabla, "causa", 50), "la segunda vez no cambia nada")
            jdbc.update("INSERT INTO $tabla (id, causa) VALUES (2, 'OPERACION_SOSPECHOSA')")
            assertEquals(listOf("CONFLICTO", "OPERACION_SOSPECHOSA"), jdbc.queryForList("SELECT causa FROM $tabla ORDER BY id", String::class.java))
        } finally {
            jdbc.execute("DROP TABLE $tabla")
        }
    }
}
