package com.security.modules.oficial.aplicacion

import com.security.modules.oficial.dominio.AmbitoAdjunto
import com.security.modules.oficial.dominio.Dedicacion
import com.security.modules.oficial.dominio.DocumentoDesignacion
import com.security.modules.oficial.dominio.EstadoDesignacion
import com.security.modules.oficial.dominio.EstadoEmpresaOc
import com.security.modules.oficial.dominio.MotivoRemocion
import com.security.modules.oficial.dominio.QuienDesigna
import com.security.modules.oficial.dominio.ResultadoUif
import com.security.modules.oficial.dominio.TextosOc
import com.security.modules.oficial.dominio.TipoCargo
import com.security.modules.oficial.dominio.TipoDocumentoOc
import com.security.modules.oficial.dominio.TipoEmpresaOc
import com.security.modules.oficial.dominio.TipoEvento
import com.security.modules.oficial.dominio.TipoPendiente
import com.security.modules.oficial.dominio.Vinculo
import com.security.modules.oficial.dominio.enumDe
import com.security.modules.oficial.persistencia.OcDesignacion
import com.security.modules.oficial.persistencia.OcDesignacionRepository
import com.security.modules.oficial.persistencia.OcEvento
import com.security.modules.oficial.persistencia.OcEventoCambio
import com.security.modules.oficial.persistencia.OcEventoCambioRepository
import com.security.modules.oficial.persistencia.OcEventoRepository
import com.security.modules.oficial.persistencia.OcPendiente
import com.security.modules.oficial.persistencia.OcPendienteRepository
import com.security.modules.oficial.persistencia.OcRemocion
import com.security.modules.oficial.persistencia.OcRemocionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Anota el historial. Solo inserta: no hay forma de cambiar ni borrar un evento. */
@Service
class HistorialOficial(
    private val eventos: OcEventoRepository,
    private val cambios: OcEventoCambioRepository,
    private val reloj: RelojOficial
) {
    fun anotar(
        ctx: ContextoOc,
        designacion: OcDesignacion,
        tipo: TipoEvento,
        titulo: String,
        detalle: String? = null,
        motivo: String? = null,
        etiqueta: String? = null,
        pendienteId: Long? = null,
        lista: List<CambioCampo> = emptyList()
    ): OcEvento {
        val evento = eventos.save(
            OcEvento(
                uuid = UUID.randomUUID().toString(),
                empresaId = designacion.empresaId,
                designacionId = requireNotNull(designacion.id),
                pendienteId = pendienteId,
                tipo = tipo.name,
                titulo = titulo.take(255),
                detalle = detalle?.take(2000),
                motivo = motivo?.take(2000),
                etiqueta = etiqueta,
                usuarioId = ctx.actor.id,
                usuario = ctx.actor.usuario,
                usuarioNombre = ctx.actor.nombreCompleto,
                fechaHora = reloj.ahora()
            )
        )
        lista.forEachIndexed { i, c ->
            cambios.save(
                OcEventoCambio(
                    eventoId = requireNotNull(evento.id), orden = i, campo = c.campo, etiqueta = c.etiqueta,
                    antes = c.antes?.take(500), despues = c.despues?.take(500)
                )
            )
        }
        return evento
    }
}

/** Arma las respuestas (ficha, estado, historial, pendientes) con los plazos ya resueltos. */
@Service
class FichaOficialService(
    private val designaciones: OcDesignacionRepository,
    private val remociones: OcRemocionRepository,
    private val pendientes: OcPendienteRepository,
    private val eventos: OcEventoRepository,
    private val cambios: OcEventoCambioRepository,
    private val adjuntos: AdjuntosOficialService,
    private val catalogos: CatalogoOficialService,
    private val reglas: ReglasOficialService,
    private val cifrado: CifradoCodigosOficial
) {

    fun buscar(ctx: ContextoOc, id: String): OcDesignacion =
        designaciones.findByUuidAndEmpresaId(id.trim(), ctx.empresaId) ?: throw Oc.noEncontrado("No existe esa designación.")

    fun titularActivo(empresaId: Int): OcDesignacion? =
        designaciones.findFirstByEmpresaIdAndTipoCargoAndEstadoNotOrderByGuardadaEnDescIdDesc(
            empresaId, TipoCargo.TITULAR.name, EstadoDesignacion.REMOVIDA.name
        )

    fun ultimoTitularRemovido(empresaId: Int): OcDesignacion? =
        designaciones.findFirstByEmpresaIdAndTipoCargoAndEstadoOrderByRemovidaEnDescIdDesc(
            empresaId, TipoCargo.TITULAR.name, EstadoDesignacion.REMOVIDA.name
        )

    // ============================================================
    // ESTADO (pantalla raiz)
    // ============================================================

    @Transactional(readOnly = true)
    fun estado(ctx: ContextoOc): EstadoResponse {
        val motor = reglas.motor()
        val activo = titularActivo(ctx.empresaId)
        val removido = if (activo == null) ultimoTitularRemovido(ctx.empresaId) else null
        val estado = when {
            activo != null -> when (estadoDe(activo)) {
                EstadoDesignacion.PENDIENTE_COMUNICAR -> EstadoEmpresaOc.PENDIENTE_COMUNICAR
                EstadoDesignacion.EN_EVALUACION -> EstadoEmpresaOc.EN_EVALUACION
                else -> EstadoEmpresaOc.VIGENTE
            }
            removido != null -> EstadoEmpresaOc.VACANTE
            else -> EstadoEmpresaOc.SIN_OFICIAL
        }
        val ultima = designaciones.findFirstByEmpresaIdOrderByGuardadaEnDescIdDesc(ctx.empresaId)
        return EstadoResponse(
            empresa = EmpresaResponse(ctx.empresa.uuid, ctx.empresa.nombre),
            estado = estado.name,
            estadoEtiqueta = estado.etiqueta,
            tipoEmpresa = ultima?.tipoEmpresa,
            titular = activo?.let { resumen(it) },
            plazoComunicacion = activo?.let { plazoComunicacion(it, motor) },
            observacionUif = activo?.let { observacion(it) },
            vacancia = removido?.let { vacancia(ctx, it, motor) },
            pendientes = pendientes.findByEmpresaIdAndComunicadoFalseOrderByCreadoEnDescIdDesc(ctx.empresaId)
                .map { pendiente(ctx, it, motor) },
            permisos = permisos(ctx, activo)
        )
    }

    private fun vacancia(ctx: ContextoOc, d: OcDesignacion, motor: ReglasOficialService.Motor): VacanciaResponse {
        val remocion = remociones.findFirstByDesignacionIdAndEmpresaIdOrderByIdDesc(d.id!!, ctx.empresaId)
        val pendiente = remocion?.let { pendientes.findFirstByRemocionIdAndEmpresaId(it.id!!, ctx.empresaId) }
        val motivo = enumDe<MotivoRemocion>(remocion?.motivo)
        return VacanciaResponse(
            designacionRemovidaId = d.uuid,
            nombreCompleto = nombreCompleto(d),
            motivo = motivo?.name,
            motivoNombre = motivo?.nombre,
            fechaDecision = remocion?.fechaDecision,
            registradaEn = remocion?.registradaEn,
            aprobadaPor = remocion?.aprobadaPor,
            plazoDesignar = remocion?.let { motor.plazo(ReglasPorDefecto.VACANCIA_MAXIMA, it.fechaDecision) },
            pendienteRemocionId = pendiente?.uuid,
            plazoComunicarRemocion = pendiente?.takeIf { !it.comunicado }?.let { motor.plazo(it.regla, it.desde) },
            remocionComunicada = pendiente?.comunicado ?: false
        )
    }

    // ============================================================
    // FICHA
    // ============================================================

    @Transactional(readOnly = true)
    fun ficha(ctx: ContextoOc, d: OcDesignacion): FichaResponse {
        val motor = reglas.motor()
        val estado = estadoDe(d)
        val vinculo = enumDe<Vinculo>(d.vinculo)
        val puedeVerDj = ctx.actor.puedeEscribir
        val remocion = if (estado == EstadoDesignacion.REMOVIDA) {
            remociones.findFirstByDesignacionIdAndEmpresaIdOrderByIdDesc(d.id!!, ctx.empresaId)
        } else null
        val activo = if (estado.activa) d else titularActivo(ctx.empresaId)
        return FichaResponse(
            id = d.uuid,
            estado = estado.name,
            estadoEtiqueta = estado.etiqueta,
            empresa = EmpresaResponse(ctx.empresa.uuid, ctx.empresa.nombre),
            tipoEmpresa = d.tipoEmpresa,
            tipoEmpresaNombre = nombre<TipoEmpresaOc>(d.tipoEmpresa),
            tipoCargo = d.tipoCargo,
            tipoCargoNombre = nombre<TipoCargo>(d.tipoCargo),
            dedicacion = d.dedicacion,
            dedicacionNombre = nombre<Dedicacion>(d.dedicacion),
            nombreCompleto = nombreCompleto(d),
            iniciales = iniciales(d),
            etiquetas = etiquetas(d, estado),
            persona = PersonaResponse(
                nombres = d.nombres, apellidos = d.apellidos,
                nacionalidad = d.nacionalidad, nacionalidadNombre = catalogos.nombrePais(d.nacionalidad),
                tipoDocumento = d.tipoDocumento, tipoDocumentoNombre = nombre<TipoDocumentoOc>(d.tipoDocumento),
                numeroDocumento = d.numeroDocumento, documento = documento(d),
                fechaNacimiento = d.fechaNacimiento, domicilio = d.domicilio, telefono = d.telefono, correo = d.correo
            ),
            cargo = CargoResponse(d.cargo, d.fechaIngreso, d.vinculo, nombre<Vinculo>(d.vinculo), d.direccionOficina),
            designacion = DesignacionDatosResponse(
                designa = d.designa,
                designaNombre = nombre<QuienDesigna>(d.designa),
                nombreOrgano = d.nombreOrgano,
                documentoTipo = d.documentoTipo,
                documentoTipoNombre = nombre<DocumentoDesignacion>(d.documentoTipo),
                documentoFecha = d.documentoFecha,
                documentoTexto = documentoTexto(d),
                fechaDesignacion = d.fechaDesignacion,
                esOficialOtraEmpresa = d.esOficialOtraEmpresa,
                reemplazaAId = d.reemplazaAId?.let { designaciones.findByIdAndEmpresaId(it, ctx.empresaId)?.uuid },
                adjunto = adjunto(d.actaAdjuntoId, ctx),
                hojaVida = adjunto(d.hojaVidaAdjuntoId, ctx),
                documentoVinculo = adjunto(d.documentoVinculoAdjuntoId, ctx)
            ),
            declaraciones = DeclaracionesResponse(
                requisitos = DjRequisitosResponse(
                    completa = puntos(d).size == TextosOc.LETRAS.size,
                    puntosAceptados = puntos(d).size,
                    puntosTotal = TextosOc.LETRAS.size,
                    firmaNombre = d.djReqFirmaNombre,
                    fechaFirma = d.djReqFechaFirma,
                    adjunto = if (puedeVerDj) adjunto(d.djReqAdjuntoId, ctx) else null,
                    vigente = estado.activa
                ),
                designante = DjDesignanteResponse(
                    completa = d.djDesAceptada,
                    firmaNombre = d.djDesFirmaNombre,
                    fechaFirma = d.djDesFechaFirma,
                    adjunto = if (puedeVerDj) adjunto(d.djDesAdjuntoId, ctx) else null,
                    vigente = estado.activa
                )
            ),
            comunicacion = comunicacion(d, ctx),
            plazoComunicacion = plazoComunicacion(d, motor),
            observacionUif = observacion(d),
            enElCargoDesde = if (estado.activa) d.fechaDesignacion else null,
            revisionSugerida = motor.vencimiento(ReglasPorDefecto.REVISION_SUGERIDA, d.fechaDesignacion),
            requisitosCargo = TextosOc.requisitosCargo(vinculo),
            remocion = remocion?.let { remocion(it, ctx) },
            pendientes = pendientes.findByDesignacionIdAndEmpresaIdOrderByCreadoEnDescIdDesc(d.id!!, ctx.empresaId)
                .map { pendiente(ctx, it, motor) },
            alterno = null,
            guardadaEn = d.guardadaEn,
            guardadaPor = UsuarioOc(d.guardadaPorUsuario, d.guardadaPorNombre),
            permisos = permisos(ctx, activo, d)
        )
    }

    fun resumen(d: OcDesignacion): ResumenDesignacion {
        val estado = estadoDe(d)
        return ResumenDesignacion(
            id = d.uuid,
            estado = estado.name,
            estadoEtiqueta = estado.etiqueta,
            nombreCompleto = nombreCompleto(d),
            iniciales = iniciales(d),
            tipoCargo = d.tipoCargo,
            tipoCargoNombre = nombre<TipoCargo>(d.tipoCargo),
            dedicacion = d.dedicacion,
            dedicacionNombre = nombre<Dedicacion>(d.dedicacion),
            documento = documento(d),
            fechaDesignacion = d.fechaDesignacion,
            guardadaEn = d.guardadaEn,
            designaNombre = nombre<QuienDesigna>(d.designa),
            nombreOrgano = d.nombreOrgano,
            resultadoUif = d.comResultado,
            fechaNotificacion = d.comFechaNotificacion
        )
    }

    private fun comunicacion(d: OcDesignacion, ctx: ContextoOc): ComunicacionResponse? {
        if (d.comResultado == null && d.codigoEmpresaCifrado == null && d.codigoOficialCifrado == null) return null
        return ComunicacionResponse(
            fechaComunicacion = d.comFecha,
            numeroSolicitud = d.comNumeroSolicitud,
            resultado = d.comResultado,
            resultadoNombre = d.comResultado?.let { nombre<ResultadoUif>(it) },
            fechaNotificacion = d.comFechaNotificacion,
            motivoNoProcedente = d.comMotivoNoProcedente,
            constancia = adjunto(d.comConstanciaAdjuntoId, ctx),
            registradaEn = d.comRegistradaEn,
            codigos = CodigosResponse(
                reservados = d.codigoEmpresaCifrado != null || d.codigoOficialCifrado != null,
                registradosEn = d.codigosRegistradosEn,
                cifradoDisponible = cifrado.disponible
            )
        )
    }

    /** Plazo de 15 dias habiles: solo mientras esta pendiente y la UIF no la observo. */
    fun plazoComunicacion(d: OcDesignacion, motor: ReglasOficialService.Motor) =
        if (estadoDe(d) == EstadoDesignacion.PENDIENTE_COMUNICAR && d.comResultado != ResultadoUif.NO_PROCEDENTE.name) {
            motor.plazo(ReglasPorDefecto.PLAZO_COMUNICAR_DESIGNACION, d.fechaDesignacion)
        } else null

    private fun observacion(d: OcDesignacion) =
        if (estadoDe(d) == EstadoDesignacion.PENDIENTE_COMUNICAR && d.comResultado == ResultadoUif.NO_PROCEDENTE.name) {
            ObservacionUif(d.comMotivoNoProcedente, d.comFechaNotificacion)
        } else null

    fun remocion(r: OcRemocion, ctx: ContextoOc): RemocionResponse {
        val motivo = enumDe<MotivoRemocion>(r.motivo)
        return RemocionResponse(
            id = r.uuid,
            motivo = r.motivo,
            motivoNombre = motivo?.nombre ?: r.motivo,
            razones = r.razones,
            aprobadaPor = r.aprobadaPor,
            fechaDecision = r.fechaDecision,
            acta = adjunto(r.actaAdjuntoId, ctx),
            registradaEn = r.registradaEn,
            registradaPor = UsuarioOc(r.registradaPorUsuario, r.registradaPorNombre)
        )
    }

    fun pendiente(ctx: ContextoOc, p: OcPendiente, motor: ReglasOficialService.Motor): PendienteResponse =
        PendienteResponse(
            id = p.uuid,
            tipo = p.tipo,
            designacionId = designaciones.findByIdAndEmpresaId(p.designacionId, ctx.empresaId)?.uuid,
            titulo = p.titulo,
            detalle = p.detalle,
            plazo = if (p.comunicado) null else motor.plazo(p.regla, p.desde),
            comunicado = p.comunicado,
            comunicacion = if (!p.comunicado) null else ComunicacionPendienteResponse(
                fecha = p.comFecha,
                numeroSolicitud = p.comNumeroSolicitud,
                nota = p.comNota,
                constancia = adjunto(p.comConstanciaAdjuntoId, ctx),
                registradaEn = p.comRegistradaEn,
                registradaPor = UsuarioOc(p.comRegistradaPorUsuario, p.comRegistradaPorNombre)
            ),
            creadoEn = p.creadoEn,
            creadoPor = UsuarioOc(p.creadoPorUsuario, p.creadoPorNombre)
        )

    @Transactional(readOnly = true)
    fun pendientes(ctx: ContextoOc, todos: Boolean): List<PendienteResponse> {
        val motor = reglas.motor()
        val lista = if (todos) pendientes.findByEmpresaIdOrderByCreadoEnDescIdDesc(ctx.empresaId)
        else pendientes.findByEmpresaIdAndComunicadoFalseOrderByCreadoEnDescIdDesc(ctx.empresaId)
        return lista.map { pendiente(ctx, it, motor) }
    }

    // ============================================================
    // HISTORIAL
    // ============================================================

    @Transactional(readOnly = true)
    fun historial(ctx: ContextoOc, d: OcDesignacion): HistorialResponse {
        val motor = reglas.motor()
        val lista = eventos.findByDesignacionIdAndEmpresaIdOrderByFechaHoraDescIdDesc(d.id!!, ctx.empresaId)
        val ids = lista.mapNotNull { it.id }
        val cambiosPorEvento = if (ids.isEmpty()) emptyMap()
        else cambios.findByEventoIdInOrderByEventoIdAscOrdenAsc(ids).groupBy { it.eventoId }
        val pendientesDe = pendientes.findByDesignacionIdAndEmpresaIdOrderByCreadoEnDescIdDesc(d.id!!, ctx.empresaId)
        val porId = pendientesDe.associateBy { it.id }
        val estado = estadoDe(d)
        return HistorialResponse(
            designacionId = d.uuid,
            nombreCompleto = nombreCompleto(d),
            eventos = lista.map { e ->
                EventoResponse(
                    id = e.uuid,
                    tipo = e.tipo,
                    titulo = e.titulo,
                    etiqueta = etiquetaEvento(e, e.pendienteId?.let { porId[it] }),
                    detalle = e.detalle,
                    motivo = e.motivo,
                    cambios = cambiosPorEvento[e.id].orEmpty().map { CambioCampo(it.campo, it.etiqueta, it.antes, it.despues) },
                    usuario = UsuarioOc(e.usuario, e.usuarioNombre),
                    fechaHora = e.fechaHora
                )
            },
            pendientesUif = pendientesDe.filter { !it.comunicado }.map { pendiente(ctx, it, motor) },
            vigencia = VigenciaResponse(
                estado = estado.name,
                estadoEtiqueta = estado.etiqueta,
                enElCargoDesde = if (estado.activa) d.fechaDesignacion else null,
                revisionSugerida = motor.vencimiento(ReglasPorDefecto.REVISION_SUGERIDA, d.fechaDesignacion)
            )
        )
    }

    private fun etiquetaEvento(e: OcEvento, p: OcPendiente?): EtiquetaOc? = when {
        p != null && (e.tipo == TipoEvento.CAMBIO_DATOS.name || e.tipo == TipoEvento.REMOCION.name) ->
            if (p.comunicado) EtiquetaOc("COMUNICADO", "Comunicado", "verde")
            else EtiquetaOc("PENDIENTE_COMUNICAR", "Pendiente de comunicar", "ambar")
        e.etiqueta == "CERRADO" -> EtiquetaOc("CERRADO", "Cerrado", "gris")
        else -> null
    }

    // ============================================================
    // AYUDAS
    // ============================================================

    fun permisos(ctx: ContextoOc, activo: OcDesignacion?, d: OcDesignacion? = activo): PermisosOc {
        val escribe = ctx.actor.puedeEscribir
        val estado = d?.let { estadoDe(it) }
        return PermisosOc(
            puedeDesignar = escribe && activo == null,
            puedeCorregir = escribe && estado == EstadoDesignacion.PENDIENTE_COMUNICAR,
            puedeComunicar = escribe && estado != null && estado.activa,
            puedeCambiarDatos = escribe && (estado == EstadoDesignacion.VIGENTE || estado == EstadoDesignacion.EN_EVALUACION),
            puedeRemover = escribe && (estado == EstadoDesignacion.VIGENTE || estado == EstadoDesignacion.EN_EVALUACION),
            puedeRegistrarPendientes = escribe
        )
    }

    private fun etiquetas(d: OcDesignacion, estado: EstadoDesignacion): List<EtiquetaOc> {
        val lista = mutableListOf(EtiquetaOc(estado.name, estado.etiqueta, estado.tono))
        if (estado == EstadoDesignacion.VIGENTE && (d.codigoEmpresaCifrado != null || d.codigoOficialCifrado != null)) {
            lista += EtiquetaOc("CODIGOS_ACTIVOS", "Códigos secretos activos", "lila")
        }
        lista += EtiquetaOc(d.tipoCargo, nombre<TipoCargo>(d.tipoCargo), "gris")
        lista += EtiquetaOc("DEDICACION", "Dedicación " + nombre<Dedicacion>(d.dedicacion).lowercase(), "gris")
        return lista
    }

    private fun adjunto(id: Long?, ctx: ContextoOc) = adjuntos.respuesta(adjuntos.porId(id, ctx.empresaId))

    companion object {
        fun estadoDe(d: OcDesignacion): EstadoDesignacion =
            enumDe<EstadoDesignacion>(d.estado) ?: EstadoDesignacion.PENDIENTE_COMUNICAR

        fun nombreCompleto(d: OcDesignacion) = "${d.nombres} ${d.apellidos}".trim()

        fun iniciales(d: OcDesignacion) =
            listOf(d.nombres, d.apellidos).mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }.joinToString("")

        fun documento(d: OcDesignacion) = "${d.tipoDocumento} ${d.numeroDocumento}"

        fun documentoTexto(d: OcDesignacion) =
            "${nombre<DocumentoDesignacion>(d.documentoTipo)} del ${TextosOc.fechaCorta(d.documentoFecha)}"

        fun puntos(d: OcDesignacion): List<String> = d.djReqPuntos.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        inline fun <reified E> nombre(codigo: String): String where E : Enum<E>, E : com.security.modules.oficial.dominio.ConNombre =
            enumDe<E>(codigo)?.nombre ?: codigo

        fun esPendiente(tipo: String) = tipo == TipoPendiente.CAMBIO_DATOS.name || tipo == TipoPendiente.REMOCION.name

        fun esDeclaracion(ambito: String) = enumDe<AmbitoAdjunto>(ambito)?.esDeclaracion == true
    }
}
