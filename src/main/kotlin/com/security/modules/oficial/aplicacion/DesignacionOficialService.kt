package com.security.modules.oficial.aplicacion

import com.security.modules.oficial.dominio.AmbitoAdjunto
import com.security.modules.oficial.dominio.Dedicacion
import com.security.modules.oficial.dominio.DocumentoDesignacion
import com.security.modules.oficial.dominio.EstadoDesignacion
import com.security.modules.oficial.dominio.QuienDesigna
import com.security.modules.oficial.dominio.TextosOc
import com.security.modules.oficial.dominio.TipoCargo
import com.security.modules.oficial.dominio.TipoDocumentoOc
import com.security.modules.oficial.dominio.TipoEmpresaOc
import com.security.modules.oficial.dominio.TipoEvento
import com.security.modules.oficial.dominio.Vinculo
import com.security.modules.oficial.dominio.enumDe
import com.security.modules.oficial.persistencia.OcAdjunto
import com.security.modules.oficial.persistencia.OcDesignacion
import com.security.modules.oficial.persistencia.OcDesignacionRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/** Designar (OC 2 a OC 6), corregir antes de comunicar, ficha, lista y declaraciones. */
@Service
class DesignacionOficialService(
    private val designaciones: OcDesignacionRepository,
    private val adjuntos: AdjuntosOficialService,
    private val fichas: FichaOficialService,
    private val historial: HistorialOficial,
    private val catalogos: CatalogoOficialService,
    private val reloj: RelojOficial
) {

    /** Datos ya validados de un alta o una correccion. */
    private class Valida(
        val tipoEmpresa: TipoEmpresaOc,
        val dedicacion: Dedicacion,
        val nombres: String,
        val apellidos: String,
        val nacionalidad: String,
        val tipoDocumento: TipoDocumentoOc,
        val numeroDocumento: String,
        val fechaNacimiento: LocalDate,
        val domicilio: String,
        val telefono: String?,
        val correo: String,
        val cargo: String,
        val fechaIngreso: LocalDate,
        val vinculo: Vinculo,
        val direccionOficina: String,
        val designa: QuienDesigna,
        val nombreOrgano: String,
        val documentoTipo: DocumentoDesignacion,
        val documentoFecha: LocalDate,
        val fechaDesignacion: LocalDate,
        val reemplazaOficialAnterior: Boolean,
        val acta: OcAdjunto,
        val hojaVida: OcAdjunto?,
        val documentoVinculo: OcAdjunto?,
        val puntos: List<String>,
        val reqFirmaNombre: String?,
        val reqFechaFirma: LocalDate?,
        val reqAdjunto: OcAdjunto?,
        val desFirmaNombre: String?,
        val desFechaFirma: LocalDate?,
        val desAdjunto: OcAdjunto?
    )

    @Transactional
    fun crear(ctx: ContextoOc, pedido: DesignacionRequest): FichaResponse {
        ctx.exigirEscritura()
        exigirTitular(pedido.tipoCargo)
        val v = validar(ctx, pedido, null)
        if (fichas.titularActivo(ctx.empresaId) != null) throw titularActivo()

        val ahora = reloj.ahora()
        val d = OcDesignacion(
            uuid = UUID.randomUUID().toString(),
            empresaId = ctx.empresaId,
            titularActivoEmpresa = ctx.empresaId,
            estado = EstadoDesignacion.PENDIENTE_COMUNICAR.name,
            tipoCargo = TipoCargo.TITULAR.name,
            guardadaEn = ahora,
            guardadaPorId = ctx.actor.id,
            guardadaPorUsuario = ctx.actor.usuario,
            guardadaPorNombre = ctx.actor.nombreCompleto,
            actualizadaEn = ahora
        )
        aplicar(d, v)
        if (v.reemplazaOficialAnterior) d.reemplazaAId = fichas.ultimoTitularRemovido(ctx.empresaId)?.id
        val guardada = try {
            designaciones.saveAndFlush(d)
        } catch (e: DataIntegrityViolationException) {
            throw titularActivo()
        }
        vincularTodo(guardada, v)
        historial.anotar(
            ctx, guardada, TipoEvento.DESIGNACION_GUARDADA, "Designación guardada",
            detalle = "${v.dedicacion.let { "Titular · " + it.nombre.lowercase() }} · designación por ${v.nombreOrgano}. " +
                "Declaraciones juradas firmadas."
        )
        return fichas.ficha(ctx, guardada)
    }

    @Transactional
    fun corregir(ctx: ContextoOc, id: String, pedido: DesignacionRequest): FichaResponse {
        ctx.exigirEscritura()
        val d = fichas.buscar(ctx, id)
        if (FichaOficialService.estadoDe(d) != EstadoDesignacion.PENDIENTE_COMUNICAR) {
            throw Oc.estado("Solo se corrige una designación que aún no se comunicó a la UIF. Usa «Cambiar datos» o «Reemplazar o remover».")
        }
        exigirTitular(pedido.tipoCargo)
        val val0 = Validador()
        val motivo = val0.texto("motivo", pedido.motivo, 500, true, "Escribe el motivo de la corrección.")
        val v = try {
            validar(ctx, pedido, d)
        } catch (e: OficialException) {
            if (e.code == Oc.DATOS_INVALIDOS && val0.campos.isNotEmpty()) throw Oc.datos(val0.campos + e.campos.orEmpty())
            throw e
        }
        val0.lanzarSiHay()

        val antes = foto(d)
        aplicar(d, v)
        val despues = foto(d)
        val cambios = antes.keys.filter { antes[it]!!.second != despues[it]!!.second }
            .map { CambioCampo(it, antes[it]!!.first, antes[it]!!.second, despues[it]!!.second) }
        if (cambios.isEmpty()) {
            throw OficialException(HttpStatus.BAD_REQUEST, Oc.SIN_CAMBIOS, "No cambiaste ningún dato.")
        }
        d.actualizadaEn = reloj.ahora()
        val guardada = designaciones.save(d)
        vincularTodo(guardada, v)
        historial.anotar(
            ctx, guardada, TipoEvento.DESIGNACION_CORREGIDA,
            "Designación corregida",
            detalle = cambios.joinToString(" · ") { it.etiqueta },
            motivo = motivo, lista = cambios
        )
        return fichas.ficha(ctx, guardada)
    }

    @Transactional(readOnly = true)
    fun listar(ctx: ContextoOc): List<ResumenDesignacion> =
        designaciones.findByEmpresaIdOrderByGuardadaEnDescIdDesc(ctx.empresaId).map { fichas.resumen(it) }

    @Transactional(readOnly = true)
    fun ficha(ctx: ContextoOc, id: String): FichaResponse = fichas.ficha(ctx, fichas.buscar(ctx, id))

    @Transactional(readOnly = true)
    fun historial(ctx: ContextoOc, id: String): HistorialResponse = fichas.historial(ctx, fichas.buscar(ctx, id))

    // ============================================================
    // DECLARACIONES
    // ============================================================

    fun vistaPrevia(ctx: ContextoOc, p: VistaPreviaRequest): HojaDeclaracionResponse {
        ctx.exigirEscritura()
        val nombre = listOfNotNull(p.nombres?.trim()?.takeIf { it.isNotEmpty() }, p.apellidos?.trim()?.takeIf { it.isNotEmpty() })
            .joinToString(" ").ifEmpty { "[Nombres Apellidos]" }
        val tipoDoc = enumDe<TipoDocumentoOc>(p.tipoDocumento)
        val numero = p.numeroDocumento?.trim()?.takeIf { it.isNotEmpty() }
        val documento = if (tipoDoc != null && numero != null) "${tipoDoc.name} $numero" else "[Documento]"
        val cargo = (enumDe<TipoCargo>(p.tipoCargo) ?: TipoCargo.TITULAR).nombre.lowercase()
        return hoja(nombre.take(310), documento.take(40), cargo, ctx.empresa.nombre, enumDe<Vinculo>(p.vinculo), p.fecha ?: reloj.hoy())
    }

    @Transactional(readOnly = true)
    fun declaracionRequisitos(ctx: ContextoOc, id: String): HojaDeclaracionResponse {
        val d = fichas.buscar(ctx, id)
        val base = hoja(
            FichaOficialService.nombreCompleto(d), FichaOficialService.documento(d),
            FichaOficialService.nombre<TipoCargo>(d.tipoCargo).lowercase(), ctx.empresa.nombre,
            enumDe<Vinculo>(d.vinculo), d.djReqFechaFirma ?: d.guardadaEn.toLocalDate()
        )
        return base.copy(
            firmada = d.djReqFirmaNombre != null || d.djReqAdjuntoId != null,
            fechaFirma = d.djReqFechaFirma,
            adjunto = if (ctx.actor.puedeEscribir) adjuntos.respuesta(adjuntos.porId(d.djReqAdjuntoId, ctx.empresaId)) else null,
            vigente = FichaOficialService.estadoDe(d).activa,
            firmaNombre = d.djReqFirmaNombre ?: base.firmaNombre
        )
    }

    @Transactional(readOnly = true)
    fun declaracionDesignante(ctx: ContextoOc, id: String): DjDesignanteVerResponse {
        val d = fichas.buscar(ctx, id)
        return DjDesignanteVerResponse(
            titulo = "Declaración jurada de quién designó",
            organo = d.nombreOrgano,
            designaNombre = FichaOficialService.nombre<QuienDesigna>(d.designa),
            documento = FichaOficialService.documentoTexto(d),
            texto = TextosOc.DJ_DESIGNANTE,
            aceptada = d.djDesAceptada,
            firmaNombre = d.djDesFirmaNombre,
            fechaFirma = d.djDesFechaFirma,
            adjunto = if (ctx.actor.puedeEscribir) adjuntos.respuesta(adjuntos.porId(d.djDesAdjuntoId, ctx.empresaId)) else null,
            vigente = FichaOficialService.estadoDe(d).activa
        )
    }

    private fun hoja(
        nombre: String, documento: String, cargo: String, empresa: String, vinculo: Vinculo?, fecha: LocalDate
    ) = HojaDeclaracionResponse(
        titulo = TextosOc.TITULO_DJ_REQUISITOS,
        encabezado = "Yo, $nombre, identificado con $documento, designado como oficial de cumplimiento $cargo de $empresa, " +
            "declaro bajo juramento que:",
        puntos = TextosOc.puntosRequisitos(vinculo).map { PuntoHoja(it.letra, it.texto) },
        lugarFecha = "${TextosOc.LUGAR}, ${TextosOc.fechaLarga(fecha)}",
        firmaNombre = nombre,
        firmaDocumento = documento
    )

    // ============================================================
    // VALIDACION
    // ============================================================

    private fun exigirTitular(tipoCargo: String?) {
        val tipo = enumDe<TipoCargo>(tipoCargo) ?: throw Oc.dato("tipoCargo", "Elige el cargo a designar.")
        if (!tipo.disponible) throw Oc.pronto("Designar un oficial ${tipo.nombre.lowercase()} estará disponible pronto.")
    }

    private fun validar(ctx: ContextoOc, p: DesignacionRequest, actual: OcDesignacion?): Valida {
        val v = Validador()
        val hoy = reloj.hoy()
        val persona = p.persona ?: PersonaRequest()
        val cargo = p.cargo ?: CargoRequest()
        val des = p.designacion ?: DatosDesignacionRequest()
        val req = p.declaraciones?.requisitos ?: DjRequisitosRequest()
        val dsg = p.declaraciones?.designante ?: DjDesignanteRequest()

        val tipoEmpresa = enumDe<TipoEmpresaOc>(p.tipoEmpresa)
        if (tipoEmpresa == null) v.error("tipoEmpresa", "Elige el tipo de empresa.")
        val dedicacion = enumDe<Dedicacion>(p.dedicacion)
        if (dedicacion == null) v.error("dedicacion", "Elige la dedicación.")

        // Persona
        val nombres = v.texto("persona.nombres", persona.nombres, 150, true, "Escribe los nombres.")
        val apellidos = v.texto("persona.apellidos", persona.apellidos, 150, true, "Escribe los apellidos.")
        val nacionalidad = persona.nacionalidad?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (nacionalidad == null) v.error("persona.nacionalidad", "Elige la nacionalidad.")
        else if (!catalogos.existePais(nacionalidad)) v.error("persona.nacionalidad", "Esa nacionalidad no está en el catálogo.")
        val tipoDocumento = enumDe<TipoDocumentoOc>(persona.tipoDocumento)
        if (tipoDocumento == null) v.error("persona.tipoDocumento", "Elige el tipo de documento.")
        val numero = v.texto("persona.numeroDocumento", persona.numeroDocumento?.replace(" ", ""), 20, true, "Escribe el número de documento.")
            ?.uppercase()
        if (numero != null && tipoDocumento != null && !tipoDocumento.patron.matches(numero)) {
            v.error("persona.numeroDocumento", tipoDocumento.mensaje)
        }
        val nacimiento = persona.fechaNacimiento
        if (nacimiento == null) v.error("persona.fechaNacimiento", "Indica la fecha de nacimiento.")
        else if (!nacimiento.isBefore(hoy)) v.error("persona.fechaNacimiento", "La fecha de nacimiento debe ser anterior a hoy.")
        val domicilio = v.texto("persona.domicilio", persona.domicilio, 255, true, "Escribe el domicilio.")
        val telefono = v.texto("persona.telefono", persona.telefono, 30, false, "")
        if (telefono != null && !TELEFONO.matches(telefono)) v.error("persona.telefono", "El teléfono solo puede tener dígitos, espacios, +, - y paréntesis.")
        val correo = v.texto("persona.correo", persona.correo, 150, true, "Escribe el correo electrónico.")
        if (correo != null && !CORREO.matches(correo)) v.error("persona.correo", "El correo no tiene un formato válido.")

        // Cargo
        val cargoTexto = v.texto("cargo.cargo", cargo.cargo, 150, true, "Escribe el cargo que desempeña.")
        val ingreso = v.fechaNoFutura("cargo.fechaIngreso", cargo.fechaIngreso, hoy, true, "Indica la fecha de ingreso.")
        val vinculo = enumDe<Vinculo>(cargo.vinculo)
        if (vinculo == null) v.error("cargo.vinculo", "Elige el vínculo con la empresa.")
        val oficina = v.texto("cargo.direccionOficina", cargo.direccionOficina, 255, true, "Escribe la dirección de la oficina donde trabaja.")

        // Designacion
        val designa = enumDe<QuienDesigna>(des.designa)
        if (designa == null) v.error("designacion.designa", "Elige quién designa.")
        else if (tipoEmpresa != null && !tipoEmpresa.permite(designa)) {
            v.error("designacion.designa", "Para una empresa «${tipoEmpresa.nombre.lowercase()}» designa: ${tipoEmpresa.quienDesigna}")
        }
        val organo = v.texto("designacion.nombreOrgano", des.nombreOrgano, 255, true, "Escribe el nombre de quien designa.")
        val docTipo = enumDe<DocumentoDesignacion>(des.documentoTipo)
        if (docTipo == null) v.error("designacion.documentoTipo", "Elige el tipo de documento de designación.")
        val fechaDesignacion = v.fechaNoFutura("designacion.fechaDesignacion", des.fechaDesignacion, hoy, true, "Indica la fecha de designación.")
        val docFecha = v.fechaNoFutura("designacion.documentoFecha", des.documentoFecha, hoy, true, "Indica la fecha del documento.")
        if (docFecha != null && fechaDesignacion != null && docFecha.isAfter(fechaDesignacion)) {
            v.error("designacion.documentoFecha", "La fecha del documento no puede ser posterior a la fecha de designación.")
        }
        if (des.esOficialOtraEmpresa == true) {
            v.error("designacion.esOficialOtraEmpresa", "Solo un oficial corporativo autorizado puede serlo de otra empresa (art. 5.2).")
        }
        val acta = adjuntos.resolver(ctx, v, "designacion.adjuntoId", des.adjuntoId, AmbitoAdjunto.ACTA_DESIGNACION, true,
            actual?.actaAdjuntoId, "Adjunta el acta o el documento que acredita la designación.")
        val hoja = adjuntos.resolver(ctx, v, "designacion.hojaVidaAdjuntoId", des.hojaVidaAdjuntoId, AmbitoAdjunto.HOJA_DE_VIDA, false, actual?.hojaVidaAdjuntoId)
        val docVinculo = adjuntos.resolver(ctx, v, "designacion.documentoVinculoAdjuntoId", des.documentoVinculoAdjuntoId,
            AmbitoAdjunto.DOCUMENTO_VINCULO, false, actual?.documentoVinculoAdjuntoId)

        // Declaracion de requisitos
        val puntos = req.puntos.orEmpty().map { it.trim().lowercase() }
        if (puntos.toSet() != TextosOc.LETRAS.toSet() || puntos.size != TextosOc.LETRAS.size) {
            v.error("declaraciones.requisitos.puntos", "Marca las 8 afirmaciones de la declaración jurada de requisitos.")
        }
        val reqAdj = adjuntos.resolver(ctx, v, "declaraciones.requisitos.adjuntoId", req.adjuntoId,
            AmbitoAdjunto.DJ_REQUISITOS_FIRMADA, false, actual?.djReqAdjuntoId)
        val (reqNombre, reqFecha) = firma(v, "declaraciones.requisitos", req.firmaNombre, req.fechaFirma, reqAdj, req.adjuntoId, hoy)

        // Declaracion de quien designo
        if (dsg.aceptada != true) {
            v.error("declaraciones.designante.aceptada", "Marca la declaración jurada de quién designó.")
        }
        val desAdj = adjuntos.resolver(ctx, v, "declaraciones.designante.adjuntoId", dsg.adjuntoId,
            AmbitoAdjunto.DJ_DESIGNANTE_FIRMADA, false, actual?.djDesAdjuntoId)
        val (desNombre, desFecha) = firma(v, "declaraciones.designante", dsg.firmaNombre, dsg.fechaFirma, desAdj, dsg.adjuntoId, hoy)

        v.lanzarSiHay()
        return Valida(
            tipoEmpresa!!, dedicacion!!, nombres!!, apellidos!!, nacionalidad!!, tipoDocumento!!, numero!!, nacimiento!!,
            domicilio!!, telefono, correo!!, cargoTexto!!, ingreso!!, vinculo!!, oficina!!, designa!!, organo!!, docTipo!!,
            docFecha!!, fechaDesignacion!!, des.reemplazaOficialAnterior == true, acta!!, hoja, docVinculo,
            TextosOc.LETRAS, reqNombre, reqFecha, reqAdj, desNombre, desFecha, desAdj
        )
    }

    /** Firma escrita (nombre + fecha) o PDF firmado: al menos una de las dos. */
    private fun firma(
        v: Validador, prefijo: String, nombre: String?, fecha: LocalDate?, adjunto: OcAdjunto?, adjuntoId: String?, hoy: LocalDate
    ): Pair<String?, LocalDate?> {
        val n = v.texto("$prefijo.firmaNombre", nombre, 255, false, "")
        val f = v.fechaNoFutura("$prefijo.fechaFirma", fecha, hoy, false, "")
        val hayAdjunto = adjunto != null || !adjuntoId.isNullOrBlank()
        if (n != null && fecha == null) v.error("$prefijo.fechaFirma", "Indica la fecha de la firma.")
        if (n == null && f != null && !hayAdjunto) v.error("$prefijo.firmaNombre", "Escribe el nombre completo de quien firma.")
        if (n == null && nombre.isNullOrBlank() && !hayAdjunto) {
            v.error("$prefijo.firmaNombre", "Escribe el nombre completo para firmar o sube la declaración firmada.")
        }
        return n to f
    }

    private fun aplicar(d: OcDesignacion, v: Valida) {
        d.tipoEmpresa = v.tipoEmpresa.name
        d.dedicacion = v.dedicacion.name
        d.nombres = v.nombres
        d.apellidos = v.apellidos
        d.nacionalidad = v.nacionalidad
        d.tipoDocumento = v.tipoDocumento.name
        d.numeroDocumento = v.numeroDocumento
        d.fechaNacimiento = v.fechaNacimiento
        d.domicilio = v.domicilio
        d.telefono = v.telefono
        d.correo = v.correo
        d.cargo = v.cargo
        d.fechaIngreso = v.fechaIngreso
        d.vinculo = v.vinculo.name
        d.direccionOficina = v.direccionOficina
        d.designa = v.designa.name
        d.nombreOrgano = v.nombreOrgano
        d.documentoTipo = v.documentoTipo.name
        d.documentoFecha = v.documentoFecha
        d.fechaDesignacion = v.fechaDesignacion
        d.esOficialOtraEmpresa = false
        d.reemplazaOficialAnterior = v.reemplazaOficialAnterior
        d.actaAdjuntoId = v.acta.id
        d.hojaVidaAdjuntoId = v.hojaVida?.id
        d.documentoVinculoAdjuntoId = v.documentoVinculo?.id
        d.djReqPuntos = v.puntos.joinToString(",")
        d.djReqFirmaNombre = v.reqFirmaNombre
        d.djReqFechaFirma = v.reqFechaFirma
        d.djReqAdjuntoId = v.reqAdjunto?.id
        d.djTextoVersion = TextosOc.VERSION
        d.djDesAceptada = true
        d.djDesFirmaNombre = v.desFirmaNombre
        d.djDesFechaFirma = v.desFechaFirma
        d.djDesAdjuntoId = v.desAdjunto?.id
    }

    private fun vincularTodo(d: OcDesignacion, v: Valida) {
        listOf(v.acta, v.hojaVida, v.documentoVinculo, v.reqAdjunto, v.desAdjunto)
            .forEach { adjuntos.vincular(it, d.id) }
    }

    /** Valores legibles de cada campo, para el «antes → después» de una correccion. */
    private fun foto(d: OcDesignacion): LinkedHashMap<String, Pair<String, String?>> {
        fun adj(id: Long?) = adjuntos.porId(id, d.empresaId)?.nombreOriginal
        return linkedMapOf(
            "tipoEmpresa" to ("Tipo de empresa" to FichaOficialService.nombre<TipoEmpresaOc>(d.tipoEmpresa)),
            "dedicacion" to ("Dedicación" to FichaOficialService.nombre<Dedicacion>(d.dedicacion)),
            "persona.nombres" to ("Nombres" to d.nombres),
            "persona.apellidos" to ("Apellidos" to d.apellidos),
            "persona.nacionalidad" to ("Nacionalidad" to (catalogos.nombrePais(d.nacionalidad) ?: d.nacionalidad)),
            "persona.tipoDocumento" to ("Tipo de documento" to FichaOficialService.nombre<TipoDocumentoOc>(d.tipoDocumento)),
            "persona.numeroDocumento" to ("N.º de documento" to d.numeroDocumento),
            "persona.fechaNacimiento" to ("Fecha de nacimiento" to TextosOc.fechaCorta(d.fechaNacimiento)),
            "persona.domicilio" to ("Domicilio" to d.domicilio),
            "persona.telefono" to ("Teléfono" to d.telefono),
            "persona.correo" to ("Correo electrónico" to d.correo),
            "cargo.cargo" to ("Cargo que desempeña" to d.cargo),
            "cargo.fechaIngreso" to ("Fecha de ingreso" to TextosOc.fechaCorta(d.fechaIngreso)),
            "cargo.vinculo" to ("Vínculo con la empresa" to FichaOficialService.nombre<Vinculo>(d.vinculo)),
            "cargo.direccionOficina" to ("Dirección de la oficina" to d.direccionOficina),
            "designacion.designa" to ("Persona u órgano que designa" to FichaOficialService.nombre<QuienDesigna>(d.designa)),
            "designacion.nombreOrgano" to ("Nombre del órgano" to d.nombreOrgano),
            "designacion.documentoTipo" to ("Tipo de documento de designación" to FichaOficialService.nombre<DocumentoDesignacion>(d.documentoTipo)),
            "designacion.documentoFecha" to ("Fecha del documento" to TextosOc.fechaCorta(d.documentoFecha)),
            "designacion.fechaDesignacion" to ("Fecha de designación" to TextosOc.fechaCorta(d.fechaDesignacion)),
            "designacion.adjuntoId" to ("Documento de designación" to adj(d.actaAdjuntoId)),
            "designacion.hojaVidaAdjuntoId" to ("Hoja de vida" to adj(d.hojaVidaAdjuntoId)),
            "designacion.documentoVinculoAdjuntoId" to ("Documento del vínculo" to adj(d.documentoVinculoAdjuntoId)),
            "designacion.reemplazaOficialAnterior" to ("Reemplaza a un oficial anterior" to if (d.reemplazaOficialAnterior) "Sí" else "No"),
            "declaraciones.requisitos.firmaNombre" to ("Firma del oficial designado" to d.djReqFirmaNombre),
            "declaraciones.requisitos.fechaFirma" to ("Fecha de firma del oficial" to TextosOc.fechaCorta(d.djReqFechaFirma)),
            "declaraciones.requisitos.adjuntoId" to ("Declaración de requisitos firmada" to adj(d.djReqAdjuntoId)),
            "declaraciones.designante.firmaNombre" to ("Firma de quien designa" to d.djDesFirmaNombre),
            "declaraciones.designante.fechaFirma" to ("Fecha de firma de quien designa" to TextosOc.fechaCorta(d.djDesFechaFirma)),
            "declaraciones.designante.adjuntoId" to ("Declaración de quién designó firmada" to adj(d.djDesAdjuntoId))
        )
    }

    private fun titularActivo() = OficialException(
        HttpStatus.CONFLICT, Oc.TITULAR_ACTIVO,
        "Ya hay un oficial titular designado. Para cambiarlo usa «Reemplazar o remover»."
    )

    companion object {
        val TELEFONO = Regex("[0-9 +()\\-]{3,30}")
        val CORREO = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
