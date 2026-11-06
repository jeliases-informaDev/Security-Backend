package com.security.modules.oficial.aplicacion

import com.security.modules.oficial.dominio.AmbitoAdjunto
import com.security.modules.oficial.dominio.EstadoDesignacion
import com.security.modules.oficial.dominio.EstadoEmpresaOc
import com.security.modules.oficial.dominio.MotivoRemocion
import com.security.modules.oficial.dominio.TextosOc
import com.security.modules.oficial.dominio.TipoCargo
import com.security.modules.oficial.dominio.TipoEvento
import com.security.modules.oficial.dominio.TipoPendiente
import com.security.modules.oficial.dominio.enumDe
import com.security.modules.oficial.persistencia.OcDesignacionRepository
import com.security.modules.oficial.persistencia.OcPendiente
import com.security.modules.oficial.persistencia.OcPendienteRepository
import com.security.modules.oficial.persistencia.OcRemocion
import com.security.modules.oficial.persistencia.OcRemocionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Cambio de datos (OC 10), remocion y vacancia (OC 12, OC 13) y el cierre de los pendientes con la UIF. */
@Service
class CicloOficialService(
    private val designaciones: OcDesignacionRepository,
    private val remociones: OcRemocionRepository,
    private val pendientes: OcPendienteRepository,
    private val adjuntos: AdjuntosOficialService,
    private val fichas: FichaOficialService,
    private val historial: HistorialOficial,
    private val reglas: ReglasOficialService,
    private val reloj: RelojOficial
) {

    // ============================================================
    // CAMBIAR DATOS (solo contacto y cargo)
    // ============================================================

    @Transactional
    fun cambiarDatos(ctx: ContextoOc, id: String, p: CambioDatosRequest): CambioDatosResponse {
        ctx.exigirEscritura()
        val d = fichas.buscar(ctx, id)
        val estado = FichaOficialService.estadoDe(d)
        if (estado != EstadoDesignacion.VIGENTE && estado != EstadoDesignacion.EN_EVALUACION) {
            throw Oc.estado("Solo se cambian los datos de un oficial ya comunicado a la UIF. Si aún no se comunicó, corrige la designación.")
        }
        val hoy = reloj.hoy()
        val v = Validador()
        val motivo = v.texto("motivo", p.motivo, 500, true, "Escribe el motivo del cambio.")
        val fechaCambio = v.fechaNoFutura("fechaCambio", p.fechaCambio, hoy, false, "") ?: if (p.fechaCambio == null) hoy else null
        if (fechaCambio != null && fechaCambio.isBefore(d.fechaDesignacion)) {
            v.error("fechaCambio", "El cambio no puede ser anterior a la designación.")
        }

        val cambios = mutableListOf<CambioCampo>()
        // Telefono: opcional; "" lo borra.
        if (p.telefono != null) {
            val nuevo = v.texto("telefono", p.telefono, 30, false, "")
            if (nuevo != null && !DesignacionOficialService.TELEFONO.matches(nuevo)) {
                v.error("telefono", "El teléfono solo puede tener dígitos, espacios, +, - y paréntesis.")
            }
            if (nuevo != d.telefono && !v.campos.containsKey("telefono")) cambios += CambioCampo("telefono", "Teléfono", d.telefono, nuevo)
        }
        fun obligatorio(campo: String, etiqueta: String, valor: String?, max: Int, actual: String, falta: String): String? {
            if (valor == null) return null
            val nuevo = v.texto(campo, valor, max, true, falta) ?: return null
            if (nuevo != actual) cambios += CambioCampo(campo, etiqueta, actual, nuevo)
            return nuevo
        }
        val correo = obligatorio("correo", "Correo electrónico", p.correo, 150, d.correo, "Escribe el correo electrónico.")
        if (correo != null && !DesignacionOficialService.CORREO.matches(correo)) {
            v.error("correo", "El correo no tiene un formato válido.")
            cambios.removeIf { it.campo == "correo" }
        }
        obligatorio("domicilio", "Domicilio", p.domicilio, 255, d.domicilio, "Escribe el domicilio.")
        obligatorio("cargo", "Cargo que desempeña", p.cargo, 150, d.cargo, "Escribe el cargo que desempeña.")
        obligatorio("direccionOficina", "Dirección de la oficina", p.direccionOficina, 255, d.direccionOficina,
            "Escribe la dirección de la oficina.")
        v.lanzarSiHay()
        if (cambios.isEmpty()) throw OficialException(HttpStatus.BAD_REQUEST, Oc.SIN_CAMBIOS, "No cambiaste ningún dato.")

        cambios.forEach { c ->
            when (c.campo) {
                "telefono" -> d.telefono = c.despues
                "correo" -> d.correo = c.despues!!
                "domicilio" -> d.domicilio = c.despues!!
                "cargo" -> d.cargo = c.despues!!
                "direccionOficina" -> d.direccionOficina = c.despues!!
            }
        }
        val ahora = reloj.ahora()
        d.actualizadaEn = ahora
        val guardada = designaciones.save(d)

        val nombres = cambios.joinToString(", ") { it.etiqueta.lowercase() }
        val detalle = cambios.joinToString(" · ") { "${it.etiqueta}: ${it.antes ?: "(vacío)"} → ${it.despues ?: "(vacío)"}" }
        val pendiente = pendientes.save(
            OcPendiente(
                uuid = UUID.randomUUID().toString(),
                empresaId = ctx.empresaId,
                designacionId = guardada.id!!,
                tipo = TipoPendiente.CAMBIO_DATOS.name,
                titulo = if (cambios.size == 1) "Comunicar el cambio de $nombres" else "Comunicar el cambio de datos",
                detalle = detalle.take(2000),
                regla = ReglasPorDefecto.PLAZO_COMUNICAR_CAMBIO,
                desde = fechaCambio!!,
                creadoEn = ahora,
                creadoPorUsuario = ctx.actor.usuario,
                creadoPorNombre = ctx.actor.nombreCompleto
            )
        )
        historial.anotar(
            ctx, guardada, TipoEvento.CAMBIO_DATOS, "Cambio de datos: $nombres",
            detalle = cambios.joinToString(" · ") { "${it.antes ?: "(vacío)"} → ${it.despues ?: "(vacío)"}" },
            motivo = motivo, pendienteId = pendiente.id, lista = cambios
        )
        val motor = reglas.motor()
        return CambioDatosResponse(cambios, fichas.pendiente(ctx, pendiente, motor), fichas.ficha(ctx, guardada))
    }

    // ============================================================
    // REMOCION
    // ============================================================

    @Transactional
    fun remover(ctx: ContextoOc, id: String, p: RemocionRequest): RemocionCreadaResponse {
        ctx.exigirEscritura()
        val d = fichas.buscar(ctx, id)
        val estado = FichaOficialService.estadoDe(d)
        if (estado != EstadoDesignacion.VIGENTE && estado != EstadoDesignacion.EN_EVALUACION) {
            throw Oc.estado(
                if (estado == EstadoDesignacion.REMOVIDA) "Esta designación ya fue removida."
                else "La designación aún no se comunicó a la UIF: corrígela en vez de removerla."
            )
        }
        val motivo = enumDe<MotivoRemocion>(p.motivo) ?: throw Oc.dato("motivo", "Elige el motivo.")
        if (!motivo.disponible) throw Oc.pronto("«${motivo.nombre}» estará disponible pronto (necesita al oficial alterno).")
        val esDecision = motivo == MotivoRemocion.REMOCION_EMPRESA
        val hoy = reloj.hoy()
        val v = Validador()
        val razones = v.texto("razones", p.razones, 2000, true, "Escribe las razones que justifican la medida.")
        val aprobada = v.texto("aprobadaPor", p.aprobadaPor, 255, esDecision, "Indica quién aprobó la remoción.")
        val fecha = v.fechaNoFutura("fechaDecision", p.fechaDecision, hoy, true, "Indica la fecha de la decisión.")
        if (fecha != null && fecha.isBefore(d.fechaDesignacion)) {
            v.error("fechaDecision", "La fecha no puede ser anterior a la designación.")
        }
        val acta = adjuntos.resolver(ctx, v, "actaAdjuntoId", p.actaAdjuntoId, AmbitoAdjunto.ACTA_REMOCION, esDecision,
            faltaMensaje = "Adjunta el acta o el documento de la decisión.")
        v.lanzarSiHay()

        val ahora = reloj.ahora()
        val remocion = remociones.save(
            OcRemocion(
                uuid = UUID.randomUUID().toString(),
                empresaId = ctx.empresaId,
                designacionId = d.id!!,
                motivo = motivo.name,
                razones = razones!!,
                aprobadaPor = aprobada,
                fechaDecision = fecha!!,
                actaAdjuntoId = acta?.id,
                registradaEn = ahora,
                registradaPorUsuario = ctx.actor.usuario,
                registradaPorNombre = ctx.actor.nombreCompleto
            )
        )
        adjuntos.vincular(acta, d.id)
        d.estado = EstadoDesignacion.REMOVIDA.name
        d.titularActivoEmpresa = null
        d.removidaEn = ahora
        d.actualizadaEn = ahora
        val guardada = designaciones.save(d)

        val pendiente = pendientes.save(
            OcPendiente(
                uuid = UUID.randomUUID().toString(),
                empresaId = ctx.empresaId,
                designacionId = guardada.id!!,
                tipo = TipoPendiente.REMOCION.name,
                titulo = "Comunicar la remoción",
                detalle = "${motivo.nombre}. Decisión del ${TextosOc.fechaCorta(fecha)}" + (aprobada?.let { ", aprobada por $it" } ?: "") + ".",
                regla = ReglasPorDefecto.PLAZO_COMUNICAR_REMOCION,
                desde = fecha,
                remocionId = remocion.id,
                creadoEn = ahora,
                creadoPorUsuario = ctx.actor.usuario,
                creadoPorNombre = ctx.actor.nombreCompleto
            )
        )
        historial.anotar(
            ctx, guardada, TipoEvento.REMOCION, "Remoción registrada",
            detalle = "${motivo.nombre}. Decisión del ${TextosOc.fechaCorta(fecha)}" +
                (aprobada?.let { ", aprobada por $it" } ?: "") + (if (acta != null) ", con acta y sustento." else ", con sustento."),
            motivo = razones, pendienteId = pendiente.id
        )

        val motor = reglas.motor()
        val estadoEmpresa = if (d.tipoCargo == TipoCargo.TITULAR.name && fichas.titularActivo(ctx.empresaId) == null) {
            EstadoEmpresaOc.VACANTE
        } else fichas.estado(ctx).let { enumDe<EstadoEmpresaOc>(it.estado) ?: EstadoEmpresaOc.VACANTE }
        return RemocionCreadaResponse(
            designacionId = guardada.uuid,
            estadoEmpresa = estadoEmpresa.name,
            remocion = fichas.remocion(remocion, ctx),
            pendiente = fichas.pendiente(ctx, pendiente, motor),
            plazos = PlazosRemocionCreada(
                comunicarRemocion = motor.plazo(ReglasPorDefecto.PLAZO_COMUNICAR_REMOCION, fecha),
                vacancia = motor.plazo(ReglasPorDefecto.VACANCIA_MAXIMA, fecha)
            )
        )
    }

    // ============================================================
    // REGISTRAR LA COMUNICACION DE UN PENDIENTE
    // ============================================================

    @Transactional
    fun comunicarPendiente(ctx: ContextoOc, id: String, p: ComunicacionPendienteRequest): PendienteResponse {
        ctx.exigirEscritura()
        val pendiente = pendientes.findByUuidAndEmpresaId(id.trim(), ctx.empresaId) ?: throw Oc.noEncontrado("No existe ese pendiente.")
        if (pendiente.comunicado) {
            throw OficialException(HttpStatus.CONFLICT, Oc.YA_COMUNICADO, "Ese pendiente ya se registró como comunicado.")
        }
        val v = Validador()
        val fecha = v.fechaNoFutura("fecha", p.fecha, reloj.hoy(), true, "Indica la fecha en que se comunicó.")
        if (fecha != null && fecha.isBefore(pendiente.desde)) v.error("fecha", "La fecha no puede ser anterior al hecho que se comunica.")
        val numero = v.texto("numeroSolicitud", p.numeroSolicitud, 50, false, "")
        val nota = v.texto("nota", p.nota, 500, false, "")
        val constancia = adjuntos.resolver(ctx, v, "constanciaAdjuntoId", p.constanciaAdjuntoId,
            AmbitoAdjunto.CONSTANCIA_COMUNICACION, false)
        v.lanzarSiHay()

        val ahora = reloj.ahora()
        pendiente.comunicado = true
        pendiente.comFecha = fecha
        pendiente.comNumeroSolicitud = numero
        pendiente.comNota = nota
        pendiente.comConstanciaAdjuntoId = constancia?.id
        pendiente.comRegistradaEn = ahora
        pendiente.comRegistradaPorUsuario = ctx.actor.usuario
        pendiente.comRegistradaPorNombre = ctx.actor.nombreCompleto
        val guardado = pendientes.save(pendiente)
        adjuntos.vincular(constancia, pendiente.designacionId)

        val designacion = designaciones.findByIdAndEmpresaId(pendiente.designacionId, ctx.empresaId)
        if (designacion != null) {
            val esRemocion = pendiente.tipo == TipoPendiente.REMOCION.name
            historial.anotar(
                ctx, designacion,
                if (esRemocion) TipoEvento.REMOCION_COMUNICADA else TipoEvento.CAMBIO_COMUNICADO,
                if (esRemocion) "Remoción comunicada a la UIF" else "Cambio de datos comunicado a la UIF",
                detalle = listOfNotNull(
                    "Comunicado el ${TextosOc.fechaCorta(fecha)}",
                    numero?.let { "solicitud $it" },
                    if (constancia != null) "con constancia" else null
                ).joinToString(", ") + ".",
                motivo = nota,
                pendienteId = pendiente.id
            )
        }
        return fichas.pendiente(ctx, guardado, reglas.motor())
    }
}
