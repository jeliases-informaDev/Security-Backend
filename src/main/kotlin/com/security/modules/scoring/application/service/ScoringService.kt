package com.security.modules.scoring.application.service

import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaReglas
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.dominio.TIPO_SCORING
import com.security.modules.listas.application.service.HomonimoVigente
import com.security.modules.listas.application.service.HomonimosListasService
import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.scoring.domain.ScoringDepartamento
import com.security.modules.scoring.domain.ScoringOcupacion
import com.security.modules.scoring.domain.ScoringOpcion
import com.security.modules.scoring.domain.ScoringPaisGrupo
import com.security.modules.scoring.domain.ScoringRiesgo
import com.security.modules.scoring.domain.metodologia.AparicionLista
import com.security.modules.scoring.domain.metodologia.Bloqueo
import com.security.modules.scoring.domain.metodologia.CalculoScoring
import com.security.modules.scoring.domain.metodologia.CampoScoring
import com.security.modules.scoring.domain.metodologia.ClasificadorListas
import com.security.modules.scoring.domain.metodologia.CodigoRegla
import com.security.modules.scoring.domain.metodologia.CondicionCliente
import com.security.modules.scoring.domain.metodologia.ConsultaListasResultado
import com.security.modules.scoring.domain.metodologia.EntradaCalculo
import com.security.modules.scoring.domain.metodologia.MetodologiaV1
import com.security.modules.scoring.domain.metodologia.MetodologiaV2
import com.security.modules.scoring.domain.metodologia.Nivel
import com.security.modules.scoring.domain.metodologia.OpcionElegida
import com.security.modules.scoring.domain.metodologia.PaisElegido
import com.security.modules.scoring.domain.metodologia.ResultadoCalculo
import com.security.modules.scoring.dto.*
import com.security.modules.scoring.infrastructure.ScoringDepartamentoRepository
import com.security.modules.scoring.infrastructure.ScoringOcupacionRepository
import com.security.modules.scoring.infrastructure.ScoringOpcionRepository
import com.security.modules.scoring.infrastructure.ScoringPaisGrupoRepository
import com.security.modules.scoring.infrastructure.ScoringRiesgoRepository
import com.security.modules.scoring.infrastructure.ScoringSustentoRepository
import com.security.modules.scoring.infrastructure.VolumenOperacionesConsulta
import com.security.shared.datos.entities.Pais
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.PaisRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.Alcance
import com.security.shared.security.VisibilidadEmpresaService
import com.security.shared.security.limpiarEmpresa
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Scoring de riesgo LA/FT, metodologia version 2 (ver docs/SCORING.md). El calculo esta en CalculoScoring (puro);
 * aqui se juntan los datos (catalogos, Listas Negativas y Registro de Operaciones), se guarda, se consume el
 * token y se aplica la visibilidad por empresa.
 */
@Service
class ScoringService(
    private val entidadRepository: EntidadRepository,
    private val ocupacionRepository: ScoringOcupacionRepository,
    private val departamentoRepository: ScoringDepartamentoRepository,
    private val opcionRepository: ScoringOpcionRepository,
    private val paisGrupoRepository: ScoringPaisGrupoRepository,
    private val paisRepository: PaisRepository,
    private val scoringRiesgoRepository: ScoringRiesgoRepository,
    private val sustentoRepository: ScoringSustentoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val visibilidad: VisibilidadEmpresaService,
    private val licenciaContexto: LicenciaContexto,
    private val licenciaService: LicenciaService,
    private val volumenOperaciones: VolumenOperacionesConsulta,
    // Descartes "Es homonimo" de Listas Negativas (null en las pruebas que arman el servicio a mano: no hay descartes)
    private val homonimos: HomonimosListasService? = null
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val metodologia = MetodologiaV2.ACTUAL
    private val json = jacksonObjectMapper()

    // =====================================================================
    // Catalogos
    // =====================================================================

    @Transactional(readOnly = true)
    fun obtenerCatalogos(): CatalogosScoringResponse {
        val grupos = paisGrupoRepository.todosConPais().associateBy { it.pais.id }
        val paises = paisRepository.findAll()
            .filter { !it.nombre.isNullOrBlank() }
            .sortedWith(compareBy<Pais>({ if (esPeru(it.nombre)) 0 else 1 }, { CalculoScoring.normalizar(it.nombre!!) }))
            .map { p ->
                val g = grupos[p.id]
                PaisCatalogo(p.id!!, p.nombre!!, g?.grupo, g?.altoRiesgoGafi == true)
            }
        return CatalogosScoringResponse(
            metodologia = MetodologiaInfo(metodologia.version, metodologia.fecha),
            ocupaciones = ocupacionRepository.findAll().sortedBy { it.nombre }.map { OpcionCatalogo(it.id!!, it.nombre, it.puntajeV2) },
            productos = opciones(ScoringOpcion.PRODUCTO).map { OpcionCatalogo(it.id!!, it.nombre, it.puntaje) },
            canales = opciones(ScoringOpcion.CANAL).map { OpcionCatalogo(it.id!!, it.nombre, it.puntaje) },
            mediosPago = opciones(ScoringOpcion.MEDIO_PAGO).map {
                MedioPagoCatalogo(it.id!!, it.nombre, it.puntaje, it.pideOrigenFondos == true)
            },
            departamentos = departamentoRepository.findAll().sortedBy { it.nombre }.map { OpcionCatalogo(it.id!!, it.nombre, it.puntajeV2) },
            paises = paises,
            sustentoDisponible = true,
            bandas = metodologia.bandas.map { BandaCatalogo(it.nivel.name, it.desde, it.hasta) },
            grupos = metodologia.grupos.map { GrupoCatalogo(it.codigo, it.nombre, it.peso) },
            vigenciaPaises = grupos.values.mapNotNull { it.vigencia }.maxOrNull()
        )
    }

    private fun opciones(tipo: String): List<ScoringOpcion> =
        opcionRepository.findByTipo(tipo).sortedWith(compareBy<ScoringOpcion>({ it.orden ?: Int.MAX_VALUE }, { it.nombre }))

    // =====================================================================
    // Cliente (consulta a Listas Negativas; no consume tokens)
    // =====================================================================

    @Transactional(readOnly = true)
    fun consultarCliente(documento: String?, tipoDocumento: String?, username: String? = null): ClienteScoringResponse {
        val doc = documento?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ScoringException(HttpStatus.BAD_REQUEST, "SCORING_DOCUMENTO_OBLIGATORIO", "Escribe el documento del cliente.")
        val entidad = buscarEnListas(doc, tipoDocumento)
        val homonimo = homonimoDe(username?.let { usuarioRepository.findByUsuario(it) }, entidad)
        val consulta = clasificar(entidad, homonimo)
        val condicion = ClasificadorListas.condicion(metodologia, consulta)
        return ClienteScoringResponse(
            encontrado = entidad != null,
            entidadId = entidad?.id,
            tipoPersona = entidad?.tipoEntidad,
            tipoDocumento = entidad?.tipoDocumento?.nombre,
            documento = entidad?.documento ?: doc,
            nombre = entidad?.let { nombreDe(it) },
            consulta = consultaResponse(consulta, homonimo),
            condicion = CondicionResponse(condicion.puntaje, condicion.etiqueta),
            reglas = reglasDeListas(condicion)
        )
    }

    /** La entidad de la base de listas con ese documento (y tipo, si se indica), con sus apariciones cargadas. */
    private fun buscarEnListas(documento: String, tipoDocumento: String?): Entidad? {
        val entidad = entidadRepository.buscar(
            documento, tipoDocumento?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }, null, null, null, PageRequest.of(0, 1)
        ).content.firstOrNull() ?: return null
        return conManchas(entidad)
    }

    private fun conManchas(entidad: Entidad): Entidad =
        entidad.id?.let { id -> entidadRepository.cargarManchas(listOf(id)).firstOrNull() } ?: entidad

    /** Si la empresa de quien evalua descarto a esa persona como homonimo en Listas Negativas (la marca vigente). */
    private fun homonimoDe(usuario: Usuario?, entidad: Entidad?): HomonimoVigente? =
        entidad?.id?.let { homonimos?.vigente(usuario, it) }

    // Descartada como homonimo: sus apariciones no cuentan (ni PEP ni sanciones ni noticias), como dice Listas Negativas.
    private fun clasificar(entidad: Entidad?, homonimo: HomonimoVigente? = null): ConsultaListasResultado {
        if (homonimo != null) return ClasificadorListas.clasificar(metodologia, emptyList())
        val apariciones = entidad?.manchas?.map {
            AparicionLista(it.tipoLista.esPep, it.tipoLista.nombre, it.tipoLista.grupo?.orden)
        } ?: emptyList()
        return ClasificadorListas.clasificar(metodologia, apariciones)
    }

    // Parientes, conyuge y empresas vinculadas a un PEP: la base de listas no tiene ese dato (revisado: false).
    private fun consultaResponse(c: ConsultaListasResultado, homonimo: HomonimoVigente? = null) = ConsultaListasResponse(
        fecha = LocalDateTime.now().withNano(0),
        pep = CoincidenciaListas(true, c.pep),
        vinculados = CoincidenciaListas(false, null),
        sanciones = CoincidenciaListas(true, c.sanciones),
        noticias = CoincidenciaListas(true, c.noticias),
        homonimo = homonimo?.let {
            HomonimoListasResponse(
                consultaId = it.consultaId,
                fecha = it.fecha,
                usuario = it.usuario,
                comentario = it.comentario,
                texto = "Descartado como homónimo el ${it.fecha?.format(FORMATO_FECHA_HOMONIMO) ?: "—"} por ${it.usuario}"
            )
        }
    )

    private fun reglasDeListas(condicion: CondicionCliente): List<ReglaResponse> = buildList {
        val detalle = "Fija el nivel Alto como mínimo: corresponde debida diligencia reforzada."
        if (condicion.esPep) add(ReglaResponse(CodigoRegla.PEP, metodologia.tituloRegla(CodigoRegla.PEP), detalle))
        if (condicion.coincidenciasSanciones > 0) {
            add(ReglaResponse(CodigoRegla.SANCIONES, metodologia.tituloRegla(CodigoRegla.SANCIONES), detalle))
        }
    }

    // =====================================================================
    // Vista previa y evaluacion
    // =====================================================================

    @Transactional(readOnly = true)
    fun vistaPrevia(username: String, request: DatosScoringRequest): VistaPreviaResponse {
        val usuario = usuarioRepository.findByUsuario(username) ?: throw ResourceNotFoundException("Usuario no encontrado")
        val c = preparar(usuario, request).calculo
        val porCodigo = c.grupos.associate { it.def.codigo to it.promedio }
        return VistaPreviaResponse(
            grupos = GruposVistaPrevia(porCodigo["CLIENTES"], porCodigo["PRODUCTOS"], porCodigo["ZONA"]),
            puntaje = c.puntaje,
            formula = c.formula,
            montoNota = c.montoNota,
            bloqueos = c.bloqueos.map { BloqueoResponse(it.campo, it.mensaje) }
        )
    }

    @Transactional
    fun evaluar(username: String, request: DatosScoringRequest): ResultadoScoringResponse {
        val usuario = usuarioRepository.findByUsuario(username) ?: throw ResourceNotFoundException("Usuario no encontrado")
        val p = preparar(usuario, request)
        val calculo = p.calculo
        val nivel = calculo.nivel
        val puntaje = calculo.puntaje
        val ocupacion = p.ocupacion
        val departamento = p.departamentoResidencia
        val monto = request.montoOperacion
        if (!calculo.completo || nivel == null || puntaje == null || ocupacion == null || departamento == null || monto == null) {
            throw ScoringException(
                HttpStatus.UNPROCESSABLE_ENTITY, "SCORING_DATOS_INCOMPLETOS",
                "Faltan datos o hay opciones sin puntaje: el sistema no calcula el nivel.",
                calculo.bloqueos.map { BloqueoResponse(it.campo, it.mensaje) }
            )
        }
        val sustentoId = request.sustentoId?.trim()?.takeIf { it.isNotEmpty() }?.also { validarSustento(usuario, it) }

        val fecha = LocalDateTime.now().withNano(0)
        val resultado = construirResultado(p, id = 0, fecha = fecha, sustentoId = sustentoId)

        // El token se pide recien aqui, con todo resuelto: datos incompletos o un sustento ajeno no consumen.
        val entidad = p.entidad
        val claveConsumo = consumirScoring(if (entidad != null) "scoring de la entidad ${entidad.id}" else "scoring de un cliente nuevo")

        // Si el guardado falla despues de consumir, se devuelve el token.
        val guardado = try {
            scoringRiesgoRepository.save(
                ScoringRiesgo(
                    usuario = usuario,
                    entidad = entidad,
                    ocupacion = ocupacion,
                    departamento = departamento,
                    clienteSensibleTipo = if (p.condicion?.esPep == true) "PEP" else "SIN_PEP",
                    volumenTransaccional = listOfNotNull(monto, p.promedioMensual).max(),
                    puntaje = puntaje,
                    sustento = calculo.grupos.flatMap { it.factores }
                        .joinToString("; ") { "${it.def.nombre}: ${it.dato} (${it.puntaje} x ${it.def.peso}%)" },
                    categoria = nivel.etiqueta,
                    fechaCreacion = fecha,
                    version = metodologia.version,
                    nivel = nivel.name,
                    nivelPorPromedio = calculo.nivelPorPromedio?.name,
                    fijadoPorRegla = calculo.fijadoPorRegla,
                    clienteNombre = p.cliente.nombre?.take(300),
                    clienteTipoDocumento = p.cliente.tipoDocumento,
                    clienteDocumento = p.cliente.documento,
                    clienteTipoPersona = p.cliente.tipoPersona,
                    departamentoOperacion = p.departamentoOperacion,
                    producto = p.producto,
                    canal = p.canal,
                    medioPago = p.medioPago,
                    paisFondos = p.pais,
                    montoOperacion = monto,
                    promedioMensual = p.promedioMensual,
                    sustentoId = sustentoId,
                    resultado = json.writeValueAsString(resultado)
                )
            )
        } catch (e: Exception) {
            claveConsumo?.let { licenciaService.anular(it) }
            throw e
        }
        return resultado.copy(id = guardado.id!!)
    }

    /** Todo lo que se junto para una evaluacion. */
    private class Preparacion(
        val calculo: ResultadoCalculo,
        val entidad: Entidad?,
        val cliente: ClienteResultado,
        val condicion: CondicionCliente?,
        val consulta: ConsultaListasResponse?,
        val ocupacion: ScoringOcupacion?,
        val departamentoResidencia: ScoringDepartamento?,
        val departamentoOperacion: ScoringDepartamento?,
        val producto: ScoringOpcion?,
        val canal: ScoringOpcion?,
        val medioPago: ScoringOpcion?,
        val pais: Pais?,
        val promedioMensual: BigDecimal?
    )

    private fun preparar(usuario: Usuario, r: DatosScoringRequest): Preparacion {
        val previos = mutableListOf<Bloqueo>()

        // ---- Cliente: de la base de listas, o uno nuevo con sus datos en la peticion (no se crea en la base de listas)
        val entidadId = r.entidadId
        val entidad: Entidad? = entidadId?.let { id -> entidadRepository.buscarPorIdConDetalle(id)?.let { conManchas(it) } }
        val clienteNuevo: ClienteNuevoRequest? = if (entidadId == null && errorClienteNuevo(r.cliente) == null) r.cliente else null
        when {
            entidadId != null && entidad == null ->
                previos.add(Bloqueo(CampoScoring.CLIENTE, "Ese cliente no está en Listas Negativas: búscalo de nuevo o regístralo como cliente nuevo."))
            entidadId == null && clienteNuevo == null ->
                previos.add(Bloqueo(CampoScoring.CLIENTE, errorClienteNuevo(r.cliente) ?: "Falta el cliente."))
        }
        val documentoNuevo = clienteNuevo?.documento?.trim()
        // Aunque se registre como nuevo, su documento se consulta en las listas: nunca se afirma "sin coincidencias" sin mirar.
        val entidadConsulta = entidad ?: documentoNuevo?.let { buscarEnListas(it, clienteNuevo?.tipoDocumento) }
        val homonimo = homonimoDe(usuario, entidadConsulta)
        val consulta = if (entidad != null || clienteNuevo != null) clasificar(entidadConsulta, homonimo) else null
        val condicion = consulta?.let { ClasificadorListas.condicion(metodologia, it) }

        // ---- Opciones elegidas (un id que no existe bloquea; uno que falta lo informa el calculo)
        val ocupacion = r.ocupacionId?.let { id ->
            val o = ocupacionRepository.findById(id).orElse(null)
            if (o == null) previos.add(Bloqueo(CampoScoring.OCUPACION, "Esa ocupación no existe en el catálogo."))
            o
        }
        val producto = opcion(r.productoId, ScoringOpcion.PRODUCTO, CampoScoring.PRODUCTO, "Ese producto o servicio", previos)
        val canal = opcion(r.canalId, ScoringOpcion.CANAL, CampoScoring.CANAL, "Ese canal", previos)
        val medio = opcion(r.medioPagoId, ScoringOpcion.MEDIO_PAGO, CampoScoring.MEDIO_PAGO, "Ese medio de pago", previos)
        val depRes = departamento(r.departamentoResidenciaId, CampoScoring.DEPARTAMENTO_RESIDENCIA, previos)
        val depOp = departamento(r.departamentoOperacionId, CampoScoring.DEPARTAMENTO_OPERACION, previos)
        val pais = r.paisFondosId?.let { id ->
            val p = paisRepository.findById(id).orElse(null)
            if (p == null) previos.add(Bloqueo(CampoScoring.PAIS, "Ese país no existe en el catálogo."))
            p
        }
        val grupoPais: ScoringPaisGrupo? = pais?.id?.let { paisGrupoRepository.buscarPorPais(it) }

        // ---- Registro de Operaciones (solo si ya hay monto)
        val documentoCliente = entidad?.documento ?: documentoNuevo
        val registro: Pair<BigDecimal?, String?> =
            if (r.montoOperacion != null) promedioRegistro(usuario, documentoCliente) else Pair(null, null)

        val entrada = EntradaCalculo(
            ocupacion = ocupacion?.let { OpcionElegida(it.nombre, it.puntajeV2) },
            condicion = condicion,
            montoOperacion = r.montoOperacion,
            promedioMensual = registro.first,
            notaRegistro = registro.second,
            historial = OpcionElegida("Sin alertas", metodologia.puntajeHistorialSinAlertas),
            producto = producto?.let { OpcionElegida(it.nombre, it.puntaje) },
            canal = canal?.let { OpcionElegida(it.nombre, it.puntaje) },
            medioPago = medio?.let { OpcionElegida(it.nombre, it.puntaje) },
            departamentoResidencia = depRes?.let { OpcionElegida(it.nombre, it.puntajeV2) },
            departamentoOperacion = depOp?.let { OpcionElegida(it.nombre, it.puntajeV2) },
            pais = pais?.let { PaisElegido(it.nombre ?: "", grupoPais?.grupo, grupoPais?.altoRiesgoGafi == true) },
            bloqueosPrevios = previos
        )
        val calculo = CalculoScoring.calcular(metodologia, entrada)

        val cliente = when {
            entidad != null -> ClienteResultado(
                nombre = nombreDe(entidad),
                tipoDocumento = entidad.tipoDocumento.nombre,
                documento = entidad.documento,
                tipoPersona = entidad.tipoEntidad,
                ocupacion = ocupacion?.nombre,
                departamento = depRes?.nombre
            )
            clienteNuevo != null -> ClienteResultado(
                nombre = clienteNuevo.nombre?.trim(),
                tipoDocumento = clienteNuevo.tipoDocumento?.trim()?.uppercase(),
                documento = documentoNuevo,
                tipoPersona = clienteNuevo.tipoPersona?.trim()?.uppercase(),
                ocupacion = ocupacion?.nombre,
                departamento = depRes?.nombre
            )
            else -> ClienteResultado(null, null, null, null, ocupacion?.nombre, depRes?.nombre)
        }

        return Preparacion(
            calculo = calculo,
            entidad = entidad,
            cliente = cliente,
            condicion = condicion,
            consulta = consulta?.let { consultaResponse(it, homonimo) },
            ocupacion = ocupacion,
            departamentoResidencia = depRes,
            departamentoOperacion = depOp,
            producto = producto,
            canal = canal,
            medioPago = medio,
            pais = pais,
            promedioMensual = registro.first
        )
    }

    private fun opcion(id: Int?, tipo: String, campo: String, texto: String, previos: MutableList<Bloqueo>): ScoringOpcion? {
        if (id == null) return null
        val o = opcionRepository.findById(id).orElse(null)?.takeIf { it.tipo == tipo }
        if (o == null) previos.add(Bloqueo(campo, "$texto no existe en el catálogo."))
        return o
    }

    private fun departamento(id: Int?, campo: String, previos: MutableList<Bloqueo>): ScoringDepartamento? {
        if (id == null) return null
        val d = departamentoRepository.findById(id).orElse(null)
        if (d == null) previos.add(Bloqueo(campo, "Ese departamento no existe en el catálogo."))
        return d
    }

    private fun errorClienteNuevo(c: ClienteNuevoRequest?): String? {
        if (c == null) return "Falta el cliente: búscalo por su documento o registra sus datos."
        val faltan = buildList {
            if (c.tipoPersona.isNullOrBlank()) add("tipo de persona")
            if (c.tipoDocumento.isNullOrBlank()) add("tipo de documento")
            if (c.documento.isNullOrBlank()) add("documento")
            if (c.nombre.isNullOrBlank()) add("nombre")
        }
        if (faltan.isNotEmpty()) return "Faltan datos del cliente nuevo: ${CalculoScoring.enumerar(faltan)}."
        if (c.tipoPersona?.trim()?.uppercase() !in TIPOS_PERSONA) return "El tipo de persona debe ser NATURAL o JURIDICA."
        if (c.tipoDocumento?.trim()?.uppercase() !in TIPOS_DOCUMENTO) return "El tipo de documento debe ser DNI, CE, RUC o PASAPORTE."
        return null
    }

    /**
     * Promedio mensual del cliente en el Registro de Operaciones de la empresa de quien evalua (nunca de otra),
     * en los ultimos 12 meses y solo con las operaciones en soles (no hay tipo de cambio para las demas).
     */
    private fun promedioRegistro(usuario: Usuario, documento: String?): Pair<BigDecimal?, String?> {
        val soloMonto = "Solo se usa el monto de la operación:"
        val empresa = limpiarEmpresa(usuario.empresa)
            ?: return Pair(null, "$soloMonto tu cuenta no tiene empresa asignada, así que no se puede leer su Registro de Operaciones.")
        if (documento.isNullOrBlank()) {
            return Pair(null, "$soloMonto falta el documento del cliente para ubicarlo en el Registro de Operaciones.")
        }
        val meses = metodologia.mesesPromedioOperaciones
        val hoy = LocalDate.now()
        val totales = try {
            volumenOperaciones.totalesPorMoneda(empresa, documento, hoy.minusMonths(meses.toLong()), hoy)
        } catch (e: Exception) {
            log.warn("Scoring: no se pudo leer el Registro de Operaciones: {}", e.message)
            return Pair(null, "$soloMonto no se pudo leer el Registro de Operaciones.")
        }
        val (soles, otras) = totales.partition { esSoles(it.moneda) }
        val enSoles = soles.sumOf { it.cantidad }
        val enOtras = otras.sumOf { it.cantidad }
        if (enSoles == 0L) {
            val motivo = if (enOtras > 0) {
                "$soloMonto en el Registro de Operaciones de tu empresa el cliente solo tiene operaciones en otra moneda ($enOtras) y no hay tipo de cambio para sumarlas en soles."
            } else {
                "$soloMonto el cliente no tiene operaciones en el Registro de Operaciones de tu empresa en los últimos $meses meses."
            }
            return Pair(null, motivo)
        }
        var total = BigDecimal.ZERO
        soles.forEach { total = total.add(it.total) }
        val promedio = total.divide(BigDecimal(meses), 2, RoundingMode.HALF_UP)
        val nota = buildString {
            append("Promedio mensual de los últimos $meses meses en el Registro de Operaciones: S/ ${CalculoScoring.soles(promedio)}")
            append(if (enSoles == 1L) " (1 operación en soles)." else " ($enSoles operaciones en soles).")
            if (enOtras > 0) append(" No se sumaron $enOtras en otra moneda (no hay tipo de cambio).")
        }
        return Pair(promedio, nota)
    }

    private fun esSoles(moneda: String?): Boolean {
        if (moneda == null) return false
        val m = CalculoScoring.normalizar(moneda)
        return m.startsWith("PEN") || m.contains("SOL") || m.startsWith("S/")
    }

    private fun validarSustento(usuario: Usuario, id: String) {
        val sustento = sustentoRepository.buscarConUsuario(id)
        if (sustento == null || !visibilidad.de(usuario).puedeVer(sustento.usuario)) {
            throw ScoringException(HttpStatus.BAD_REQUEST, "SCORING_SUSTENTO_INVALIDO", "No se encontró el sustento indicado: vuelve a subirlo.")
        }
    }

    private fun construirResultado(p: Preparacion, id: Int, fecha: LocalDateTime, sustentoId: String?): ResultadoScoringResponse {
        val c = p.calculo
        val nivel = c.nivel
        return ResultadoScoringResponse(
            id = id,
            version = metodologia.version,
            fecha = fecha,
            cliente = p.cliente,
            puntaje = c.puntaje,
            nivel = nivel?.name,
            nivelPorPromedio = c.nivelPorPromedio?.name,
            fijadoPorRegla = c.fijadoPorRegla,
            avisoRegla = c.avisoRegla?.let { AvisoReglaResponse(it.titulo, it.detalle) },
            grupos = c.grupos.map { g ->
                GrupoResultado(
                    codigo = g.def.codigo,
                    nombre = g.def.nombre,
                    peso = g.def.peso,
                    promedio = g.promedio,
                    factores = g.factores.map { f ->
                        FactorResultado(f.def.codigo.name, f.def.nombre, f.dato, f.puntaje, f.def.peso, f.aporte, f.def.automatico)
                    }
                )
            },
            reglas = c.reglas.map { ReglaResponse(it.codigo, it.titulo, it.detalle) },
            queHacer = nivel?.let { queHacer(it, fecha.toLocalDate()) },
            decision = null,
            entidadId = p.entidad?.id,
            sustentoId = sustentoId,
            montoNota = c.montoNota,
            consulta = p.consulta
        )
    }

    private fun queHacer(nivel: Nivel, fecha: LocalDate): QueHacerResponse {
        val a = metodologia.acciones.getValue(nivel)
        return QueHacerResponse(a.debidaDiligencia, a.decide, a.revisionMeses, fecha.plusMonths(a.revisionMeses.toLong()), a.reunir)
    }

    /**
     * Pide 1 token a Sulotec para la evaluacion y devuelve la clave del consumo (null si no se consumio: licencias
     * apagadas, sin sesion o una cuenta sin empresa con UUID). Cada evaluacion es una accion nueva.
     */
    private fun consumirScoring(detalle: String): String? {
        if (!licenciaService.activo) return null
        val contexto = licenciaContexto.actual() ?: return null
        val uuid = contexto.empresaUuid ?: return null
        val clave = LicenciaReglas.claveAleatoria(uuid, TIPO_SCORING)
        licenciaService.consumirComo(contexto, TIPO_SCORING, clave, detalle)
        return clave
    }

    // =====================================================================
    // Volver a abrir, decidir e historial (misma visibilidad por empresa)
    // =====================================================================

    @Transactional(readOnly = true)
    fun obtener(username: String, id: Int): ResultadoScoringResponse = resultadoDe(visible(username, id))

    /** La evaluacion si quien pide la puede ver; si no, el mismo 404 que si no existiera. */
    private fun visible(username: String, id: Int): ScoringRiesgo {
        val vista = visibilidad.de(username)
        val sr = scoringRiesgoRepository.buscarPorIdConDetalle(id)
        if (sr == null || !vista.puedeVer(sr.usuario)) throw ResourceNotFoundException("Evaluación no encontrada")
        return sr
    }

    private fun resultadoDe(sr: ScoringRiesgo): ResultadoScoringResponse {
        val decision = decisionDe(sr)
        val guardado = if (sr.versionMetodologia() >= 2) leerResultado(sr) else null
        if (guardado != null) return guardado.copy(id = sr.id!!, decision = decision)

        // Version 1 (o una fila sin resultado guardado): lo que habia entonces, sin nivel nuevo.
        val entidad = sr.entidad
        return ResultadoScoringResponse(
            id = sr.id!!,
            version = sr.versionMetodologia(),
            fecha = sr.fechaCreacion,
            cliente = ClienteResultado(
                nombre = sr.clienteNombre ?: entidad?.let { nombreDe(it) },
                tipoDocumento = sr.clienteTipoDocumento ?: entidad?.tipoDocumento?.nombre,
                documento = sr.clienteDocumento ?: entidad?.documento,
                tipoPersona = sr.clienteTipoPersona ?: entidad?.tipoEntidad,
                ocupacion = sr.ocupacion.nombre,
                departamento = sr.departamento.nombre
            ),
            puntaje = sr.puntaje,
            nivel = sr.nivel,
            nivelPorPromedio = sr.nivelPorPromedio,
            fijadoPorRegla = sr.fijadoPorRegla == true,
            avisoRegla = null,
            grupos = emptyList(),
            reglas = emptyList(),
            queHacer = null,
            decision = decision,
            categoria = sr.categoria,
            entidadId = entidad?.id
        )
    }

    private fun leerResultado(sr: ScoringRiesgo): ResultadoScoringResponse? {
        val texto = sr.resultado ?: return null
        return try {
            json.readValue<ResultadoScoringResponse>(texto)
        } catch (e: Exception) {
            log.warn("Scoring: no se pudo leer el resultado guardado de la evaluacion {}: {}", sr.id, e.message)
            null
        }
    }

    private fun decisionDe(sr: ScoringRiesgo): DecisionResponse? =
        sr.decisionAccion?.let { DecisionResponse(it, sr.decisionComentario, sr.decisionPor, sr.decisionFecha) }

    /**
     * Decision del analista. Con nivel Bajo o Medio decide el analista (APROBAR o RECHAZAR); con nivel Alto decide
     * la gerencia mas alta o un comite, asi que el analista solo puede ENVIAR_A_APROBACION o RECHAZAR, y con
     * comentario obligatorio. Una evaluacion ya decidida no se vuelve a decidir.
     */
    @Transactional
    fun decidir(username: String, id: Int, request: DecisionRequest): ResultadoScoringResponse {
        val sr = visible(username, id)
        if (sr.versionMetodologia() < 2) {
            throw ScoringException(HttpStatus.CONFLICT, "SCORING_VERSION_ANTIGUA", "Las evaluaciones de la metodología anterior no admiten decisión.")
        }
        val accion = request.accion?.trim()?.uppercase()
        if (accion == null || accion !in ACCIONES) {
            throw ScoringException(HttpStatus.BAD_REQUEST, "SCORING_ACCION_INVALIDA", "La acción debe ser APROBAR, ENVIAR_A_APROBACION o RECHAZAR.")
        }
        if (sr.decisionAccion != null) {
            throw ScoringException(HttpStatus.CONFLICT, "SCORING_YA_DECIDIDO", "Esta evaluación ya tiene una decisión.")
        }
        val alto = sr.nivel == Nivel.ALTO.name
        if (accion == ACCION_APROBAR && alto) {
            throw ScoringException(
                HttpStatus.CONFLICT, "SCORING_REQUIERE_APROBACION",
                "Con riesgo Alto la aceptación la decide la gerencia más alta o un comité: envíala a aprobación."
            )
        }
        if (accion == ACCION_ENVIAR && !alto) {
            throw ScoringException(
                HttpStatus.CONFLICT, "SCORING_NO_REQUIERE_APROBACION",
                "Con riesgo Bajo o Medio decide el analista: apruébala o recházala."
            )
        }
        val comentario = request.comentario?.trim()?.takeIf { it.isNotEmpty() }?.take(4000)
        if (alto && comentario == null) {
            throw ScoringException(HttpStatus.BAD_REQUEST, "SCORING_COMENTARIO_OBLIGATORIO", "Con riesgo Alto el comentario del analista es obligatorio.")
        }
        val base = resultadoDe(sr)
        val fecha = LocalDateTime.now().withNano(0)
        if (scoringRiesgoRepository.registrarDecision(id, accion, comentario, username, fecha) == 0) {
            throw ScoringException(HttpStatus.CONFLICT, "SCORING_YA_DECIDIDO", "Esta evaluación ya tiene una decisión.")
        }
        return base.copy(decision = DecisionResponse(accion, comentario, username, fecha))
    }

    @Transactional(readOnly = true)
    fun obtenerHistorial(username: String, pageable: Pageable): Page<HistorialScoringResponse> {

        // Cada cuenta ve sus evaluaciones; el administrador de empresa, las de su empresa; el universal, todas.
        val vista = visibilidad.de(username)
        val pagina = when (vista.alcance) {
            Alcance.TODO -> scoringRiesgoRepository.buscarTodas(pageable)
            Alcance.EMPRESA -> scoringRiesgoRepository.buscarPorEmpresa(requireNotNull(vista.empresa), pageable)
            Alcance.PROPIO -> scoringRiesgoRepository.buscarPorUsuario(vista.usuario.id!!, pageable)
        }
        return pagina.map { historialDe(it) }
    }

    private fun historialDe(sr: ScoringRiesgo): HistorialScoringResponse {
        val entidad = sr.entidad
        val version = sr.versionMetodologia()
        val factores = if (version >= 2) {
            leerResultado(sr)?.grupos?.flatMap { it.factores }?.map {
                FactorHistorial(it.nombre, it.dato, it.puntaje, it.peso, it.aporte)
            } ?: emptyList()
        } else {
            // Como se mostraban en la version 1 (sin el puntaje de cliente sensible ni de volumen).
            listOf(
                FactorHistorial("Ocupación / Profesión", sr.ocupacion.nombre, sr.ocupacion.puntaje, MetodologiaV1.PESO_OCUPACION, BigDecimal.ZERO),
                FactorHistorial("Cliente Sensible", sr.clienteSensibleTipo, 0, MetodologiaV1.PESO_CLIENTE_SENSIBLE, BigDecimal.ZERO),
                FactorHistorial("Zona Geográfica (Departamento)", sr.departamento.nombre, sr.departamento.puntaje, MetodologiaV1.PESO_DEPARTAMENTO, BigDecimal.ZERO),
                FactorHistorial("Volumen Transaccional Estimado", sr.volumenTransaccional.toPlainString(), 0, MetodologiaV1.PESO_VOLUMEN, BigDecimal.ZERO)
            )
        }
        return HistorialScoringResponse(
            id = sr.id!!,
            version = version,
            entidadId = entidad?.id,
            nombreCompleto = sr.clienteNombre ?: entidad?.let { nombreDe(it) } ?: "",
            documento = sr.clienteDocumento ?: entidad?.documento,
            factores = factores,
            puntajeTotal = sr.puntaje,
            categoria = sr.categoria,
            nivel = if (version >= 2) sr.nivel else null,
            decision = decisionDe(sr),
            fechaCreacion = sr.fechaCreacion
        )
    }

    private fun nombreDe(entidad: Entidad): String = when {
        entidad.personaNatural != null -> listOfNotNull(
            entidad.personaNatural?.nombre,
            entidad.personaNatural?.segundoNombre,
            entidad.personaNatural?.apePat,
            entidad.personaNatural?.apeMat
        ).filter { it.isNotBlank() }.joinToString(" ")
        entidad.personaJuridica != null -> entidad.personaJuridica?.razonSocial ?: ""
        else -> ""
    }

    private fun esPeru(nombre: String?): Boolean = nombre != null && CalculoScoring.normalizar(nombre) == "PERU"

    companion object {
        const val ACCION_APROBAR = "APROBAR"
        const val ACCION_ENVIAR = "ENVIAR_A_APROBACION"
        const val ACCION_RECHAZAR = "RECHAZAR"
        private val ACCIONES = setOf(ACCION_APROBAR, ACCION_ENVIAR, ACCION_RECHAZAR)
        private val TIPOS_PERSONA = setOf("NATURAL", "JURIDICA")
        private val TIPOS_DOCUMENTO = setOf("DNI", "CE", "RUC", "PASAPORTE")
        private val FORMATO_FECHA_HOMONIMO: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
