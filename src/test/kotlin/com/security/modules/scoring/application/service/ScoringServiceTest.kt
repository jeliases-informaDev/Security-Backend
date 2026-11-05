package com.security.modules.scoring.application.service

import com.security.modules.licencias.ContextoLicencia
import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.dominio.TIPO_SCORING
import com.security.modules.listas.application.service.HomonimoVigente
import com.security.modules.listas.application.service.HomonimosListasService
import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.domain.HistorialMancha
import com.security.modules.listas.domain.ListaGrupo
import com.security.modules.listas.domain.TipoLista
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.scoring.domain.ScoringDepartamento
import com.security.modules.scoring.domain.ScoringOcupacion
import com.security.modules.scoring.domain.ScoringOpcion
import com.security.modules.scoring.domain.ScoringPaisGrupo
import com.security.modules.scoring.domain.ScoringRiesgo
import com.security.modules.scoring.dto.ClienteNuevoRequest
import com.security.modules.scoring.dto.DatosScoringRequest
import com.security.modules.scoring.dto.DecisionRequest
import com.security.modules.scoring.infrastructure.ScoringDepartamentoRepository
import com.security.modules.scoring.infrastructure.ScoringOcupacionRepository
import com.security.modules.scoring.infrastructure.ScoringOpcionRepository
import com.security.modules.scoring.infrastructure.ScoringPaisGrupoRepository
import com.security.modules.scoring.infrastructure.ScoringRiesgoRepository
import com.security.modules.scoring.infrastructure.ScoringSustentoRepository
import com.security.modules.scoring.infrastructure.TotalMoneda
import com.security.modules.scoring.infrastructure.VolumenOperacionesConsulta
import com.security.shared.datos.entities.Pais
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.TipoDocumento
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.PaisRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.VisibilidadEmpresaService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El servicio con la base simulada: datos automaticos, token, 422, visibilidad por empresa y decision. */
class ScoringServiceTest {

    private val entidadRepository = mockk<EntidadRepository>()
    private val ocupacionRepository = mockk<ScoringOcupacionRepository>()
    private val departamentoRepository = mockk<ScoringDepartamentoRepository>()
    private val opcionRepository = mockk<ScoringOpcionRepository>()
    private val paisGrupoRepository = mockk<ScoringPaisGrupoRepository>()
    private val paisRepository = mockk<PaisRepository>()
    private val scoringRiesgoRepository = mockk<ScoringRiesgoRepository>()
    private val sustentoRepository = mockk<ScoringSustentoRepository>()
    private val usuarioRepository = mockk<UsuarioRepository>()
    private val contexto = mockk<LicenciaContexto>()
    private val licencias = mockk<LicenciaService>(relaxed = true)
    private val volumen = mockk<VolumenOperacionesConsulta>()
    private lateinit var service: ScoringService

    private val peru = Pais(id = 30, nombre = "Perú", continente = "America")
    private val ahora = LocalDateTime.now()

    private fun cuenta(id: Int, nombre: String, empresa: String?, rol: String = "USUARIO"): Usuario {
        val u = Usuario(id = id, usuario = nombre, clave = "x", correo = "$nombre@example.test", empresa = empresa,
            fechaCreacion = ahora, fechaActualizacion = ahora)
        u.roles = mutableListOf(UsuarioRol(usuario = u, rol = Roles(id = 1, codigo = rol, nombre = rol, activo = true,
            fechaCreacion = ahora, fechaActualizacion = ahora)))
        every { usuarioRepository.findByUsuario(nombre) } returns u
        return u
    }

    private lateinit var analista: Usuario

    @BeforeEach
    fun setUp() {
        service = ScoringService(
            entidadRepository, ocupacionRepository, departamentoRepository, opcionRepository, paisGrupoRepository,
            paisRepository, scoringRiesgoRepository, sustentoRepository, usuarioRepository,
            VisibilidadEmpresaService(usuarioRepository), contexto, licencias, volumen
        )
        analista = cuenta(1, "analista", "Gamma")
        every { scoringRiesgoRepository.save(any<ScoringRiesgo>()) } answers { firstArg<ScoringRiesgo>().also { it.id = 77 } }
        every { ocupacionRepository.findById(1) } returns Optional.of(ScoringOcupacion(id = 1, nombre = "CONSTRUCTOR", puntaje = 1, puntajeV2 = 4))
        every { ocupacionRepository.findById(2) } returns Optional.of(ScoringOcupacion(id = 2, nombre = "PINTOR", puntaje = 1, puntajeV2 = null))
        every { ocupacionRepository.findById(3) } returns Optional.of(ScoringOcupacion(id = 3, nombre = "TECNICO", puntaje = 1, puntajeV2 = 2))
        every { opcionRepository.findById(10) } returns Optional.of(ScoringOpcion(id = 10, tipo = ScoringOpcion.PRODUCTO, nombre = "Venta de inmueble", puntaje = 2))
        every { opcionRepository.findById(11) } returns Optional.of(ScoringOpcion(id = 11, tipo = ScoringOpcion.CANAL, nombre = "Presencial", puntaje = 1))
        every { opcionRepository.findById(12) } returns Optional.of(ScoringOpcion(id = 12, tipo = ScoringOpcion.MEDIO_PAGO, nombre = "Transferencia bancaria", puntaje = 2))
        every { departamentoRepository.findById(20) } returns Optional.of(ScoringDepartamento(id = 20, pais = peru, nombre = "Lima", puntaje = 2, puntajeV2 = 2))
        every { paisRepository.findById(30) } returns Optional.of(peru)
        every { paisGrupoRepository.buscarPorPais(30) } returns ScoringPaisGrupo(id = 1, pais = peru, grupo = 1, altoRiesgoGafi = false)
        every { volumen.totalesPorMoneda(any(), any(), any(), any()) } returns emptyList()
        every { entidadRepository.buscar(any(), any(), any(), any(), any(), any()) } returns PageImpl(emptyList())
    }

    private fun entidad(esPep: Boolean = false, sancion: Boolean = false): Entidad {
        val e = Entidad(id = 5, tipoDocumento = TipoDocumento(id = 1, nombre = "DNI"), pais = peru, documento = "00000001", tipoEntidad = Entidad.TIPO_NATURAL)
        if (esPep) {
            e.manchas.add(HistorialMancha(entidad = e, tipoLista = TipoLista(id = 10, nombre = "Personas Expuestas Publicamente", esPep = true, grupo = ListaGrupo(id = 6, orden = 6))))
        }
        if (sancion) {
            e.manchas.add(HistorialMancha(entidad = e, tipoLista = TipoLista(id = 11, nombre = "Lista OFAC", grupo = ListaGrupo(id = 1, orden = 1))))
        }
        e.manchas.add(HistorialMancha(entidad = e, tipoLista = TipoLista(id = 12, nombre = "ASBANC - Noticias (información negativa)", grupo = ListaGrupo(id = 6, orden = 6))))
        every { entidadRepository.buscarPorIdConDetalle(5) } returns e
        every { entidadRepository.cargarManchas(listOf(5)) } returns listOf(e)
        return e
    }

    private fun datos(ocupacion: Int = 1, monto: String = "5000000") = DatosScoringRequest(
        entidadId = 5, ocupacionId = ocupacion, montoOperacion = BigDecimal(monto), productoId = 10, canalId = 11,
        medioPagoId = 12, departamentoResidenciaId = 20, departamentoOperacionId = 20, paisFondosId = 30
    )

    @Test
    fun `evaluar el ejemplo de control guarda la version 2 y se vuelve a abrir igual`() {
        entidad(esPep = true)
        val guardada = slot<ScoringRiesgo>()
        every { scoringRiesgoRepository.save(capture(guardada)) } answers { firstArg<ScoringRiesgo>().also { it.id = 77 } }

        val r = service.evaluar("analista", datos())

        assertEquals(77, r.id)
        assertEquals(BigDecimal("2.95"), r.puntaje)
        assertEquals("ALTO", r.nivel)
        assertEquals(listOf("PEP", "DOS_FACTORES_EN_4"), r.reglas.map { it.codigo })
        assertEquals("Reforzada", r.queHacer?.debidaDiligencia)
        assertEquals(6, r.queHacer?.revisionMeses)
        val consulta = assertNotNull(r.consulta)
        assertFalse(consulta.vinculados.revisado)
        assertNull(consulta.vinculados.coincidencias)
        assertEquals(1, consulta.noticias.coincidencias)
        assertEquals(2, guardada.captured.version)
        assertEquals("ALTO", guardada.captured.nivel)
        assertEquals("00000001", guardada.captured.clienteDocumento)

        // Se vuelve a abrir desde lo guardado
        every { scoringRiesgoRepository.buscarPorIdConDetalle(77) } returns guardada.captured.also { it.id = 77 }
        val otra = service.obtener("analista", 77)
        assertEquals(77, otra.id)
        assertEquals(BigDecimal("2.95"), otra.puntaje)
        assertEquals(r.grupos, otra.grupos)
        assertEquals(r.reglas, otra.reglas)
        assertNull(otra.decision)
    }

    @Test
    fun `una coincidencia en listas de sanciones fija Alto`() {
        entidad(sancion = true)
        val r = service.evaluar("analista", datos(ocupacion = 3, monto = "1000"))
        assertEquals("ALTO", r.nivel)
        assertEquals(listOf("SANCIONES"), r.reglas.map { it.codigo })
    }

    @Test
    fun `con datos incompletos responde 422 sin guardar ni consumir`() {
        entidad()
        licenciasEncendidas()
        val e = assertFailsWith<ScoringException> { service.evaluar("analista", datos(ocupacion = 2)) }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.status)
        assertEquals("SCORING_DATOS_INCOMPLETOS", e.code)
        assertEquals(listOf("ocupacionId"), e.bloqueos?.map { it.campo })
        verify(exactly = 0) { scoringRiesgoRepository.save(any<ScoringRiesgo>()) }
        verify(exactly = 0) { licencias.consumirComo(any(), any(), any(), any()) }
    }

    @Test
    fun `la vista previa acepta datos a medias y no guarda`() {
        entidad()
        val v = service.vistaPrevia("analista", DatosScoringRequest(entidadId = 5, productoId = 10, canalId = 11, medioPagoId = 12))
        assertEquals(BigDecimal("2.00"), v.grupos.productos)
        assertNull(v.grupos.clientes)
        assertNull(v.puntaje)
        assertTrue(v.bloqueos.any { it.campo == "ocupacionId" })
        verify(exactly = 0) { scoringRiesgoRepository.save(any<ScoringRiesgo>()) }
    }

    @Test
    fun `cliente nuevo sin datos completos bloquea y nunca crea una entidad`() {
        val v = service.vistaPrevia("analista", datos().copy(entidadId = null, cliente = ClienteNuevoRequest(tipoPersona = "NATURAL", documento = "00000002")))
        assertEquals(listOf("cliente"), v.bloqueos.map { it.campo })

        val r = service.evaluar(
            "analista",
            datos(ocupacion = 3, monto = "1000").copy(
                entidadId = null,
                cliente = ClienteNuevoRequest(tipoPersona = "NATURAL", tipoDocumento = "DNI", documento = "00000002", nombre = "Cliente de prueba")
            )
        )
        assertEquals("Cliente de prueba", r.cliente.nombre)
        assertNull(r.entidadId)
        verify(exactly = 0) { entidadRepository.save(any<Entidad>()) }
        // Su documento si se consulto en las listas
        verify { entidadRepository.buscar("00000002", "DNI", null, null, null, any()) }
    }

    @Test
    fun `el monto toma el promedio del Registro de Operaciones de la propia empresa si es mayor`() {
        entidad()
        every { volumen.totalesPorMoneda("Gamma", "00000001", any(), any()) } returns listOf(
            TotalMoneda("PEN — Soles", BigDecimal("7200000"), 12),
            TotalMoneda("USD — Dolares americanos", BigDecimal("1000"), 1)
        )
        val r = service.evaluar("analista", datos(ocupacion = 3, monto = "10000"))
        val monto = r.grupos.flatMap { it.factores }.first { it.codigo == "MONTO" }
        assertEquals(4, monto.puntaje)
        val nota = assertNotNull(r.montoNota)
        assertTrue(nota.contains("S/ 600,000"), nota)
        assertTrue(nota.contains("otra moneda"), nota)
        verify { volumen.totalesPorMoneda("Gamma", "00000001", any(), any()) }
    }

    @Test
    fun `una cuenta sin empresa usa solo el monto de la operacion y lo dice`() {
        cuenta(9, "suelto", null)
        entidad()
        val r = service.evaluar("suelto", datos(ocupacion = 3, monto = "10000"))
        val nota = assertNotNull(r.montoNota)
        assertTrue(nota.contains("Solo se usa el monto de la operación"), nota)
        verify(exactly = 0) { volumen.totalesPorMoneda(any(), any(), any(), any()) }
    }

    // ------------------------------------------------------------- token

    private val quien = ContextoLicencia(usuario = "analista", empresa = "Gamma", empresaUuid = "u-1", esSulotec = false)

    private fun licenciasEncendidas() {
        every { licencias.activo } returns true
        every { contexto.actual() } returns quien
    }

    @Test
    fun `cada evaluacion pide 1 token con una clave nueva`() {
        entidad()
        licenciasEncendidas()
        val clave = slot<String>()
        every { licencias.consumirComo(quien, TIPO_SCORING, capture(clave), any()) } returns Unit

        service.evaluar("analista", datos(ocupacion = 3, monto = "1000"))

        verify(exactly = 1) { licencias.consumirComo(quien, TIPO_SCORING, any(), any()) }
        assertTrue(clave.captured.startsWith("plaft:u-1:scoring:"))
        verify(exactly = 0) { licencias.anular(any()) }
    }

    @Test
    fun `si el guardado falla despues de consumir se devuelve el token`() {
        entidad()
        licenciasEncendidas()
        val clave = slot<String>()
        every { licencias.consumirComo(quien, TIPO_SCORING, capture(clave), any()) } returns Unit
        every { scoringRiesgoRepository.save(any<ScoringRiesgo>()) } throws IllegalStateException("sin base")

        assertFailsWith<IllegalStateException> { service.evaluar("analista", datos(ocupacion = 3, monto = "1000")) }

        verify(exactly = 1) { licencias.anular(clave.captured) }
    }

    // ------------------------------------------------------------- visibilidad por empresa

    private fun evaluacionDe(duenio: Usuario, id: Int = 40, nivel: String? = null, version: Int = 1): ScoringRiesgo {
        val sr = ScoringRiesgo(
            id = id,
            usuario = duenio,
            ocupacion = ScoringOcupacion(id = 1, nombre = "CONSTRUCTOR", puntaje = 1, puntajeV2 = 4),
            departamento = ScoringDepartamento(id = 20, pais = peru, nombre = "Lima", puntaje = 2, puntajeV2 = 2),
            clienteSensibleTipo = "NACIONAL",
            volumenTransaccional = BigDecimal("1000"),
            puntaje = BigDecimal("1.80"),
            categoria = "Riesgo Muy Bajo",
            version = version,
            nivel = nivel
        )
        every { scoringRiesgoRepository.buscarPorIdConDetalle(id) } returns sr
        return sr
    }

    @Test
    fun `GET por id - una empresa nunca ve evaluaciones de otra`() {
        val duenia = cuenta(2, "duenia", "Delta")
        evaluacionDe(duenia)

        // Otra empresa: ni la cuenta comun ni su administrador
        assertFailsWith<ResourceNotFoundException> { service.obtener("analista", 40) }
        cuenta(3, "jefaGamma", "Gamma", rol = "ADMINISTRADOR_EMPRESA")
        assertFailsWith<ResourceNotFoundException> { service.obtener("jefaGamma", 40) }
        assertFailsWith<ResourceNotFoundException> { service.decidir("jefaGamma", 40, DecisionRequest("RECHAZAR")) }

        // Misma empresa: la duenia y su administrador; una colega comun, no
        assertEquals(40, service.obtener("duenia", 40).id)
        cuenta(4, "jefaDelta", "delta", rol = "ADMINISTRADOR_EMPRESA")
        assertEquals(40, service.obtener("jefaDelta", 40).id)
        cuenta(5, "colega", "Delta")
        assertFailsWith<ResourceNotFoundException> { service.obtener("colega", 40) }

        // Administrador universal de Sulotec: todo
        cuenta(6, "sulotec", null, rol = "ADMINISTRADOR")
        assertEquals(40, service.obtener("sulotec", 40).id)

        // Una que no existe: el mismo 404
        every { scoringRiesgoRepository.buscarPorIdConDetalle(999) } returns null
        assertFailsWith<ResourceNotFoundException> { service.obtener("sulotec", 999) }
    }

    @Test
    fun `una evaluacion antigua se abre como version 1 con su categoria y sin nivel`() {
        evaluacionDe(analista)
        val r = service.obtener("analista", 40)
        assertEquals(1, r.version)
        assertNull(r.nivel)
        assertEquals("Riesgo Muy Bajo", r.categoria)
    }

    @Test
    fun `historial - las filas antiguas traen version 1, su categoria y nivel null`() {
        val antigua = evaluacionDe(analista)
        every { scoringRiesgoRepository.buscarPorUsuario(1, any()) } returns PageImpl(listOf(antigua))
        val fila = service.obtenerHistorial("analista", PageRequest.of(0, 10)).content.single()
        assertEquals(1, fila.version)
        assertNull(fila.nivel)
        assertNull(fila.decision)
        assertEquals("Riesgo Muy Bajo", fila.categoria)
    }

    // ------------------------------------------------------------- decision

    @Test
    fun `con nivel Alto no se aprueba directo, se envia a aprobacion con comentario`() {
        evaluacionDe(analista, nivel = "ALTO", version = 2)
        every { scoringRiesgoRepository.registrarDecision(40, any(), any(), any(), any()) } returns 1

        val aprobar = assertFailsWith<ScoringException> { service.decidir("analista", 40, DecisionRequest("APROBAR", "ok")) }
        assertEquals("SCORING_REQUIERE_APROBACION", aprobar.code)
        assertEquals(HttpStatus.CONFLICT, aprobar.status)

        val sinComentario = assertFailsWith<ScoringException> { service.decidir("analista", 40, DecisionRequest("ENVIAR_A_APROBACION", " ")) }
        assertEquals("SCORING_COMENTARIO_OBLIGATORIO", sinComentario.code)
        assertEquals(HttpStatus.BAD_REQUEST, sinComentario.status)

        val r = service.decidir("analista", 40, DecisionRequest("ENVIAR_A_APROBACION", "Reunir sustento"))
        assertEquals("ENVIAR_A_APROBACION", r.decision?.accion)
        assertEquals("analista", r.decision?.por)
        assertNotNull(r.decision?.fecha)
    }

    @Test
    fun `con nivel Bajo o Medio decide el analista - aprobar o rechazar, no enviar`() {
        evaluacionDe(analista, nivel = "MEDIO", version = 2)
        every { scoringRiesgoRepository.registrarDecision(40, any(), any(), any(), any()) } returns 1

        val enviar = assertFailsWith<ScoringException> { service.decidir("analista", 40, DecisionRequest("ENVIAR_A_APROBACION", "x")) }
        assertEquals("SCORING_NO_REQUIERE_APROBACION", enviar.code)
        assertEquals(HttpStatus.CONFLICT, enviar.status)

        val r = service.decidir("analista", 40, DecisionRequest("APROBAR"))
        assertEquals("APROBAR", r.decision?.accion)
        assertNull(r.decision?.comentario)
        verify { scoringRiesgoRepository.registrarDecision(40, "APROBAR", null, "analista", any()) }

        evaluacionDe(analista, id = 41, nivel = "BAJO", version = 2)
        every { scoringRiesgoRepository.registrarDecision(41, any(), any(), any(), any()) } returns 1
        assertEquals("RECHAZAR", service.decidir("analista", 41, DecisionRequest("RECHAZAR")).decision?.accion)
    }

    @Test
    fun `una evaluacion ya decidida no se vuelve a decidir`() {
        evaluacionDe(analista, nivel = "MEDIO", version = 2).decisionAccion = "APROBAR"
        val e = assertFailsWith<ScoringException> { service.decidir("analista", 40, DecisionRequest("RECHAZAR")) }
        assertEquals("SCORING_YA_DECIDIDO", e.code)

        // Dos clics a la vez: el segundo no actualiza ninguna fila
        evaluacionDe(analista, id = 42, nivel = "MEDIO", version = 2)
        every { scoringRiesgoRepository.registrarDecision(42, any(), any(), any(), any()) } returns 0
        val doble = assertFailsWith<ScoringException> { service.decidir("analista", 42, DecisionRequest("APROBAR")) }
        assertEquals("SCORING_YA_DECIDIDO", doble.code)
    }

    @Test
    fun `una evaluacion de la version 1 no admite decision`() {
        evaluacionDe(analista)
        val e = assertFailsWith<ScoringException> { service.decidir("analista", 40, DecisionRequest("APROBAR")) }
        assertEquals("SCORING_VERSION_ANTIGUA", e.code)
    }

    // ---------------- Homonimo descartado en Listas Negativas ----------------

    private fun conHomonimos(vigente: HomonimoVigente?): ScoringService {
        val homonimos = mockk<HomonimosListasService>()
        every { homonimos.vigente(any(), any()) } returns vigente
        return ScoringService(
            entidadRepository, ocupacionRepository, departamentoRepository, opcionRepository, paisGrupoRepository,
            paisRepository, scoringRiesgoRepository, sustentoRepository, usuarioRepository,
            VisibilidadEmpresaService(usuarioRepository), contexto, licencias, volumen, homonimos
        )
    }

    @Test
    fun `una persona descartada como homonimo por la empresa no aplica PEP ni sanciones en el paso 1`() {
        val e = entidad(esPep = true, sancion = true)
        every { entidadRepository.buscar("00000001", any(), any(), any(), any(), any()) } returns PageImpl(listOf(e))

        val sinDescarte = conHomonimos(null).consultarCliente("00000001", null, "analista")
        assertEquals(1, sinDescarte.consulta.pep.coincidencias)
        assertEquals(listOf("PEP", "SANCIONES"), sinDescarte.reglas.map { it.codigo })
        assertNull(sinDescarte.consulta.homonimo)

        val descartado = HomonimoVigente(901, LocalDateTime.of(2026, 11, 5, 16, 40), "Ana T.", "Otra persona")
        val conDescarte = conHomonimos(descartado).consultarCliente("00000001", null, "analista")
        assertEquals(0, conDescarte.consulta.pep.coincidencias)
        assertEquals(0, conDescarte.consulta.sanciones.coincidencias)
        assertEquals(0, conDescarte.consulta.noticias.coincidencias)
        assertTrue(conDescarte.reglas.isEmpty())
        assertEquals("Descartado como homónimo el 05/11/2026 por Ana T.", conDescarte.consulta.homonimo?.texto)
        assertEquals(901L, conDescarte.consulta.homonimo?.consultaId)

        // La evaluacion tampoco aplica la regla PEP por esa coincidencia
        val r = conHomonimos(descartado).evaluar("analista", datos())
        assertFalse("PEP" in r.reglas.map { it.codigo })
        assertFalse("SANCIONES" in r.reglas.map { it.codigo })
        assertNotNull(r.consulta?.homonimo)
    }
}
