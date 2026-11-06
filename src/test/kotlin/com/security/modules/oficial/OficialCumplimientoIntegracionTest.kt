package com.security.modules.oficial

import com.jayway.jsonpath.JsonPath
import com.security.modules.auth.security.JwtService
import com.security.modules.oficial.aplicacion.RelojOficial
import com.security.modules.oficial.persistencia.OcDesignacionRepository
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import jakarta.servlet.Filter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prueba de punta a punta contra la MySQL del CI: rutas reales, token real, filtro de seguridad real. Cada prueba crea sus
 * propias empresas y cuentas (nombres y documentos ficticios). Hoy fijo: 20/11/2026 (hora de Lima).
 */
@SpringBootTest(properties = ["OC_CLAVE_CIFRADO=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="])
class OficialCumplimientoIntegracionTest {

    @Autowired lateinit var wac: WebApplicationContext
    @Autowired lateinit var usuarios: UsuarioRepository
    @Autowired lateinit var empresas: EmpresaRepository
    @Autowired lateinit var roles: RolesRepository
    @Autowired lateinit var usuarioRoles: UsuarioRolRepository
    @Autowired lateinit var jwt: JwtService
    @Autowired lateinit var reloj: RelojOficial
    @Autowired lateinit var designacionesRepo: OcDesignacionRepository

    private lateinit var mvc: MockMvc
    private val base = "/api/oficial-cumplimiento"
    private val pdf = "%PDF-1.4\n% documento de prueba\n".toByteArray()

    @BeforeEach
    fun preparar() {
        val cadena = wac.getBean("springSecurityFilterChain", Filter::class.java)
        mvc = MockMvcBuilders.webAppContextSetup(wac)
            .addFilters<DefaultMockMvcBuilder>(cadena)
            .defaultResponseCharacterEncoding<DefaultMockMvcBuilder>(StandardCharsets.UTF_8)
            .build()
        reloj.clock = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), RelojOficial.ZONA)
    }

    @AfterEach
    fun restaurar() {
        reloj.clock = Clock.system(RelojOficial.ZONA)
    }

    // ------------------------------------------------------------ ayudas

    private fun sufijo() = UUID.randomUUID().toString().replace("-", "").take(10)

    private fun empresa(): Empresa {
        val ahora = LocalDateTime.now()
        return empresas.save(
            Empresa(nombre = "Empresa Ficticia OC ${sufijo()}", uuid = UUID.randomUUID().toString(), activa = true,
                fechaCreacion = ahora, fechaActualizacion = ahora)
        )
    }

    private fun rol(codigo: String): Roles {
        val ahora = LocalDateTime.now()
        return roles.findByCodigo(codigo)
            ?: roles.save(Roles(codigo = codigo, nombre = codigo, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora))
    }

    private fun token(empresa: String?, rolCodigo: String): String {
        val ahora = LocalDateTime.now()
        val s = sufijo()
        val u = usuarios.save(
            Usuario(usuario = "oc.$s", clave = "x", correo = "oc.$s@example.test", nombres = "Prueba", apePat = "Ficticia",
                empresa = empresa, activo = true, fechaCreacion = ahora, fechaActualizacion = ahora)
        )
        val r = rol(rolCodigo)
        usuarioRoles.save(UsuarioRol(usuario = u, rol = r))
        u.roles = mutableListOf(UsuarioRol(usuario = u, rol = r))
        return jwt.generateToken(u)
    }

    private fun get(url: String, t: String): ResultActions =
        mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer $t"))

    private fun enviar(metodo: String, url: String, t: String, json: String): ResultActions {
        val b = when (metodo) {
            "PUT" -> MockMvcRequestBuilders.put(url)
            "PATCH" -> MockMvcRequestBuilders.patch(url)
            else -> MockMvcRequestBuilders.post(url)
        }
        return mvc.perform(b.header("Authorization", "Bearer $t").contentType(MediaType.APPLICATION_JSON).content(json))
    }

    private fun borrar(url: String, t: String): ResultActions =
        mvc.perform(MockMvcRequestBuilders.delete(url).header("Authorization", "Bearer $t"))

    private fun subir(t: String, ambito: String, nombre: String = "acta-prueba.pdf", contenido: ByteArray = pdf): ResultActions =
        mvc.perform(
            MockMvcRequestBuilders.multipart("$base/adjuntos")
                .file(MockMultipartFile("archivo", nombre, "application/pdf", contenido))
                .param("ambito", ambito)
                .header("Authorization", "Bearer $t")
        )

    private fun idDe(r: ResultActions, ruta: String = "$.id"): String =
        JsonPath.read(r.andReturn().response.getContentAsString(StandardCharsets.UTF_8), ruta)

    private fun cuerpo(r: ResultActions) = r.andReturn().response.getContentAsString(StandardCharsets.UTF_8)

    private fun designacion(acta: String, tipoCargo: String = "TITULAR", designa: String = "DIRECTORIO", dni: String = "00000001") = """
        { "tipoEmpresa": "PERSONA_JURIDICA", "tipoCargo": "$tipoCargo", "dedicacion": "NO_EXCLUSIVA",
          "persona": { "nombres": "Ana", "apellidos": "Prueba Torres", "nacionalidad": "PE", "tipoDocumento": "DNI",
                       "numeroDocumento": "$dni", "fechaNacimiento": "1990-01-15", "domicilio": "Calle Ficticia 123",
                       "telefono": "900000001", "correo": "ana.prueba@example.test" },
          "cargo": { "cargo": "Jefa de Cumplimiento", "fechaIngreso": "2022-03-15", "vinculo": "LABORAL",
                     "direccionOficina": "Av. Ejemplo 456" },
          "designacion": { "designa": "$designa", "nombreOrgano": "Directorio de prueba", "documentoTipo": "ACTA_DIRECTORIO",
                           "documentoFecha": "2026-10-30", "fechaDesignacion": "2026-11-03", "adjuntoId": "$acta",
                           "esOficialOtraEmpresa": false, "reemplazaOficialAnterior": false },
          "declaraciones": {
            "requisitos": { "puntos": ["a","b","c","d","e","f","g","h"], "firmaNombre": "Ana Prueba Torres", "fechaFirma": "2026-11-03" },
            "designante": { "aceptada": true, "firmaNombre": "Luis Demo Rojas", "fechaFirma": "2026-11-03" } } }
    """.trimIndent()

    private fun designar(t: String): String {
        val acta = idDe(subir(t, "ACTA_DESIGNACION").andExpect(status().isCreated))
        return idDe(enviar("POST", "$base/designaciones", t, designacion(acta)).andExpect(status().isCreated))
    }

    // ------------------------------------------------------------ pruebas

    @Test
    fun `ciclo completo, designar, comunicar, cambiar datos, comunicar el cambio, remover y volver a designar`() {
        val a = empresa()
        val admin = token(a.nombre, "ADMINISTRADOR_EMPRESA")

        get("$base/estado", admin).andExpect(status().isOk).andExpect(jsonPath("$.estado").value("SIN_OFICIAL"))
        get("$base/catalogos", admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.nacionalidadInicial").value("PE"))
            .andExpect(jsonPath("$.declaracionRequisitos.length()").value(8))
            .andExpect(jsonPath("$.tiposCargo[1].disponible").value(false))
        get("$base/plazos?regla=PLAZO_COMUNICAR_DESIGNACION&desde=2026-11-03", admin)
            .andExpect(status().isOk).andExpect(jsonPath("$.vence").value("2026-11-24"))

        val id = designar(admin)

        get("$base/estado", admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("PENDIENTE_COMUNICAR"))
            .andExpect(jsonPath("$.titular.id").value(id))
            .andExpect(jsonPath("$.plazoComunicacion.vence").value("2026-11-24"))
            .andExpect(jsonPath("$.plazoComunicacion.diasRestantes").value(2))
            .andExpect(jsonPath("$.plazoComunicacion.estado").value("POR_VENCER"))
            .andExpect(jsonPath("$.plazoComunicacion.cuentaFeriados").value(false))

        // Ya hay titular: no se puede designar otro
        val acta2 = idDe(subir(admin, "ACTA_DESIGNACION"))
        enviar("POST", "$base/designaciones", admin, designacion(acta2)).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("OC_TITULAR_ACTIVO"))

        // Comunicacion: presentada y luego procedente con codigos
        enviar("POST", "$base/designaciones/$id/comunicacion", admin,
            """{ "fechaComunicacion": "2026-11-05", "numeroSolicitud": "SISDEL-PRUEBA-1", "resultado": "EN_EVALUACION" }""")
            .andExpect(status().isOk).andExpect(jsonPath("$.estado").value("EN_EVALUACION"))
            .andExpect(jsonPath("$.plazoComunicacion").doesNotExist())
        val vigente = enviar("POST", "$base/designaciones/$id/comunicacion", admin,
            """{ "fechaComunicacion": "2026-11-05", "numeroSolicitud": "SISDEL-PRUEBA-1", "resultado": "PROCEDENTE",
                 "fechaNotificacion": "2026-11-12", "codigoEmpresa": "CODIGO-EMPRESA-FICTICIO", "codigoOficial": "CODIGO-OFICIAL-FICTICIO" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("VIGENTE"))
            .andExpect(jsonPath("$.comunicacion.codigos.reservados").value(true))
            .andExpect(jsonPath("$.comunicacion.codigos.cifradoDisponible").value(true))
            .andExpect(jsonPath("$.revisionSugerida").value("2027-11-03"))
        assertFalse(cuerpo(vigente).contains("CODIGO-EMPRESA-FICTICIO"))
        assertFalse(cuerpo(vigente).contains("CODIGO-OFICIAL-FICTICIO"))
        val guardada = designacionesRepo.findAll().first { it.uuid == id }
        assertNotNull(guardada.codigoEmpresaCifrado)
        assertTrue(guardada.codigoEmpresaCifrado!!.startsWith("v1:"))
        assertFalse(guardada.codigoEmpresaCifrado!!.contains("CODIGO-EMPRESA-FICTICIO"))

        // Cambiar datos: queda pendiente de comunicar
        val cambio = enviar("PATCH", "$base/designaciones/$id/datos", admin,
            """{ "telefono": "900000002", "motivo": "Cambio de línea de prueba." }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cambios[0].campo").value("telefono"))
            .andExpect(jsonPath("$.pendiente.plazo.vence").value("2026-11-27"))
        val pendiente = idDe(cambio, "$.pendiente.id")
        enviar("PATCH", "$base/designaciones/$id/datos", admin, """{ "telefono": "900000002", "motivo": "Igual." }""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("OC_SIN_CAMBIOS"))
        enviar("PATCH", "$base/designaciones/$id/datos", admin, """{ "telefono": "900000003" }""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.campos.motivo").exists())

        get("$base/designaciones/$id/historial", admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.eventos[0].tipo").value("CAMBIO_DATOS"))
            .andExpect(jsonPath("$.eventos[0].etiqueta.codigo").value("PENDIENTE_COMUNICAR"))
            .andExpect(jsonPath("$.eventos[0].cambios[0].antes").value("900000001"))
            .andExpect(jsonPath("$.eventos.length()").value(4))
            .andExpect(jsonPath("$.pendientesUif.length()").value(1))

        enviar("POST", "$base/pendientes/$pendiente/comunicacion", admin, """{ "fecha": "2026-11-20", "nota": "Presentado." }""")
            .andExpect(status().isOk).andExpect(jsonPath("$.comunicado").value(true))
        enviar("POST", "$base/pendientes/$pendiente/comunicacion", admin, """{ "fecha": "2026-11-20" }""")
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("OC_YA_COMUNICADO"))
        get("$base/designaciones/$id/historial", admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.eventos[0].tipo").value("CAMBIO_COMUNICADO"))
            .andExpect(jsonPath("$.eventos[1].etiqueta.codigo").value("COMUNICADO"))
            .andExpect(jsonPath("$.pendientesUif.length()").value(0))

        // Remocion: el cargo queda vacante
        val actaRemocion = idDe(subir(admin, "ACTA_REMOCION"))
        enviar("POST", "$base/designaciones/$id/remocion", admin,
            """{ "motivo": "REMOCION_EMPRESA", "razones": "Razones de prueba.", "aprobadaPor": "Directorio de prueba",
                 "fechaDecision": "2026-11-20", "actaAdjuntoId": "$actaRemocion" }""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.estadoEmpresa").value("VACANTE"))
            .andExpect(jsonPath("$.plazos.comunicarRemocion.vence").value("2026-11-27"))
            .andExpect(jsonPath("$.plazos.vacancia.vence").value("2026-12-20"))
        get("$base/estado", admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("VACANTE"))
            .andExpect(jsonPath("$.vacancia.designacionRemovidaId").value(id))
            .andExpect(jsonPath("$.vacancia.plazoDesignar.diasRestantes").value(30))
            .andExpect(jsonPath("$.vacancia.remocionComunicada").value(false))
        enviar("POST", "$base/designaciones/$id/remocion", admin,
            """{ "motivo": "VACANCIA", "razones": "x", "fechaDecision": "2026-11-20" }""")
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("OC_ESTADO_INVALIDO"))
        // El acta ya ligada no se puede borrar
        borrar("$base/adjuntos/$actaRemocion", admin).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("OC_ADJUNTO_EN_USO"))

        // Nuevo oficial
        val nuevo = designar(admin)
        get("$base/estado", admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("PENDIENTE_COMUNICAR"))
            .andExpect(jsonPath("$.titular.id").value(nuevo))
        get("$base/designaciones", admin).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `no procedente vuelve a pendiente y se corrige en el mismo registro con historial`() {
        val a = empresa()
        val admin = token(a.nombre, "ADMINISTRADOR_EMPRESA")
        val id = designar(admin)
        enviar("POST", "$base/designaciones/$id/comunicacion", admin,
            """{ "fechaComunicacion": "2026-11-05", "numeroSolicitud": "SISDEL-PRUEBA-2", "resultado": "NO_PROCEDENTE", "fechaNotificacion": "2026-11-10" }""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.campos.motivoNoProcedente").exists())
        enviar("POST", "$base/designaciones/$id/comunicacion", admin,
            """{ "fechaComunicacion": "2026-11-05", "numeroSolicitud": "SISDEL-PRUEBA-2", "resultado": "NO_PROCEDENTE",
                 "fechaNotificacion": "2026-11-10", "motivoNoProcedente": "Falta un dato de prueba." , "codigoOficial": "X1" }""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.campos.codigoOficial").exists())
        enviar("POST", "$base/designaciones/$id/comunicacion", admin,
            """{ "fechaComunicacion": "2026-11-05", "numeroSolicitud": "SISDEL-PRUEBA-2", "resultado": "NO_PROCEDENTE",
                 "fechaNotificacion": "2026-11-10", "motivoNoProcedente": "Falta un dato de prueba." }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.estado").value("PENDIENTE_COMUNICAR"))
            .andExpect(jsonPath("$.observacionUif.motivo").value("Falta un dato de prueba."))

        val ficha = cuerpo(get("$base/designaciones/$id", admin))
        val acta: String = JsonPath.read(ficha, "$.designacion.adjunto.id")
        val corregido = designacion(acta, dni = "00000002").replace("\"declaraciones\"", "\"motivo\": \"Corrección de prueba.\", \"declaraciones\"")
        enviar("PUT", "$base/designaciones/$id", admin, corregido).andExpect(status().isOk)
            .andExpect(jsonPath("$.persona.numeroDocumento").value("00000002"))
        enviar("PUT", "$base/designaciones/$id", admin, corregido)
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("OC_SIN_CAMBIOS"))
        get("$base/designaciones/$id/historial", admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.eventos[0].tipo").value("DESIGNACION_CORREGIDA"))
            .andExpect(jsonPath("$.eventos[0].cambios[0].campo").value("persona.numeroDocumento"))
            .andExpect(jsonPath("$.eventos[1].tipo").value("DESIGNACION_NO_PROCEDENTE"))
        // Pendiente no se puede remover ni cambiar datos: se corrige
        enviar("PATCH", "$base/designaciones/$id/datos", admin, """{ "telefono": "900000009", "motivo": "x" }""")
            .andExpect(status().isConflict)
    }

    @Test
    fun `una empresa no ve ni toca nada de otra en ninguna ruta y el administrador universal si`() {
        val a = empresa()
        val b = empresa()
        val adminA = token(a.nombre, "ADMINISTRADOR_EMPRESA")
        val adminB = token(b.nombre, "ADMINISTRADOR_EMPRESA")
        val lectorB = token(b.nombre, "USUARIO")
        val id = designar(adminA)
        enviar("PATCH", "$base/designaciones/$id/datos", adminA, """{ "telefono": "900000005", "motivo": "Prueba." }""")
            .andExpect(status().isConflict) // aun pendiente
        val ficha = cuerpo(get("$base/designaciones/$id", adminA).andExpect(status().isOk))
        val acta: String = JsonPath.read(ficha, "$.designacion.adjunto.id")
        val suelto = idDe(subir(adminA, "CONSTANCIA_UIF"))

        for (t in listOf(adminB, lectorB)) {
            get("$base/designaciones/$id", t).andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("OC_NO_ENCONTRADO"))
            get("$base/designaciones/$id/historial", t).andExpect(status().isNotFound)
            get("$base/designaciones/$id/declaraciones/requisitos", t).andExpect(status().isNotFound)
            get("$base/designaciones/$id/declaraciones/requisitos/pdf", t).andExpect(status().isNotFound)
            get("$base/designaciones/$id/declaraciones/designante", t).andExpect(status().isNotFound)
            get("$base/adjuntos/$acta", t).andExpect(status().isNotFound)
            get("$base/estado", t).andExpect(status().isOk).andExpect(jsonPath("$.estado").value("SIN_OFICIAL"))
            get("$base/designaciones", t).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(0))
            get("$base/pendientes?todos=true", t).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(0))
            // ?empresa= de una cuenta comun se ignora
            get("$base/estado?empresa=${a.uuid}", t).andExpect(status().isOk).andExpect(jsonPath("$.estado").value("SIN_OFICIAL"))
        }
        enviar("PUT", "$base/designaciones/$id", adminB, designacion(acta)).andExpect(status().isNotFound)
        enviar("POST", "$base/designaciones/$id/comunicacion", adminB,
            """{ "fechaComunicacion": "2026-11-05", "numeroSolicitud": "S-1", "resultado": "EN_EVALUACION" }""")
            .andExpect(status().isNotFound)
        enviar("PATCH", "$base/designaciones/$id/datos", adminB, """{ "telefono": "900000006", "motivo": "x" }""")
            .andExpect(status().isNotFound)
        enviar("POST", "$base/designaciones/$id/remocion", adminB,
            """{ "motivo": "VACANCIA", "razones": "x", "fechaDecision": "2026-11-20" }""").andExpect(status().isNotFound)
        borrar("$base/adjuntos/$suelto", adminB).andExpect(status().isNotFound)
        // El acta de A no sirve para designar en B
        enviar("POST", "$base/designaciones", adminB, designacion(acta)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.campos['designacion.adjuntoId']").exists())

        // Pendientes de A tampoco: se comunica, se cambia un dato y B intenta cerrarlo
        enviar("POST", "$base/designaciones/$id/comunicacion", adminA,
            """{ "fechaComunicacion": "2026-11-05", "numeroSolicitud": "S-1", "resultado": "PROCEDENTE", "fechaNotificacion": "2026-11-06" }""")
            .andExpect(status().isOk)
        val pendiente = idDe(enviar("PATCH", "$base/designaciones/$id/datos", adminA, """{ "telefono": "900000007", "motivo": "x" }"""),
            "$.pendiente.id")
        enviar("POST", "$base/pendientes/$pendiente/comunicacion", adminB, """{ "fecha": "2026-11-20" }""").andExpect(status().isNotFound)

        // Administrador universal: con ?empresa= ve y actua en A; sin empresa propia y sin parametro, 400
        val universal = token(null, "ADMINISTRADOR")
        get("$base/designaciones/$id?empresa=${a.uuid}", universal).andExpect(status().isOk)
            .andExpect(jsonPath("$.empresa.uuid").value(a.uuid))
        mvc.perform(MockMvcRequestBuilders.get("$base/estado").param("empresa", a.nombre).header("Authorization", "Bearer $universal")).andExpect(status().isOk).andExpect(jsonPath("$.estado").value("VIGENTE"))
        get("$base/estado", universal).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("OC_EMPRESA_REQUERIDA"))
        get("$base/designaciones/$id?empresa=${b.uuid}", universal).andExpect(status().isNotFound)
    }

    @Test
    fun `los roles comunes solo leen y no ven los adjuntos de las declaraciones`() {
        val a = empresa()
        val admin = token(a.nombre, "ADMINISTRADOR_EMPRESA")
        val lector = token(a.nombre, "USUARIO")
        val djFirmada = idDe(subir(admin, "DJ_REQUISITOS_FIRMADA", "dj-firmada.pdf"))
        val acta = idDe(subir(admin, "ACTA_DESIGNACION"))
        val json = designacion(acta).replace(
            "\"firmaNombre\": \"Ana Prueba Torres\", \"fechaFirma\": \"2026-11-03\"", "\"adjuntoId\": \"$djFirmada\""
        )
        val id = idDe(enviar("POST", "$base/designaciones", admin, json).andExpect(status().isCreated)
            .andExpect(jsonPath("$.declaraciones.requisitos.adjunto.id").value(djFirmada)))

        get("$base/designaciones/$id", lector).andExpect(status().isOk)
            .andExpect(jsonPath("$.declaraciones.requisitos.adjunto").doesNotExist())
            .andExpect(jsonPath("$.permisos.puedeComunicar").value(false))
        get("$base/adjuntos/$djFirmada", lector).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("OC_SIN_PERMISO"))
        get("$base/adjuntos/$djFirmada", admin).andExpect(status().isOk)
        get("$base/adjuntos/$acta", lector).andExpect(status().isOk)
        enviar("POST", "$base/designaciones", lector, designacion(acta)).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("OC_SIN_PERMISO"))
        subir(lector, "ACTA_DESIGNACION").andExpect(status().isForbidden)
        enviar("POST", "$base/designaciones/$id/comunicacion", lector,
            """{ "fechaComunicacion": "2026-11-05", "numeroSolicitud": "S-1", "resultado": "EN_EVALUACION" }""")
            .andExpect(status().isForbidden)
        enviar("POST", "$base/feriados", admin, """{ "fecha": "2026-12-25", "nombre": "Prueba", "fuente": "Prueba" }""")
            .andExpect(status().isForbidden)
    }

    @Test
    fun `validaciones del alta sin repetir datos personales en los errores`() {
        val a = empresa()
        val admin = token(a.nombre, "ADMINISTRADOR_EMPRESA")
        enviar("POST", "$base/designaciones", admin, """{ "tipoCargo": "TITULAR" }""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("OC_DATOS_INVALIDOS"))
            .andExpect(jsonPath("$.campos['persona.nombres']").exists())
            .andExpect(jsonPath("$.campos['designacion.adjuntoId']").exists())
            .andExpect(jsonPath("$.campos['declaraciones.requisitos.puntos']").exists())
        val acta = idDe(subir(admin, "ACTA_DESIGNACION"))
        enviar("POST", "$base/designaciones", admin, designacion(acta, tipoCargo = "ALTERNO"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("OC_FUNCION_PRONTO"))
        enviar("POST", "$base/designaciones", admin, designacion(acta, designa = "GERENTE"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.campos['designacion.designa']").exists())
        val error = cuerpo(enviar("POST", "$base/designaciones", admin, designacion(acta, dni = "1234567X"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.campos['persona.numeroDocumento']").exists()))
        assertFalse(error.contains("1234567X"))
        subir(admin, "ACTA_DESIGNACION", "acta.txt", "hola".toByteArray())
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("OC_ADJUNTO_INVALIDO"))
        subir(admin, "ACTA_DESIGNACION", "acta.pdf", "no es un pdf".toByteArray())
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("OC_ADJUNTO_INVALIDO"))
        // Un adjunto de otro ambito no vale para el acta
        val constancia = idDe(subir(admin, "CONSTANCIA_UIF"))
        enviar("POST", "$base/designaciones", admin, designacion(constancia))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.campos['designacion.adjuntoId']").exists())
        // Vista previa con el texto del diseño
        enviar("POST", "$base/declaraciones/vista-previa", admin,
            """{ "nombres": "Ana", "apellidos": "Prueba Torres", "tipoDocumento": "DNI", "numeroDocumento": "00000001",
                 "tipoCargo": "TITULAR", "vinculo": "CONTRACTUAL_DIRECTO", "fecha": "2026-11-03" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lugarFecha").value("Lima, 03 de noviembre de 2026"))
            .andExpect(jsonPath("$.puntos[7].texto").value("Tengo vínculo contractual directo con la empresa y actuaré con autonomía e independencia."))
        val pdfRespuesta = enviar("POST", "$base/declaraciones/vista-previa/pdf", admin, """{ "nombres": "Ana" }""")
            .andExpect(status().isOk).andReturn().response.contentAsByteArray
        assertEquals("%PDF", String(pdfRespuesta.copyOfRange(0, 4)))
    }
}
