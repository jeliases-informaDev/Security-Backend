package com.security.modules.oficial.aplicacion

import com.security.modules.oficial.dominio.AmbitoAdjunto
import com.security.modules.oficial.dominio.EstadoDesignacion
import com.security.modules.oficial.dominio.ResultadoUif
import com.security.modules.oficial.dominio.TextosOc
import com.security.modules.oficial.dominio.TipoEvento
import com.security.modules.oficial.dominio.enumDe
import com.security.modules.oficial.persistencia.OcDesignacionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Comunicacion de la designacion a la UIF (OC 8), en el mismo registro y en dos momentos: presentada (En evaluacion) y la
 * respuesta (Procedente / No procedente). Los codigos secretos se cifran antes de guardar y nunca se devuelven.
 */
@Service
class ComunicacionOficialService(
    private val designaciones: OcDesignacionRepository,
    private val adjuntos: AdjuntosOficialService,
    private val fichas: FichaOficialService,
    private val historial: HistorialOficial,
    private val cifrado: CifradoCodigosOficial,
    private val reloj: RelojOficial
) {

    @Transactional
    fun registrar(ctx: ContextoOc, id: String, p: ComunicacionRequest): FichaResponse {
        ctx.exigirEscritura()
        val d = fichas.buscar(ctx, id)
        val estado = FichaOficialService.estadoDe(d)
        val hoy = reloj.hoy()
        val v = Validador()

        val resultado = enumDe<ResultadoUif>(p.resultado)
        if (resultado == null) v.error("resultado", "Elige el resultado de la UIF.")

        if (!estado.activa) throw Oc.estado("Esta designación ya fue removida.")
        if (estado == EstadoDesignacion.VIGENTE && resultado != null && resultado != ResultadoUif.PROCEDENTE) {
            throw Oc.estado("La designación ya está vigente: solo puedes actualizar los códigos o la constancia.")
        }

        val fechaCom = v.fechaNoFutura("fechaComunicacion", p.fechaComunicacion, hoy, true, "Indica la fecha de comunicación.")
        if (fechaCom != null && fechaCom.isBefore(d.fechaDesignacion)) {
            v.error("fechaComunicacion", "La comunicación no puede ser anterior a la fecha de designación.")
        }
        val numero = v.texto("numeroSolicitud", p.numeroSolicitud, 50, true, "Escribe el N.º de solicitud en SISDEL.")
        val necesitaNotificacion = resultado == ResultadoUif.PROCEDENTE || resultado == ResultadoUif.NO_PROCEDENTE
        val fechaNot = v.fechaNoFutura("fechaNotificacion", p.fechaNotificacion, hoy, necesitaNotificacion, "Indica la fecha de notificación de la UIF.")
        if (fechaNot != null && fechaCom != null && fechaNot.isBefore(fechaCom)) {
            v.error("fechaNotificacion", "La notificación no puede ser anterior a la comunicación.")
        }
        val motivo = v.texto(
            "motivoNoProcedente", p.motivoNoProcedente, 1000, resultado == ResultadoUif.NO_PROCEDENTE,
            "Anota el motivo que dio la UIF."
        )
        val codigoEmpresa = v.texto("codigoEmpresa", p.codigoEmpresa, 100, false, "")
        val codigoOficial = v.texto("codigoOficial", p.codigoOficial, 100, false, "")
        val hayCodigos = codigoEmpresa != null || codigoOficial != null
        if (hayCodigos && resultado != null && resultado != ResultadoUif.PROCEDENTE) {
            if (codigoEmpresa != null) v.error("codigoEmpresa", "La UIF entrega los códigos solo si la designación es procedente.")
            if (codigoOficial != null) v.error("codigoOficial", "La UIF entrega los códigos solo si la designación es procedente.")
        }
        val constancia = adjuntos.resolver(ctx, v, "constanciaAdjuntoId", p.constanciaAdjuntoId, AmbitoAdjunto.CONSTANCIA_UIF, false,
            d.comConstanciaAdjuntoId)
        v.lanzarSiHay()
        // Sin clave no se guarda NADA de esta llamada (ni siquiera el resultado).
        if (hayCodigos) cifrado.exigirDisponible()

        val ahora = reloj.ahora()
        val estabaVigente = estado == EstadoDesignacion.VIGENTE
        d.comFecha = fechaCom
        d.comNumeroSolicitud = numero
        d.comResultado = resultado!!.name
        d.comFechaNotificacion = if (necesitaNotificacion) fechaNot else null
        d.comMotivoNoProcedente = if (resultado == ResultadoUif.NO_PROCEDENTE) motivo else null
        if (constancia != null) d.comConstanciaAdjuntoId = constancia.id
        d.comRegistradaEn = ahora
        if (codigoEmpresa != null) d.codigoEmpresaCifrado = cifrado.cifrar(codigoEmpresa, "${d.uuid}|codigoEmpresa")
        if (codigoOficial != null) d.codigoOficialCifrado = cifrado.cifrar(codigoOficial, "${d.uuid}|codigoOficial")
        if (hayCodigos) d.codigosRegistradosEn = ahora
        d.estado = when (resultado) {
            ResultadoUif.EN_EVALUACION -> EstadoDesignacion.EN_EVALUACION
            ResultadoUif.PROCEDENTE -> EstadoDesignacion.VIGENTE
            ResultadoUif.NO_PROCEDENTE -> EstadoDesignacion.PENDIENTE_COMUNICAR
        }.name
        d.actualizadaEn = ahora
        val guardada = designaciones.save(d)
        adjuntos.vincular(constancia, guardada.id)

        when {
            estabaVigente -> historial.anotar(
                ctx, guardada, TipoEvento.CODIGOS_ACTUALIZADOS,
                if (hayCodigos) "Códigos secretos actualizados" else "Comunicación con la UIF actualizada",
                detalle = listOfNotNull(
                    if (hayCodigos) "Se guardaron códigos secretos nuevos (reservados)." else null,
                    if (constancia != null) "Se adjuntó la constancia de la UIF." else null
                ).joinToString(" ").ifEmpty { "Se actualizaron los datos de la comunicación." }
            )
            resultado == ResultadoUif.EN_EVALUACION -> historial.anotar(
                ctx, guardada, TipoEvento.COMUNICACION_PRESENTADA, "Comunicación presentada a la UIF",
                detalle = "Solicitud $numero presentada el ${TextosOc.fechaCorta(fechaCom)}."
            )
            resultado == ResultadoUif.PROCEDENTE -> historial.anotar(
                ctx, guardada, TipoEvento.DESIGNACION_PROCEDENTE,
                if (hayCodigos) "Designación procedente y códigos registrados" else "Designación procedente",
                detalle = "La UIF notificó que la designación es procedente el ${TextosOc.fechaCorta(fechaNot)}." +
                    (if (hayCodigos) " Se guardaron los códigos secretos." else "") + " Solicitud $numero.",
                etiqueta = "CERRADO"
            )
            else -> historial.anotar(
                ctx, guardada, TipoEvento.DESIGNACION_NO_PROCEDENTE, "Designación no procedente",
                detalle = "La UIF notificó que la designación no es procedente el ${TextosOc.fechaCorta(fechaNot)}. " +
                    "Solicitud $numero. Vuelve a estar pendiente de comunicar.",
                motivo = motivo
            )
        }
        return fichas.ficha(ctx, guardada)
    }
}
