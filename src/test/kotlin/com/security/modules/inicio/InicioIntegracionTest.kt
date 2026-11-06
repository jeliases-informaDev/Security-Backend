package com.security.modules.inicio

import com.security.modules.auth.security.JwtService
import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.modules.listas.domain.ConsultaLista
import com.security.modules.listas.infrastructure.ConsultaListaRepository
import com.security.modules.oficial.aplicacion.RelojOficial
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import jakarta.servlet.Filter
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import com.jayway.jsonpath.JsonPath

/**
 * Tablero de Inicio de punta a punta contra la MySQL del CI (rutas, token y filtro de seguridad reales). Cada prueba crea
 * su propia empresa y sus cuentas (datos ficticios). Hoy fijo: viernes 06/11/2026, 12:00 de Lima.
 */
@SpringBootTest(properties = ["OC_CLAVE_CIFRADO=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="])
class InicioIntegracionTest {

    @Autowired lateinit var wac: WebApplicationContext
    @Autowired lateinit var usuarios: UsuarioRepository
    @Autowired lateinit var empresas: EmpresaRepository
    @Autowired lateinit var roles: RolesRepository
    @Autowired lateinit var usuarioRoles: UsuarioRolRepository
    @Autowired lateinit var jwt: JwtService
    @Autowired lateinit var reloj: RelojInicio
    @Autowired lateinit var relojOficial: RelojOficial
    @Autowired lateinit var consultas: ConsultaListaRepository
    @Autowired lateinit var denuncias: DenunciaRepository

    private lateinit var mvc: MockMvc
    private val hoy = Instant.parse("2026-11-06T17:00:00Z")

    @BeforeEach
    fun preparar() {
        val cadena = wac.getBean("springSecurityFilterChain", Filter::class.java)
        mvc = MockMvcBuilders.webAppContextSetup(wac)
            .addFilters<DefaultMockMvcBuilder>(cadena)
            .defaultResponseCharacterEncoding<DefaultMockMvcBuilder>(StandardCharsets.UTF_8)
            .build()
        reloj.clock = Clock.fixed(hoy, RelojInicio.ZONA)
        relojOficial.clock = Clock.fixed(hoy, RelojOficial.ZONA)
    }

    @AfterEach
    fun restaurar() {
        reloj.clock = Clock.system(RelojInicio.ZONA)
        relojOficial.clock = Clock.system(RelojOficial.ZONA)
    }

    // ------------------------------------------------------------ ayudas

    private fun sufijo() = UUID.randomUUID().toString().replace("-", "").take(10)

    private fun empresa(): String {
        val ahora = LocalDateTime.now()
        return empresas.save(
            Empresa(nombre = "Empresa Ficticia Inicio ${sufijo()}", uuid = UUID.randomUUID().toString(), activa = true,
                fechaCreacion = ahora, fechaActualizacion = ahora)
        ).nombre
    }

    private fun rol(codigo: String): Roles {
        val ahora = LocalDateTime.now()
        return roles.findByCodigo(codigo)
            ?: roles.save(Roles(codigo = codigo, nombre = codigo, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora))
    }

    private fun cuenta(empresa: String?, rolCodigo: String): Pair<Usuario, String> {
        val ahora = LocalDateTime.now()
        val s = sufijo()
        val u = usuarios.save(
            Usuario(usuario = "inicio.$s", clave = "x", correo = "inicio.$s@example.test", nombres = "Prueba", apePat = "Ficticia",
                empresa = empresa, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora)
        )
        val r = rol(rolCodigo)
        usuarioRoles.save(UsuarioRol(usuario = u, rol = r))
        u.roles = mutableListOf(UsuarioRol(usuario = u, rol = r))
        return u to jwt.generateToken(u)
    }

    private fun get(url: String, t: String): ResultActions =
        mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer $t"))

    private fun post(url: String, t: String): ResultActions =
        mvc.perform(MockMvcRequestBuilders.post(url).header("Authorization", "Bearer $t"))

    private fun delete(url: String, t: String): ResultActions =
        mvc.perform(MockMvcRequestBuilders.delete(url).header("Authorization", "Bearer $t"))

    private fun cuerpo(r: ResultActions) = r.andReturn().response.getContentAsString(StandardCharsets.UTF_8)

    /** Un dato real de la empresa (no lleva marca de demostracion): debe sobrevivir a «Quitar». */
    private fun datosReales(u: Usuario, empresa: String) {
        consultas.save(
            ConsultaLista(usuario = u, empresa = empresa, fecha = LocalDateTime.of(2026, 11, 3, 10, 0), tipoConsulta = "CLIENTE",
                criterioTipoDocumento = "DNI", criterioDocumento = "00000009", modo = ConsultaLista.MODO_DOCUMENTO,
                coincidencia = ConsultaLista.COINCIDENCIA_SIN_RESULTADOS, clasificacion = "SIN_COINCIDENCIAS", porcentaje = 0)
        )
        denuncias.save(
            DenunciaEntity(causa = CausaDenuncia.SOBORNO, relacionEmpresa = RelacionEmpresa.CLIENTE, descripcion = "Denuncia real de prueba",
                codigoSeguimiento = "DEN-${sufijo().uppercase()}", receptorUsuario = u, creadoPorUsuario = u,
                fechaCreacion = LocalDateTime.of(2026, 11, 4, 9, 0), fechaActualizacion = LocalDateTime.of(2026, 11, 4, 9, 0))
        )
    }

    // ------------------------------------------------------------ pruebas

    @Test
    fun `sin datos el resumen viene en cero y el universal puede gestionar la demostracion`() {
        val (_, t) = cuenta(empresa(), "ADMINISTRADOR")
        get("/api/inicio/resumen", t)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.periodo").value("2026-11"))
            .andExpect(jsonPath("$.periodoEtiqueta").value("Noviembre 2026"))
            .andExpect(jsonPath("$.demo.cargado").value(false))
            .andExpect(jsonPath("$.demo.puedeGestionar").value(true))
            .andExpect(jsonPath("$.listas.consultas").value(0))
            .andExpect(jsonPath("$.listas.consultasVariacionPct").value(nullValue()))
            .andExpect(jsonPath("$.listas.consultasSerie.length()").value(14))
            .andExpect(jsonPath("$.operaciones.registros").value(0))
            .andExpect(jsonPath("$.oficial.estado").value("SIN_OFICIAL"))
            .andExpect(jsonPath("$.denuncias.recibidas").value(0))
            .andExpect(jsonPath("$.denuncias.plazosATiempoPct").value(nullValue()))
            // Cabina sin datos: todo vacio, sin error
            .andExpect(jsonPath("$.cabina.scoring.evaluaciones").value(0))
            .andExpect(jsonPath("$.cabina.scoring.puntajePromedio").value(nullValue()))
            .andExpect(jsonPath("$.cabina.scoring.radar.length()").value(0))
            .andExpect(jsonPath("$.cabina.scoring.escala.corteMedio").value(1.75))
            .andExpect(jsonPath("$.cabina.scoring.escala.corteAlto").value(2.5))
            .andExpect(jsonPath("$.cabina.tendencia.consultas.length()").value(30))
            .andExpect(jsonPath("$.cabina.tendencia.desde").value("2026-10-08"))
            .andExpect(jsonPath("$.cabina.mapa.departamentos.length()").value(0))
            .andExpect(jsonPath("$.cabina.plazos.length()").value(0))
            .andExpect(jsonPath("$.cabina.licencia").value(nullValue()))
    }

    @Test
    fun `cargar la demostracion reproduce el ejemplo del contrato y quitarla deja solo lo real`() {
        val nombre = empresa()
        val (admin, t) = cuenta(nombre, "ADMINISTRADOR")
        datosReales(admin, nombre)

        post("/api/inicio/demo", t)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cargado").value(true))
            .andExpect(jsonPath("$.yaEstaba").value(false))
            .andExpect(jsonPath("$.creados.consultas").value(609))
            .andExpect(jsonPath("$.creados.registros").value(94))
            .andExpect(jsonPath("$.creados.denuncias").value(33))
            .andExpect(jsonPath("$.creados.oficial").value(1))
        // Idempotente
        post("/api/inicio/demo", t).andExpect(status().isOk).andExpect(jsonPath("$.yaEstaba").value(true))

        // Lo real suma 1 consulta y 1 denuncia en noviembre: el ejemplo del contrato + eso.
        get("/api/inicio/resumen", t)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.demo.cargado").value(true))
            .andExpect(jsonPath("$.listas.consultas").value(313))
            .andExpect(jsonPath("$.listas.consultasVariacionPct").value(5.0))
            .andExpect(jsonPath("$.listas.conCoincidenciaPct").value(23.0))
            .andExpect(jsonPath("$.listas.pepDetectados").value(19))
            .andExpect(jsonPath("$.listas.homonimosDescartados").value(11))
            .andExpect(jsonPath("$.listas.resultado.criticaPct").value(4.0))
            .andExpect(jsonPath("$.listas.resultado.altaPct").value(8.0))
            .andExpect(jsonPath("$.listas.resultado.mediaPct").value(11.0))
            .andExpect(jsonPath("$.listas.resultado.sinCoincidenciaPct").value(77.0))
            .andExpect(jsonPath("$.listas.porFuente[0].fuente").value("Noticias"))
            .andExpect(jsonPath("$.listas.porFuente[0].coincidencias").value(29))
            .andExpect(jsonPath("$.listas.porFuente[1].fuente").value("PEP · Perú"))
            .andExpect(jsonPath("$.listas.porFuente[1].coincidencias").value(19))
            .andExpect(jsonPath("$.listas.criticasSinDecidir.cantidad").value(2))
            .andExpect(jsonPath("$.listas.criticasSinDecidir.masAntiguaDias").value(1))
            .andExpect(jsonPath("$.listas.consultasSerie.length()").value(14))
            .andExpect(jsonPath("$.operaciones.registros").value(64))
            .andExpect(jsonPath("$.operaciones.montoTotalUsd").value(closeTo(2400000.0, 5000.0)))
            .andExpect(jsonPath("$.operaciones.inusuales").value(3))
            .andExpect(jsonPath("$.operaciones.sobreUmbral").value(19))
            .andExpect(jsonPath("$.operaciones.mismoDiaPct").value(91.0))
            .andExpect(jsonPath("$.operaciones.metaMismoDiaPct").value(95.0))
            .andExpect(jsonPath("$.operaciones.ultimos.length()").value(3))
            .andExpect(jsonPath("$.operaciones.ultimos[0].persona").value("Rojas Silva, Pedro"))
            .andExpect(jsonPath("$.operaciones.ultimos[0].tipo").value("Venta de departamento"))
            .andExpect(jsonPath("$.operaciones.ultimos[0].estado").value("SIN_RESPONSABLE"))
            .andExpect(jsonPath("$.operaciones.ultimos[1].estado").value("SIN_RESPONSABLE"))
            .andExpect(jsonPath("$.operaciones.ultimos[2].moneda").value("PEN"))
            .andExpect(jsonPath("$.operaciones.ultimos[2].estado").value("INUSUAL"))
            .andExpect(jsonPath("$.oficial.estado").value("VIGENTE"))
            .andExpect(jsonPath("$.oficial.nombre").value("Mariana Quispe Valdivia"))
            .andExpect(jsonPath("$.oficial.cargo").value("TITULAR"))
            .andExpect(jsonPath("$.oficial.alDia").value(true))
            .andExpect(jsonPath("$.oficial.comunicadoUif.fecha").value("2026-03-20"))
            .andExpect(jsonPath("$.oficial.comunicadoUif.enPlazo").value(true))
            .andExpect(jsonPath("$.oficial.declaraciones.vigentes").value(8))
            .andExpect(jsonPath("$.oficial.declaraciones.total").value(8))
            .andExpect(jsonPath("$.oficial.alterno").value(nullValue()))
            .andExpect(jsonPath("$.denuncias.recibidas").value(25))
            .andExpect(jsonPath("$.denuncias.porAtender").value(6))
            .andExpect(jsonPath("$.denuncias.sinAbrir").value(3))
            .andExpect(jsonPath("$.denuncias.vencidas").value(1))
            .andExpect(jsonPath("$.denuncias.plazosATiempoPct").value(82.0))
            .andExpect(jsonPath("$.denuncias.tiempoPromedioCierreDias").value(9))
            .andExpect(jsonPath("$.denuncias.porSituacion[0].situacion").value("Conflicto de intereses"))
            .andExpect(jsonPath("$.denuncias.porSituacion[0].cantidad").value(8))
            .andExpect(jsonPath("$.denuncias.porSituacion.length()").value(6))
            .andExpect(jsonPath("$.denuncias.cierres.fundadaPct").value(40.0))
            .andExpect(jsonPath("$.denuncias.cierres.infundadaPct").value(33.0))
            .andExpect(jsonPath("$.denuncias.cierres.archivadaPct").value(27.0))
            .andExpect(jsonPath("$.denuncias.derivadasAlOficial").value(2))

        // Cabina con la demostracion: todos los instrumentos llenos.
        val conDemo = cuerpo(get("/api/inicio/resumen", t).andExpect(status().isOk))
        println("CABINA DEMO: " + JsonPath.read<Any>(conDemo, "$.cabina").toString().take(3000))
        assertEquals(8, JsonPath.read<List<Any>>(conDemo, "$.cabina.scoring.radar").size)
        val evaluaciones = JsonPath.read<Number>(conDemo, "$.cabina.scoring.evaluaciones").toLong()
        assertTrue(evaluaciones > 0)
        val niveles = listOf("bajo", "medio", "alto").sumOf { JsonPath.read<Number>(conDemo, "$.cabina.scoring.porNivel.$it").toLong() }
        assertEquals(evaluaciones, niveles)
        assertTrue(JsonPath.read<Number>(conDemo, "$.cabina.scoring.porNivel.alto").toLong() > 0)
        listOf("consultas", "coincidencias", "registros", "evaluaciones", "denuncias").forEach { serie ->
            val valores = JsonPath.read<List<Number>>(conDemo, "$.cabina.tendencia.$serie")
            assertEquals(30, valores.size, serie)
            assertTrue(valores.any { it.toLong() > 0 }, serie)
        }
        val codigos = JsonPath.read<List<String?>>(conDemo, "$.cabina.mapa.departamentos[*].codigo")
        assertTrue(codigos.size >= 8, "departamentos: $codigos")
        assertTrue(codigos.all { it != null && Regex("(0[1-9]|1[0-9]|2[0-5])").matches(it) }, "codigos: $codigos")
        val dias = JsonPath.read<List<Number>>(conDemo, "$.cabina.plazos[*].dias").map { it.toLong() }
        assertTrue(dias.isNotEmpty() && dias.size <= 6)
        assertEquals(dias.sorted(), dias)
        assertTrue(JsonPath.read<List<String>>(conDemo, "$.cabina.plazos[*].tipo").contains("DENUNCIA"))

        // Otra empresa no ve nada de esto.
        val (_, otra) = cuenta(empresa(), "ADMINISTRADOR")
        get("/api/inicio/resumen", otra)
            .andExpect(jsonPath("$.demo.cargado").value(false))
            .andExpect(jsonPath("$.listas.consultas").value(0))
            .andExpect(jsonPath("$.operaciones.registros").value(0))
            .andExpect(jsonPath("$.denuncias.recibidas").value(0))

        delete("/api/inicio/demo", t)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cargado").value(false))
            .andExpect(jsonPath("$.eliminados.consultas").value(609))
            .andExpect(jsonPath("$.eliminados.registros").value(94))
            .andExpect(jsonPath("$.eliminados.denuncias").value(33))
            .andExpect(jsonPath("$.eliminados.oficial").value(1))

        get("/api/inicio/resumen", t)
            .andExpect(jsonPath("$.demo.cargado").value(false))
            .andExpect(jsonPath("$.listas.consultas").value(1))
            .andExpect(jsonPath("$.listas.pepDetectados").value(0))
            .andExpect(jsonPath("$.operaciones.registros").value(0))
            .andExpect(jsonPath("$.oficial.estado").value("SIN_OFICIAL"))
            .andExpect(jsonPath("$.denuncias.recibidas").value(1))
            .andExpect(jsonPath("$.denuncias.vencidas").value(0))
            .andExpect(jsonPath("$.denuncias.derivadasAlOficial").value(0))
            .andExpect(jsonPath("$.denuncias.cierres").value(nullValue()))
        assertTrue(denuncias.findAll().any { it.codigoSeguimiento?.startsWith("DEN-") == true && it.creadoPorUsuario?.id == admin.id })
    }

    @Test
    fun `solo el administrador universal gestiona la demostracion y cada bloque respeta los permisos`() {
        val nombre = empresa()
        val (_, adminEmpresa) = cuenta(nombre, "ADMINISTRADOR_EMPRESA")
        val (_, usuario) = cuenta(nombre, "USUARIO")

        post("/api/inicio/demo", adminEmpresa)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("DEMO_SIN_PERMISO"))
        delete("/api/inicio/demo", usuario).andExpect(status().isForbidden)

        get("/api/inicio/resumen", adminEmpresa)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.demo.puedeGestionar").value(false))
            .andExpect(jsonPath("$.cabina.scoring").exists())
            .andExpect(jsonPath("$.denuncias.recibidas").value(0))
        // Una cuenta comun no gestiona denuncias: ese bloque no viene.
        get("/api/inicio/resumen", usuario)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.denuncias").value(nullValue()))
            .andExpect(jsonPath("$.cabina").value(nullValue()))
            .andExpect(jsonPath("$.listas.consultas").value(0))
        get("/api/inicio/resumen?periodo=2026-13", usuario)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INICIO_PERIODO_INVALIDO"))
    }
}
