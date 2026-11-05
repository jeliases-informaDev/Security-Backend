package com.security.modules.listas

import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaService
import com.security.modules.listas.application.service.ConsultaListasService
import com.security.modules.listas.application.service.HomonimosListasService
import com.security.modules.listas.application.service.ListasException
import com.security.modules.listas.application.service.ReglasListasService
import com.security.modules.listas.domain.ConsultaLista
import com.security.modules.listas.domain.ConsultaListaDecision
import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.domain.HistorialMancha
import com.security.modules.listas.domain.ListaGrupo
import com.security.modules.listas.domain.PersonaNatural
import com.security.modules.listas.domain.TipoLista
import com.security.modules.listas.domain.reglas.Clasificacion
import com.security.modules.listas.dto.ConsultaListasRequest
import com.security.modules.listas.infrastructure.ConsultaListaDecisionRepository
import com.security.modules.listas.infrastructure.ConsultaListaRepository
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.listas.infrastructure.ListaGrupoPesoRepository
import com.security.modules.listas.infrastructure.ListaReglaOtrasRepository
import com.security.modules.listas.infrastructure.ListaReglaRepository
import com.security.modules.listas.infrastructure.TipoListaRepository
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.TipoDocumento
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.VisibilidadEmpresaService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La consulta como unidad con la base simulada: visibilidad por empresa, homonimo y consultas sin coincidencias. */
class ConsultaListasServiceTest {

    private val entidades = mockk<EntidadRepository>()
    private val consultas = mockk<ConsultaListaRepository>()
    private val decisiones = mockk<ConsultaListaDecisionRepository>()
    private val usuarios = mockk<UsuarioRepository>()
    private val tiposLista = mockk<TipoListaRepository>()
    private val contexto = mockk<LicenciaContexto>()
    private val licencias = mockk<LicenciaService>(relaxed = true) // activo = false: no se cobra nada

    // Tablas de reglas vacias: se usan las del archivo de valores iniciales (las mismas que se siembran).
    private val reglas = ReglasListasService(
        mockk<ListaGrupoPesoRepository> { every { findAll() } returns emptyList() },
        mockk<ListaReglaRepository> { every { findAll() } returns emptyList() },
        mockk<ListaReglaOtrasRepository> { every { findAll() } returns emptyList() },
        tiposLista
    )
    private val service = ConsultaListasService(
        entidades, consultas, decisiones, usuarios, VisibilidadEmpresaService(usuarios), reglas, contexto, licencias
    )

    private val ahora = LocalDateTime.now()
    private val ofac = TipoLista(id = 3, nombre = "Lista OFAC", grupo = ListaGrupo(id = 1, orden = 1, nombre = "Listas Restrictivas"))
    private val guardadas = mutableMapOf<Long, ConsultaLista>()
    private val registro = mutableListOf<ConsultaListaDecision>()

    init {
        every { tiposLista.findAll() } returns listOf(ofac, TipoLista(id = 7, nombre = "Noticias"))
        every { contexto.uuidDeEmpresa(any()) } returns null
        every { consultas.save(any<ConsultaLista>()) } answers {
            val c = firstArg<ConsultaLista>()
            if (c.id == null) c.id = (guardadas.size + 1).toLong()
            guardadas[c.id!!] = c
            c
        }
        every { consultas.findById(any()) } answers { Optional.ofNullable(guardadas[firstArg()]) }
        every { decisiones.save(any<ConsultaListaDecision>()) } answers { firstArg<ConsultaListaDecision>().also { registro.add(it) } }
        every { decisiones.findByIdConsultaOrderByFechaAscIdAsc(any()) } answers { registro.filter { it.idConsulta == firstArg<Long>() } }
    }

    private fun cuenta(id: Int, nombre: String, empresa: String?, rol: String = "USUARIO"): Usuario {
        val u = Usuario(id = id, usuario = nombre, clave = "x", correo = "$nombre@example.test", empresa = empresa,
            nombres = "Prueba", apePat = "Cuenta$id", apeMat = "Ejemplo", fechaCreacion = ahora, fechaActualizacion = ahora)
        u.roles = mutableListOf(UsuarioRol(usuario = u, rol = Roles(id = 1, codigo = rol, nombre = rol, activo = true,
            fechaCreacion = ahora, fechaActualizacion = ahora)))
        every { usuarios.findByUsuario(nombre) } returns u
        return u
    }

    private fun personaEnOfac(): Entidad {
        val e = Entidad(id = 50, tipoDocumento = TipoDocumento(id = 1, nombre = "DNI"), documento = "40000001", tipoEntidad = Entidad.TIPO_NATURAL)
        e.personaNatural = PersonaNatural(entidad = e, nombre = "PERSONA", apePat = "DE", apeMat = "PRUEBA")
        e.manchas.add(HistorialMancha(id = 500, entidad = e, tipoLista = ofac, descripcion = "Registro de prueba"))
        every { entidades.cargarManchas(listOf(50)) } returns listOf(e)
        every { entidades.buscarPorIdConDetalle(50) } returns e
        return e
    }

    /** Una consulta hecha por documento por "ana" (empresa Gamma) que coincide en OFAC. */
    private fun consultaDeAna(): Long {
        cuenta(1, "ana", "Gamma")
        val e = personaEnOfac()
        every { entidades.buscar("40000001", "DNI", null, null, null, any()) } returns PageImpl(listOf(e), PageRequest.of(0, 20), 1)
        val r = service.consultar("ana", ConsultaListasRequest(tipoDocumento = "DNI", documento = "40000001"))
        return assertNotNull(r.consulta).id
    }

    @Test
    fun `consulta por documento con una persona - guarda la consulta con su porcentaje y la empresa`() {
        val id = consultaDeAna()
        val c = guardadas.getValue(id)
        assertEquals(ConsultaLista.ESTADO_RESUELTA, c.estado)
        assertEquals("Gamma", c.empresa)
        assertEquals(ConsultaLista.COINCIDENCIA_DOCUMENTO, c.coincidencia)
        assertEquals(Clasificacion.CRITICA, c.clasificacion)
        assertEquals(15, c.porcentaje)
        assertEquals(50, c.idEntidad)
        assertEquals("CLIENTE", c.tipoConsulta)
        val r = service.obtener("ana", id)
        assertEquals("Coincidencia exacta por DNI 40000001", r.porQueCoincide.titulo)
        assertTrue(r.acciones.marcarHomonimo)
        assertEquals("Prueba Cuenta1 E.", r.usuario.nombre)
    }

    @Test
    fun `una empresa nunca ve la consulta de otra`() {
        val id = consultaDeAna()
        cuenta(2, "luis", "Delta")
        cuenta(3, "jefaDelta", "Delta", rol = "ADMINISTRADOR_EMPRESA")
        cuenta(4, "maria", "gamma")
        cuenta(5, "jefaGamma", " Gamma ", rol = "ADMINISTRADOR_EMPRESA")
        cuenta(6, "sulotec", null, rol = "ADMINISTRADOR")

        assertFailsWith<ResourceNotFoundException> { service.obtener("luis", id) }
        assertFailsWith<ResourceNotFoundException> { service.obtener("jefaDelta", id) }
        assertFailsWith<ResourceNotFoundException> { service.pdf("jefaDelta", id) }
        assertFailsWith<ResourceNotFoundException> { service.marcarHomonimo("jefaDelta", id, "No es la persona") }
        // Una cuenta comun de la misma empresa solo ve lo suyo
        assertFailsWith<ResourceNotFoundException> { service.obtener("maria", id) }
        assertEquals(id, service.obtener("jefaGamma", id).id)
        assertEquals(id, service.obtener("sulotec", id).id)
        assertEquals(id, service.obtener("ana", id).id)
    }

    @Test
    fun `la empresa guardada en la consulta manda aunque la cuenta cambie de empresa`() {
        val id = consultaDeAna()
        usuarios.findByUsuario("ana")!!.empresa = "Delta"
        cuenta(3, "jefaDelta", "Delta", rol = "ADMINISTRADOR_EMPRESA")
        assertFailsWith<ResourceNotFoundException> { service.obtener("jefaDelta", id) }
    }

    @Test
    fun `el historial y las cifras se piden a la base con el alcance de la cuenta`() {
        cuenta(1, "ana", "Gamma")
        cuenta(5, "jefaGamma", "Gamma", rol = "ADMINISTRADOR_EMPRESA")
        every { consultas.historial(any(), any(), any(), any(), any(), any()) } returns PageImpl(emptyList())
        every { consultas.cifras(any(), any(), any(), any(), any()) } returns listOf(
            arrayOf<Any?>(Clasificacion.SIN_COINCIDENCIAS, 34L, 0L),
            arrayOf<Any?>(Clasificacion.MEDIA, 11L, 2L),
            arrayOf<Any?>(Clasificacion.CRITICA, 3L, 1L)
        )

        service.historial("ana", null, " Garcia ", 0, 10)
        verify { consultas.historial(false, null, 1, "TODAS", "%garcia%", PageRequest.of(0, 10)) }
        service.historial("jefaGamma", "criticas", null, 2, 10)
        verify { consultas.historial(false, "Gamma", null, "CRITICAS", null, PageRequest.of(2, 10)) }

        val e = assertFailsWith<ListasException> { service.historial("ana", "OTRAS", null, 0, 10) }
        assertEquals("LISTAS_FILTRO_INVALIDO", e.code)

        val resumen = service.resumen("jefaGamma", "2026-11")
        assertEquals(48L, resumen.consultas)
        assertEquals(14L, resumen.conCoincidencias)
        assertEquals(29, resumen.porcentajeConCoincidencias)
        assertEquals(34L, resumen.sinCoincidencias)
        assertEquals(71, resumen.porcentajeSinCoincidencias)
        assertEquals(3L, resumen.criticas)
        assertEquals(2L, resumen.criticasSinDecidir)
        verify { consultas.cifras(false, "Gamma", null, LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0)) }

        every { consultas.cifras(any(), any(), any(), any(), any()) } returns emptyList()
        assertNull(service.resumen("ana", null).porcentajeConCoincidencias)
        assertEquals("LISTAS_MES_INVALIDO", assertFailsWith<ListasException> { service.resumen("ana", "noviembre") }.code)
    }

    @Test
    fun `es homonimo - pide comentario, queda con quien y cuando, y se puede deshacer`() {
        val id = consultaDeAna()

        val sinComentario = assertFailsWith<ListasException> { service.marcarHomonimo("ana", id, "  ") }
        assertEquals("LISTAS_COMENTARIO_OBLIGATORIO", sinComentario.code)

        val marcada = service.marcarHomonimo("ana", id, "Otra fecha de nacimiento")
        assertEquals("DESCARTADA_HOMONIMO", marcada.estado)
        assertEquals("Descartada · homónimo", marcada.estadoNombre)
        assertEquals("Otra fecha de nacimiento", marcada.homonimo?.comentario)
        assertEquals("ana", marcada.homonimo?.usuario?.usuario)
        assertNotNull(marcada.homonimo?.fecha)
        assertFalse(marcada.acciones.marcarHomonimo)
        assertTrue(marcada.acciones.deshacerHomonimo)
        // El resultado guardado no cambia
        assertEquals(15, marcada.resultado.porcentaje)

        val repetida = assertFailsWith<ListasException> { service.marcarHomonimo("ana", id, "Otra vez") }
        assertEquals(HttpStatus.CONFLICT, repetida.status)

        val deshecha = service.deshacerHomonimo("ana", id, "Se marcó por error")
        assertEquals("VIGENTE", deshecha.estado)
        assertNull(deshecha.homonimo)
        assertEquals(listOf(ConsultaListaDecision.ACCION_HOMONIMO, ConsultaListaDecision.ACCION_DESHACER), deshecha.decisiones.map { it.accion })
        assertEquals(listOf("ana", "ana"), deshecha.decisiones.map { it.usuario.usuario })
        assertEquals("LISTAS_NO_ES_HOMONIMO", assertFailsWith<ListasException> { service.deshacerHomonimo("ana", id, null) }.code)
    }

    @Test
    fun `el scoring encuentra el homonimo vigente de la empresa`() {
        val id = consultaDeAna()
        val ana = usuarios.findByUsuario("ana")!!
        service.marcarHomonimo("ana", id, "No es la misma persona")
        val homonimos = HomonimosListasService(consultas)
        every { consultas.homonimosDe(50, "Gamma", 1, PageRequest.of(0, 1)) } returns listOf(guardadas.getValue(id))
        val h = assertNotNull(homonimos.vigente(ana, 50))
        assertEquals(id, h.consultaId)
        assertEquals("Prueba Cuenta1 E.", h.usuario)
        assertNull(homonimos.vigente(ana, null))
    }

    @Test
    fun `sin resultados tambien se guarda la consulta, y no se puede marcar homonimo`() {
        cuenta(1, "ana", "Gamma")
        every { entidades.buscar(any(), any(), any(), any(), any(), any()) } returns PageImpl(emptyList(), PageRequest.of(0, 20), 0)

        val r = service.consultar("ana", ConsultaListasRequest(tipoConsulta = "PROVEEDOR", nombres = "Empresa de prueba"))
        val c = assertNotNull(r.consulta)
        assertEquals("CONSULTA", r.resultado)
        assertEquals(Clasificacion.SIN_COINCIDENCIAS, c.resultado.clasificacion)
        assertEquals(0, c.resultado.porcentaje)
        assertEquals("SIN_RESULTADOS", c.porQueCoincide.tipo)
        assertTrue(c.scoring.clienteNuevo)
        assertEquals("Empresa de prueba", c.consultado.nombre)
        assertEquals("Proveedor", c.tipoConsultaNombre)
        assertFalse(c.acciones.marcarHomonimo)
        assertEquals("LISTAS_SIN_COINCIDENCIAS", assertFailsWith<ListasException> { service.marcarHomonimo("ana", c.id, "No aplica") }.code)
    }

    @Test
    fun `por nombre devuelve candidatos y elegir abre la consulta sin repetirla`() {
        cuenta(1, "ana", "Gamma")
        val e = personaEnOfac()
        every { entidades.buscar(null, null, "PERSONA", null, null, any()) } returns PageImpl(listOf(e), PageRequest.of(0, 20), 1)
        every { entidades.contarEnBusqueda(50, null, null, "PERSONA", null, null) } returns 1L
        every { consultas.findByIdBusqueda(any()) } answers { guardadas.values.filter { it.idBusqueda == firstArg<Long>() } }
        every { consultas.findFirstByIdBusquedaAndIdEntidadOrderByIdAsc(any(), any()) } answers {
            guardadas.values.firstOrNull { it.idBusqueda == firstArg<Long>() && it.idEntidad == secondArg<Int>() }
        }

        val r = service.consultar("ana", ConsultaListasRequest(nombres = "PERSONA"))
        assertEquals("CANDIDATOS", r.resultado)
        val busqueda = assertNotNull(r.busqueda)
        assertEquals(1, busqueda.candidatos.size)
        assertEquals(ConsultaLista.ESTADO_BUSQUEDA, guardadas.getValue(busqueda.id).estado)
        // La busqueda no se abre como consulta
        assertFailsWith<ResourceNotFoundException> { service.obtener("ana", busqueda.id) }

        val (consulta, creada) = service.elegir("ana", busqueda.id, 50)
        assertTrue(creada)
        assertEquals("POR_NOMBRE", consulta.porQueCoincide.tipo)
        assertNotNull(consulta.porQueCoincide.advertencia)
        assertEquals(busqueda.id, consulta.busquedaId)

        val (otraVez, creadaOtraVez) = service.elegir("ana", busqueda.id, 50)
        assertFalse(creadaOtraVez)
        assertEquals(consulta.id, otraVez.id)
        assertEquals(consulta.id, service.candidatos("ana", busqueda.id, 0, 20).candidatos.first().consultaId)

        every { entidades.contarEnBusqueda(99, null, null, "PERSONA", null, null) } returns 0L
        assertEquals("LISTAS_CANDIDATO_NO_ENCONTRADO", assertFailsWith<ListasException> { service.elegir("ana", busqueda.id, 99) }.code)
    }

    @Test
    fun `validaciones de la consulta`() {
        cuenta(1, "ana", "Gamma")
        fun codigo(r: ConsultaListasRequest) = assertFailsWith<ListasException> { service.consultar("ana", r) }.code
        assertEquals("LISTAS_CRITERIO_INSUFICIENTE", codigo(ConsultaListasRequest(nombres = "AB")))
        assertEquals("LISTAS_TIPO_CONSULTA_INVALIDO", codigo(ConsultaListasRequest(tipoConsulta = "OTRO", documento = "40000001")))
        assertEquals("LISTAS_TIPO_DOCUMENTO_NO_PERMITIDO",
            codigo(ConsultaListasRequest(tipoConsulta = "TRABAJADOR_DIRECTOR", tipoDocumento = "RUC", documento = "20000000001")))
        assertEquals("LISTAS_DOCUMENTO_INVALIDO", codigo(ConsultaListasRequest(tipoDocumento = "DNI", documento = "1234")))
    }
}
