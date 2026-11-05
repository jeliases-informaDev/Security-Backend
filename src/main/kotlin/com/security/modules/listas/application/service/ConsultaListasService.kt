package com.security.modules.listas.application.service

import com.security.modules.licencias.CobroAcciones
import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.dominio.TIPO_BUSQUEDA_LISTAS
import com.security.modules.licencias.dominio.TIPO_LISTA_PDF
import com.security.modules.listas.domain.ConsultaLista
import com.security.modules.listas.domain.ConsultaListaDecision
import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.domain.HistorialMancha
import com.security.modules.listas.domain.reglas.AparicionConsulta
import com.security.modules.listas.domain.reglas.CalculoListas
import com.security.modules.listas.domain.reglas.Clasificacion
import com.security.modules.listas.domain.reglas.FuenteResultado
import com.security.modules.listas.domain.reglas.RegistroLista
import com.security.modules.listas.domain.reglas.ResultadoListas
import com.security.modules.listas.dto.*
import com.security.modules.listas.infrastructure.ConsultaListaDecisionRepository
import com.security.modules.listas.infrastructure.ConsultaListaRepository
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.listas.infrastructure.export.ConsultaListaPdfService
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.Alcance
import com.security.shared.security.Vista
import com.security.shared.security.VisibilidadEmpresaService
import com.security.shared.security.limpiarEmpresa
import com.security.shared.security.mismaEmpresa
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeParseException

/** Error de negocio de Listas Negativas con su codigo (p. ej. LISTAS_CRITERIO_INSUFICIENTE). */
class ListasException(val status: HttpStatus, val code: String, message: String) : RuntimeException(message)

/** "Juan Perez G." a partir de la cuenta; sin nombres, el usuario. */
fun nombreCorto(u: Usuario?): String {
    if (u == null) return "—"
    val nombre = u.nombres?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
    val paterno = u.apePat?.trim()?.takeIf { it.isNotEmpty() }
    val materno = u.apeMat?.trim()?.firstOrNull()?.let { "$it." }
    val partes = listOfNotNull(nombre, paterno, materno)
    return if (nombre == null) u.usuario else partes.joinToString(" ")
}

/**
 * Consultas a Listas Negativas como unidad (ver docs/LISTAS-NEGATIVAS.md): la busqueda, los candidatos, el resultado
 * con su porcentaje y clasificacion (CalculoListas), el historial con sus cifras, la decision "Es homonimo" y el PDF.
 * Una empresa nunca ve las consultas de otra (VisibilidadEmpresaService, con la empresa guardada en la consulta).
 */
@Service
class ConsultaListasService(
    private val entidadRepository: EntidadRepository,
    private val consultaRepository: ConsultaListaRepository,
    private val decisionRepository: ConsultaListaDecisionRepository,
    private val usuarioRepository: UsuarioRepository,
    private val visibilidad: VisibilidadEmpresaService,
    private val reglasService: ReglasListasService,
    private val licenciaContexto: LicenciaContexto,
    private val licenciaService: LicenciaService,
    private val pdfService: ConsultaListaPdfService = ConsultaListaPdfService()
) {

    private val cobro = CobroAcciones(licenciaContexto, licenciaService)
    private val json = jacksonObjectMapper()

    companion object {
        const val TAMANO_CANDIDATOS = 20
        const val TAMANO_HISTORIAL = 10
        const val TAMANO_MAXIMO = 50
        const val CARACTERES_MINIMOS = 3
        const val ULTIMAS_INICIO = 3
        val FILTROS = setOf("TODAS", "CON_COINCIDENCIAS", "SIN_COINCIDENCIAS", "CRITICAS")
        const val ESTADO_VIGENTE = "VIGENTE"
        const val ESTADO_DESCARTADA = "DESCARTADA_HOMONIMO"
        const val PRONTO = "PRONTO"
        const val PIE_NOTICIAS =
            "Las noticias son información periodística y no prueban responsabilidad. Revisa la fecha y el estado actual del caso antes de decidir."
        const val ADVERTENCIA_NOMBRE = "Coincidencia por nombre: verifica que no sea un homónimo."
    }

    /** Lo que se escribio, ya limpio y validado. */
    private data class Criterio(
        val tipoConsulta: String,
        val tipoDocumento: String?,
        val documento: String?,
        val nombres: String?,
        val apellidoPaterno: String?,
        val apellidoMaterno: String?
    ) {
        val porDocumento get() = documento != null
        val texto: String?
            get() = listOfNotNull(
                documento?.let { listOfNotNull(tipoDocumento, it).joinToString(" ") }, nombres, apellidoPaterno, apellidoMaterno
            ).joinToString(" ").ifBlank { null }
        val nombreEscrito: String?
            get() = listOfNotNull(nombres, apellidoPaterno, apellidoMaterno).joinToString(" ").ifBlank { null }
    }

    // =====================================================================
    // Catalogo de la pantalla
    // =====================================================================

    @Transactional(readOnly = true)
    fun catalogo(): CatalogoConsultaResponse {
        val reglas = reglasService.reglas()
        val listas = reglasService.listasDisponibles()
        val filas = CalculoListas.catalogo(reglas, listas)
        val grupos = reglas.gruposPeso.sortedBy { it.orden }
        val semilla = reglasService.semilla
        return CatalogoConsultaResponse(
            tiposConsulta = semilla.tiposConsulta,
            tipoConsultaInicial = semilla.tipoConsultaInicial,
            tiposDocumento = semilla.tiposDocumento,
            tipoDocumentoInicial = semilla.tipoDocumentoInicial,
            gruposPeso = grupos.map { GrupoPesoResponse(it.codigo, it.nombre, it.peso, it.critica) },
            leyenda = CalculoListas.leyenda(reglas),
            filas = filas,
            fuentes = filas.groupBy { it.fuente }.map { (codigo, fs) ->
                FuenteResultado(codigo, fs.first().fuenteNombre, if (fs.any { it.disponible }) "CONSULTABLE" else PRONTO)
            },
            listasConsultables = filas.count { it.disponible },
            otrasListas = reglas.otras.sortedBy { it.ordenGrupo }.map { o ->
                val g = grupos.firstOrNull { it.codigo.equals(o.grupoPeso, ignoreCase = true) }
                OtrasListasReglaResponse(o.ordenGrupo, g?.nombre, o.grupoPeso, g?.peso ?: 0)
            },
            fechaBase = listas.mapNotNull { it.ultimaCarga }.maxOrNull()
        )
    }

    // =====================================================================
    // Consultar
    // =====================================================================

    /**
     * Hace la busqueda (consume busqueda_listas una vez, con la misma clave de dos minutos que GET /buscar) y:
     *  - documento con una sola persona -> consulta lista (coincidencia exacta por documento);
     *  - nadie -> consulta sin coincidencias (tambien se guarda);
     *  - por nombre, o un documento repetido -> candidatos para elegir (elegir no vuelve a cobrar).
     */
    @Transactional
    fun consultar(username: String, request: ConsultaListasRequest): ConsultaCreadaResponse {
        val usuario = usuarioRepository.findByUsuario(username) ?: throw ResourceNotFoundException("Usuario no encontrado")
        val c = validar(request)
        val clave = consumirBusqueda(c)
        return try {
            val pagina = buscarEntidades(c, PageRequest.of(0, TAMANO_CANDIDATOS))
            when {
                pagina.totalElements == 0L -> {
                    val consulta = resolver(nueva(usuario, c), null, ConsultaLista.COINCIDENCIA_SIN_RESULTADOS)
                    ConsultaCreadaResponse("CONSULTA", respuesta(consulta), null)
                }
                c.porDocumento && pagina.totalElements == 1L -> {
                    val consulta = resolver(nueva(usuario, c), pagina.content.first(), ConsultaLista.COINCIDENCIA_DOCUMENTO)
                    ConsultaCreadaResponse("CONSULTA", respuesta(consulta), null)
                }
                else -> {
                    val busqueda = nueva(usuario, c).apply {
                        estado = ConsultaLista.ESTADO_BUSQUEDA
                        totalCandidatos = pagina.totalElements
                    }
                    consultaRepository.save(busqueda)
                    ConsultaCreadaResponse("CANDIDATOS", null, candidatosDe(busqueda, pagina))
                }
            }
        } catch (e: Exception) {
            clave?.let { licenciaService.anular(it) }
            throw e
        }
    }

    /** Otra pagina de candidatos de una busqueda ya hecha (no consume). */
    @Transactional(readOnly = true)
    fun candidatos(username: String, busquedaId: Long, page: Int, size: Int): BusquedaCandidatosResponse {
        val busqueda = busquedaVisible(username, busquedaId)
        validarPagina(page, size)
        val pagina = buscarEntidades(criterioDe(busqueda), PageRequest.of(page, minOf(size, TAMANO_MAXIMO)))
        return candidatosDe(busqueda, pagina)
    }

    /**
     * Abre el resultado de un candidato (no consume). Si ya se eligio en esta busqueda, devuelve esa misma consulta.
     * Devuelve la consulta y si se creo ahora.
     */
    @Transactional
    fun elegir(username: String, busquedaId: Long, entidadId: Int?): Pair<ConsultaResponse, Boolean> {
        val busqueda = busquedaVisible(username, busquedaId)
        val id = entidadId ?: throw ListasException(HttpStatus.BAD_REQUEST, "LISTAS_CANDIDATO_OBLIGATORIO", "Elige una persona de la lista.")
        consultaRepository.findFirstByIdBusquedaAndIdEntidadOrderByIdAsc(busquedaId, id)?.let { return respuesta(it) to false }

        val c = criterioDe(busqueda)
        val enBusqueda = if (c.porDocumento) {
            entidadRepository.contarEnBusqueda(id, c.documento, c.tipoDocumento, null, null, null)
        } else {
            entidadRepository.contarEnBusqueda(id, null, null, c.nombres, c.apellidoPaterno, c.apellidoMaterno)
        }
        val entidad: Entidad = (if (enBusqueda > 0) entidadRepository.buscarPorIdConDetalle(id) else null)
            ?: throw ListasException(HttpStatus.NOT_FOUND, "LISTAS_CANDIDATO_NO_ENCONTRADO", "Esa persona no está entre los resultados de la búsqueda.")

        val usuario = usuarioRepository.findByUsuario(username) ?: throw ResourceNotFoundException("Usuario no encontrado")
        val consulta = nueva(usuario, c).apply { idBusqueda = busqueda.id }
        val coincidencia = if (c.porDocumento) ConsultaLista.COINCIDENCIA_DOCUMENTO else ConsultaLista.COINCIDENCIA_NOMBRE
        return respuesta(resolver(consulta, entidad, coincidencia)) to true
    }

    @Transactional(readOnly = true)
    fun obtener(username: String, id: Long): ConsultaResponse = respuesta(consultaVisible(username, id))

    // =====================================================================
    // Historial, cifras y pagina principal
    // =====================================================================

    @Transactional(readOnly = true)
    fun historial(username: String, filtro: String?, q: String?, page: Int, size: Int): PaginaResponse<ConsultaHistorialResponse> {
        val f = filtro?.trim()?.uppercase()?.ifBlank { null } ?: "TODAS"
        if (f !in FILTROS) {
            throw ListasException(HttpStatus.BAD_REQUEST, "LISTAS_FILTRO_INVALIDO", "El filtro debe ser TODAS, CON_COINCIDENCIAS, SIN_COINCIDENCIAS o CRITICAS.")
        }
        validarPagina(page, size)
        val texto = q?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { "%$it%" }
        val a = alcance(visibilidad.de(username))
        val pagina = consultaRepository.historial(a.todo, a.empresa, a.usuarioId, f, texto, PageRequest.of(page, minOf(size, TAMANO_MAXIMO)))
        return PaginaResponse(pagina.content.map { fila(it) }, pagina.totalElements, pagina.totalPages, pagina.number, pagina.size)
    }

    /** Las cuatro cifras del mes (el mes en curso si no se indica). */
    @Transactional(readOnly = true)
    fun resumen(username: String, mes: String?): ResumenConsultasResponse {
        val periodo = try {
            mes?.trim()?.takeIf { it.isNotEmpty() }?.let { YearMonth.parse(it) } ?: YearMonth.now()
        } catch (_: DateTimeParseException) {
            throw ListasException(HttpStatus.BAD_REQUEST, "LISTAS_MES_INVALIDO", "El mes debe tener la forma AAAA-MM (por ejemplo 2026-11).")
        }
        val a = alcance(visibilidad.de(username))
        val filas = consultaRepository.cifras(
            a.todo, a.empresa, a.usuarioId, periodo.atDay(1).atStartOfDay(), periodo.plusMonths(1).atDay(1).atStartOfDay()
        )
        var total = 0L
        var sin = 0L
        var criticas = 0L
        var criticasHomonimo = 0L
        filas.forEach { r ->
            val n = (r.getOrNull(1) as? Number)?.toLong() ?: 0L
            val homonimos = (r.getOrNull(2) as? Number)?.toLong() ?: 0L
            total += n
            when (r.getOrNull(0) as? String) {
                Clasificacion.SIN_COINCIDENCIAS -> sin += n
                Clasificacion.CRITICA -> { criticas += n; criticasHomonimo += homonimos }
            }
        }
        val con = total - sin
        fun pct(n: Long): Int? = if (total == 0L) null else Math.round(n * 100.0 / total).toInt()
        return ResumenConsultasResponse(
            periodo = periodo.toString(), consultas = total, conCoincidencias = con, porcentajeConCoincidencias = pct(con),
            criticas = criticas, criticasSinDecidir = criticas - criticasHomonimo, sinCoincidencias = sin,
            porcentajeSinCoincidencias = pct(sin)
        )
    }

    /** "Ultimas busquedas" de la pagina principal y la rueda: el porcentaje mas alto de esas consultas. */
    @Transactional(readOnly = true)
    fun inicio(username: String): InicioListasResponse {
        val ultimas = historial(username, "TODAS", null, 0, ULTIMAS_INICIO).content
        val mayor = ultimas.filter { it.porcentaje != null }.maxByOrNull { it.porcentaje!! }
        return InicioListasResponse(ultimas, RuedaResponse(mayor?.porcentaje, mayor?.id, mayor?.clasificacion))
    }

    // =====================================================================
    // Es homonimo
    // =====================================================================

    @Transactional
    fun marcarHomonimo(username: String, id: Long, comentario: String?): ConsultaResponse {
        val consulta = consultaVisible(username, id)
        val texto = comentario?.trim()?.takeIf { it.length >= CARACTERES_MINIMOS }
            ?: throw ListasException(HttpStatus.BAD_REQUEST, "LISTAS_COMENTARIO_OBLIGATORIO", "Escribe por qué no es la misma persona (al menos 3 letras).")
        if ((consulta.listasConCoincidencia ?: 0) == 0) {
            throw ListasException(HttpStatus.BAD_REQUEST, "LISTAS_SIN_COINCIDENCIAS", "Una consulta sin coincidencias no se puede marcar como homónimo.")
        }
        if (consulta.homonimo) {
            throw ListasException(HttpStatus.CONFLICT, "LISTAS_YA_HOMONIMO", "Esta consulta ya está descartada como homónimo.")
        }
        val usuario = usuarioRepository.findByUsuario(username) ?: throw ResourceNotFoundException("Usuario no encontrado")
        val ahora = LocalDateTime.now().withNano(0)
        consulta.homonimo = true
        consulta.homonimoComentario = texto.take(2000)
        consulta.homonimoPor = usuario
        consulta.homonimoFecha = ahora
        consultaRepository.save(consulta)
        decisionRepository.save(ConsultaListaDecision(idConsulta = consulta.id!!, accion = ConsultaListaDecision.ACCION_HOMONIMO, comentario = consulta.homonimoComentario, usuario = usuario, fecha = ahora))
        return respuesta(consulta)
    }

    @Transactional
    fun deshacerHomonimo(username: String, id: Long, comentario: String?): ConsultaResponse {
        val consulta = consultaVisible(username, id)
        if (!consulta.homonimo) {
            throw ListasException(HttpStatus.CONFLICT, "LISTAS_NO_ES_HOMONIMO", "Esta consulta no está marcada como homónimo.")
        }
        val usuario = usuarioRepository.findByUsuario(username) ?: throw ResourceNotFoundException("Usuario no encontrado")
        consulta.homonimo = false
        consulta.homonimoComentario = null
        consulta.homonimoPor = null
        consulta.homonimoFecha = null
        consultaRepository.save(consulta)
        decisionRepository.save(
            ConsultaListaDecision(
                idConsulta = consulta.id!!, accion = ConsultaListaDecision.ACCION_DESHACER,
                comentario = comentario?.trim()?.takeIf { it.isNotEmpty() }?.take(2000), usuario = usuario,
                fecha = LocalDateTime.now().withNano(0)
            )
        )
        return respuesta(consulta)
    }

    // =====================================================================
    // PDF
    // =====================================================================

    /** PDF de la consulta; consume lista_pdf (misma clave durante dos minutos; se anula si el PDF falla). */
    @Transactional(readOnly = true)
    fun pdf(username: String, id: Long): ByteArray {
        val consulta = respuesta(consultaVisible(username, id))
        return cobro.ejecutar(TIPO_LISTA_PDF, "PDF de la consulta de listas $id", criterios = listOf("consulta", id.toString())) {
            pdfService.generar(consulta, username)
        }
    }

    // =====================================================================
    // Piezas
    // =====================================================================

    private fun validar(r: ConsultaListasRequest): Criterio {
        val semilla = reglasService.semilla
        val tipoConsulta = r.tipoConsulta?.trim()?.uppercase()?.ifBlank { null } ?: semilla.tipoConsultaInicial ?: "CLIENTE"
        val def = semilla.tiposConsulta.firstOrNull { it.codigo == tipoConsulta }
            ?: throw ListasException(
                HttpStatus.BAD_REQUEST, "LISTAS_TIPO_CONSULTA_INVALIDO",
                "«¿A quién consultas?» debe ser ${semilla.tiposConsulta.joinToString(", ") { it.codigo }}."
            )
        val tipoDocumento = r.tipoDocumento?.trim()?.uppercase()?.ifBlank { null }
        if (tipoDocumento != null && tipoDocumento !in def.tiposDocumento) {
            throw ListasException(
                HttpStatus.BAD_REQUEST, "LISTAS_TIPO_DOCUMENTO_NO_PERMITIDO",
                "Para «${def.nombre}» el documento puede ser ${def.tiposDocumento.joinToString(", ")}."
            )
        }
        val documento = r.documento?.trim()?.ifBlank { null }
        if (documento != null && tipoDocumento != null) {
            val tipo = semilla.tiposDocumento.firstOrNull { it.codigo == tipoDocumento }
            val patron = tipo?.patron?.let { Regex(it) }
            if (patron != null && !patron.matches(documento)) {
                throw ListasException(HttpStatus.BAD_REQUEST, "LISTAS_DOCUMENTO_INVALIDO", tipo?.ayuda ?: "El número de documento no es válido.")
            }
        }
        val nombres = r.nombres?.trim()?.ifBlank { null }
        val paterno = r.apellidoPaterno?.trim()?.ifBlank { null }
        val materno = r.apellidoMaterno?.trim()?.ifBlank { null }
        if (documento == null && listOfNotNull(nombres, paterno, materno).none { it.length >= CARACTERES_MINIMOS }) {
            throw ListasException(
                HttpStatus.BAD_REQUEST, "LISTAS_CRITERIO_INSUFICIENTE",
                "Escribe el documento o al menos $CARACTERES_MINIMOS letras del nombre o de un apellido."
            )
        }
        return Criterio(tipoConsulta, tipoDocumento, documento, nombres, paterno, materno)
    }

    /** Misma clave que GET /buscar con los mismos criterios: buscar y consultar en dos minutos no cobra dos veces. */
    private fun consumirBusqueda(c: Criterio): String? {
        if (!licenciaService.activo) return null
        val contexto = licenciaContexto.actual() ?: return null
        val uuid = contexto.empresaUuid ?: return null
        val clave = licenciaService.claveBusqueda(
            uuid, contexto.usuario, listOf(c.documento, c.tipoDocumento, c.nombres, c.apellidoPaterno, c.apellidoMaterno)
        )
        licenciaService.consumirComo(contexto, TIPO_BUSQUEDA_LISTAS, clave, "consulta en listas negativas")
        return clave
    }

    // Por documento: exacta por documento (y tipo, si se indico). Por nombre: el tipo de documento no filtra (el
    // formulario siempre trae uno elegido y dejaria fuera a quien figura con otro).
    private fun buscarEntidades(c: Criterio, pageable: PageRequest): Page<Entidad> = if (c.porDocumento) {
        entidadRepository.buscar(c.documento, c.tipoDocumento, null, null, null, pageable)
    } else {
        entidadRepository.buscar(null, null, c.nombres, c.apellidoPaterno, c.apellidoMaterno, pageable)
    }

    private fun nueva(usuario: Usuario, c: Criterio): ConsultaLista {
        val empresa = limpiarEmpresa(usuario.empresa)
        return ConsultaLista(
            usuario = usuario,
            empresa = empresa,
            empresaUuid = empresa?.let { licenciaContexto.uuidDeEmpresa(it) },
            fecha = LocalDateTime.now().withNano(0),
            tipoConsulta = c.tipoConsulta,
            criterioTipoDocumento = c.tipoDocumento,
            criterioDocumento = c.documento?.take(50),
            criterioNombres = c.nombres?.take(200),
            criterioApellidoPaterno = c.apellidoPaterno?.take(120),
            criterioApellidoMaterno = c.apellidoMaterno?.take(120),
            criterioTexto = c.texto?.take(500),
            modo = if (c.porDocumento) ConsultaLista.MODO_DOCUMENTO else ConsultaLista.MODO_NOMBRE
        )
    }

    private fun criterioDe(b: ConsultaLista) = Criterio(
        b.tipoConsulta, b.criterioTipoDocumento,
        if (b.modo == ConsultaLista.MODO_DOCUMENTO) b.criterioDocumento else null,
        b.criterioNombres, b.criterioApellidoPaterno, b.criterioApellidoMaterno
    )

    /** Calcula el resultado de la persona (o de nadie) con las reglas de hoy y lo guarda tal cual. */
    private fun resolver(consulta: ConsultaLista, entidad: Entidad?, coincidencia: String): ConsultaLista {
        val conManchas = entidad?.id?.let { entidadRepository.cargarManchas(listOf(it)).firstOrNull() } ?: entidad
        val apariciones = conManchas?.manchas.orEmpty().sortedByDescending { it.fechaRegistro }.map { aparicion(it) }
        val r = CalculoListas.calcular(reglasService.reglas(), reglasService.listasDisponibles(), apariciones)
        val c = criterioDe(consulta)
        consulta.apply {
            estado = ConsultaLista.ESTADO_RESUELTA
            this.coincidencia = coincidencia
            idEntidad = entidad?.id
            consultadoNombre = (entidad?.let { nombreDe(it) } ?: c.nombreEscrito)?.take(300)
            consultadoTipoDocumento = entidad?.tipoDocumento?.nombre ?: c.tipoDocumento
            consultadoDocumento = (entidad?.documento ?: consulta.criterioDocumento)?.take(50)
            tipoPersona = entidad?.tipoEntidad
            consultadoPais = entidad?.pais?.nombre?.take(120)
            porcentaje = r.porcentaje
            clasificacion = r.clasificacion
            listasConsultadas = r.listasConsultadas
            listasConCoincidencia = r.listasConCoincidencia
            registrosEnListas = r.registrosEnListas
            noticiasEnMedios = r.noticiasEnMedios
            esPep = r.esPep
            listasCoincidenciaTexto = r.nombresConCoincidencia().joinToString(" | ").ifBlank { null }
            resultado = json.writeValueAsString(r)
        }
        return consultaRepository.save(consulta)
    }

    private fun aparicion(m: HistorialMancha): AparicionConsulta {
        val t = m.tipoLista
        return AparicionConsulta(
            tipoListaId = t.id, nombreLista = t.nombre, esPep = t.esPep, ordenGrupo = t.grupo?.orden, nombreGrupo = t.grupo?.nombre,
            registro = RegistroLista(
                id = m.id, lista = t.nombre, esPep = t.esPep, descripcion = m.descripcion, link = m.link,
                dominio = CalculoListas.dominio(m.link), fechaRegistro = m.fechaRegistro, fechaHasta = m.fechaHasta,
                institucion = m.institucion, cargo = m.cargo, tipoPep = m.tipoPep, periodoDesde = m.periodoDesde,
                periodoHasta = m.periodoHasta,
                sinPeriodo = t.esPep && CalculoListas.periodoVacio(m.periodoDesde) && CalculoListas.periodoVacio(m.periodoHasta)
            )
        )
    }

    private fun candidatosDe(busqueda: ConsultaLista, pagina: Page<Entidad>): BusquedaCandidatosResponse {
        val ids = pagina.content.mapNotNull { it.id }
        val conManchas = if (ids.isEmpty()) emptyMap() else entidadRepository.cargarManchas(ids).associateBy { it.id }
        val elegidas = busqueda.id?.let { b -> consultaRepository.findByIdBusqueda(b).associate { it.idEntidad to it.id } }.orEmpty()
        val candidatos = pagina.content.map { e ->
            val manchas = (conManchas[e.id] ?: e).manchas
            CandidatoResponse(
                entidadId = e.id!!, nombre = nombreDe(e), tipoDocumento = e.tipoDocumento.nombre, documento = e.documento,
                tipoPersona = e.tipoEntidad, pais = e.pais?.nombre, alias = e.alias,
                esPep = manchas.any { it.tipoLista.esPep }, apariciones = manchas.size,
                listas = manchas.mapNotNull { it.tipoLista.nombre }.distinct(), consultaId = elegidas[e.id]
            )
        }
        return BusquedaCandidatosResponse(
            id = busqueda.id!!, fecha = busqueda.fecha, tipoConsulta = busqueda.tipoConsulta, modo = busqueda.modo,
            criterio = criterioResponse(busqueda), candidatos = candidatos, totalElements = pagina.totalElements,
            totalPages = pagina.totalPages, number = pagina.number, size = pagina.size
        )
    }

    private data class AlcanceConsultas(val todo: Boolean, val empresa: String?, val usuarioId: Int?)

    private fun alcance(v: Vista) = when (v.alcance) {
        Alcance.TODO -> AlcanceConsultas(true, null, null)
        Alcance.EMPRESA -> AlcanceConsultas(false, v.empresa, null)
        Alcance.PROPIO -> AlcanceConsultas(false, null, v.usuario.id)
    }

    /** Si la cuenta puede ver la consulta: la empresa es la que se guardo en la consulta, no la de hoy de quien la hizo. */
    fun puedeVer(v: Vista, c: ConsultaLista): Boolean = when (v.alcance) {
        Alcance.TODO -> true
        Alcance.EMPRESA -> mismaEmpresa(v.empresa, c.empresa)
        Alcance.PROPIO -> v.usuario.id != null && c.usuario.id == v.usuario.id
    }

    // Una consulta de otra empresa responde igual que una que no existe.
    private fun cargarVisible(username: String, id: Long): ConsultaLista {
        val vista = visibilidad.de(username)
        val c = consultaRepository.findById(id).orElse(null)
        if (c == null || !puedeVer(vista, c)) throw ResourceNotFoundException("No se encontró la consulta")
        return c
    }

    private fun consultaVisible(username: String, id: Long): ConsultaLista {
        val c = cargarVisible(username, id)
        if (c.estado != ConsultaLista.ESTADO_RESUELTA) throw ResourceNotFoundException("No se encontró la consulta")
        return c
    }

    private fun busquedaVisible(username: String, id: Long): ConsultaLista {
        val c = cargarVisible(username, id)
        if (c.estado != ConsultaLista.ESTADO_BUSQUEDA) throw ResourceNotFoundException("No se encontró la búsqueda")
        return c
    }

    private fun validarPagina(page: Int, size: Int) {
        if (page < 0 || size < 1) {
            throw ListasException(HttpStatus.BAD_REQUEST, "LISTAS_PAGINA_INVALIDA", "La página debe ser 0 o mayor y el tamaño, al menos 1.")
        }
    }

    private fun criterioResponse(c: ConsultaLista) = CriterioResponse(
        c.criterioTipoDocumento, c.criterioDocumento, c.criterioNombres, c.criterioApellidoPaterno, c.criterioApellidoMaterno, c.criterioTexto
    )

    private fun nombreTipoConsulta(codigo: String) =
        reglasService.semilla.tiposConsulta.firstOrNull { it.codigo == codigo }?.nombre ?: codigo

    private fun resultadoDe(c: ConsultaLista): ResultadoListas =
        c.resultado?.let { json.readValue<ResultadoListas>(it) }
            ?: CalculoListas.calcular(reglasService.reglas(), reglasService.listasDisponibles(), emptyList())

    private fun fila(c: ConsultaLista) = ConsultaHistorialResponse(
        id = c.id!!, fecha = c.fecha, consultado = c.consultadoNombre ?: c.criterioTexto,
        tipoDocumento = c.consultadoTipoDocumento, documento = c.consultadoDocumento, tipoConsulta = c.tipoConsulta,
        tipoConsultaNombre = nombreTipoConsulta(c.tipoConsulta), clasificacion = c.clasificacion,
        clasificacionNombre = Clasificacion.nombre(c.clasificacion), porcentaje = c.porcentaje,
        listasConCoincidencia = c.listasCoincidenciaTexto?.split(" | ")?.filter { it.isNotBlank() }.orEmpty(),
        usuario = nombreCorto(c.usuario), estado = if (c.homonimo) ESTADO_DESCARTADA else ESTADO_VIGENTE, homonimo = c.homonimo
    )

    private fun respuesta(c: ConsultaLista): ConsultaResponse {
        val r = resultadoDe(c)
        val conCoincidencia = r.listasConCoincidencia > 0
        val porQue = when (c.coincidencia) {
            ConsultaLista.COINCIDENCIA_DOCUMENTO -> PorQueCoincideResponse(
                c.coincidencia!!, "Coincidencia exacta por ${listOfNotNull(c.consultadoTipoDocumento, c.consultadoDocumento).joinToString(" ")}",
                listOfNotNull(c.consultadoNombre, c.consultadoPais).joinToString(" · ").ifBlank { null }, null
            )
            ConsultaLista.COINCIDENCIA_NOMBRE -> PorQueCoincideResponse(
                c.coincidencia!!, "Coincidencia por nombre", "Se buscó: ${c.criterioTexto ?: "—"}", ADVERTENCIA_NOMBRE
            )
            else -> PorQueCoincideResponse(
                ConsultaLista.COINCIDENCIA_SIN_RESULTADOS, "No figura en la base de listas", c.criterioTexto?.let { "Se buscó: $it" }, null
            )
        }
        val decisiones = c.id?.let { decisionRepository.findByIdConsultaOrderByFechaAscIdAsc(it) }.orEmpty().map {
            DecisionRegistroResponse(it.accion, it.comentario, UsuarioCortoResponse(it.usuario.usuario, nombreCorto(it.usuario)), it.fecha)
        }
        return ConsultaResponse(
            id = c.id!!,
            fecha = c.fecha,
            tipoConsulta = c.tipoConsulta,
            tipoConsultaNombre = nombreTipoConsulta(c.tipoConsulta),
            usuario = UsuarioCortoResponse(c.usuario.usuario, nombreCorto(c.usuario)),
            criterio = criterioResponse(c),
            busquedaId = c.idBusqueda,
            consultado = ConsultadoResponse(
                c.idEntidad, c.consultadoNombre, c.consultadoTipoDocumento, c.consultadoDocumento, c.tipoPersona, c.consultadoPais, r.esPep
            ),
            porQueCoincide = porQue,
            resultado = r,
            estado = if (c.homonimo) ESTADO_DESCARTADA else ESTADO_VIGENTE,
            estadoNombre = if (c.homonimo) "Descartada · homónimo" else "Vigente",
            homonimo = if (c.homonimo) {
                HomonimoResponse(c.homonimoComentario, c.homonimoPor?.let { UsuarioCortoResponse(it.usuario, nombreCorto(it)) }, c.homonimoFecha)
            } else null,
            decisiones = decisiones,
            acciones = AccionesResponse(
                verRegistros = conCoincidencia, marcarHomonimo = conCoincidencia && !c.homonimo, deshacerHomonimo = c.homonimo,
                reportePdf = true, continuarScoring = true, programarBusqueda = PRONTO, consultaMasiva = PRONTO
            ),
            scoring = ScoringDesdeConsultaResponse(
                consultaId = c.id!!, entidadId = c.idEntidad, clienteNuevo = c.idEntidad == null,
                tipoDocumento = c.consultadoTipoDocumento, documento = c.consultadoDocumento, nombre = c.consultadoNombre,
                tipoPersona = c.tipoPersona
            ),
            pieNoticias = PIE_NOTICIAS
        )
    }

    private fun nombreDe(e: Entidad): String = when {
        e.personaNatural != null -> listOfNotNull(
            e.personaNatural?.nombre, e.personaNatural?.segundoNombre, e.personaNatural?.apePat, e.personaNatural?.apeMat
        ).joinToString(" ")
        else -> e.personaJuridica?.razonSocial ?: ""
    }
}
