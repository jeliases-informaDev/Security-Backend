package com.security.modules.operaciones.application.service

import com.security.modules.denuncias.application.port.ArchivoStorageService
import com.security.modules.denuncias.application.service.ValidadorDocumentoDenunciaService
import com.security.modules.operaciones.domain.AdjuntoAnalisisEntity
import com.security.modules.operaciones.domain.AnalisisOperacionEntity
import com.security.modules.operaciones.domain.AnalisisOperacionEntity.Companion.DESCARTADA
import com.security.modules.operaciones.domain.AnalisisOperacionEntity.Companion.EN_ANALISIS
import com.security.modules.operaciones.domain.AnalisisOperacionEntity.Companion.SIN_ANALISIS
import com.security.modules.operaciones.domain.AnalisisOperacionEntity.Companion.SOSPECHOSA
import com.security.modules.operaciones.domain.EventoOperacionEntity
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.dto.*
import com.security.modules.operaciones.infraestructure.AdjuntoAnalisisRepository
import com.security.modules.operaciones.infraestructure.AnalisisOperacionRepository
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Un archivo leido del almacenamiento, listo para descargar. */
class ArchivoOperacion(val nombre: String, val tipo: String?, val contenido: ByteArray)

/**
 * Marcar como inusual (RO 12) y preparar el ROS (RO 13). PLAFT guarda la señal, el analisis, la decision, el plazo de 24 horas
 * desde que se califica como sospechosa y el numero de ROS; el envio es en ROSEL, fuera de PLAFT. Guardar el analisis lo puede
 * cualquiera de la empresa; calificar y registrar el ROS, solo el administrador (lo comprueba el controlador). Todo va al historial.
 * El acceso al registro (propia empresa) lo comprueba el controlador antes de llamar.
 */
@Service
class AnalisisOperacionService(
    private val operaciones: OperacionRepository,
    private val analisisRepository: AnalisisOperacionRepository,
    private val adjuntos: AdjuntoAnalisisRepository,
    private val catalogos: CatalogosRegistro,
    private val historial: HistorialOperacionService,
    private val validadorDocumento: ValidadorDocumentoDenunciaService,
    private val almacenamiento: ArchivoStorageService
) {

    private fun registro(id: Long): RegistroOperacionesEntity =
        operaciones.findById(id).orElse(null) ?: throw CodigosOperaciones.noEncontrada(id)

    @Transactional(readOnly = true)
    fun ver(id: Long, puedeCalificar: Boolean): InusualidadResponse {
        val r = registro(id)
        return respuesta(r, analisisRepository.findByRegistroId(id), catalogos.cargar(), puedeCalificar)
    }

    @Transactional
    fun guardar(id: Long, request: InusualidadRequest, autor: AutorEvento?, puedeCalificar: Boolean): InusualidadResponse {
        val r = registro(id)
        val cat = catalogos.cargar()
        val actual = analisisRepository.findByRegistroId(id)
        if (actual != null && actual.estado != EN_ANALISIS) throw cerrado()
        val errores = linkedMapOf<String, String>()
        fun texto(campo: String, v: String?, max: Int): String? {
            val t = v?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (t.length > max) errores[campo] = "Máximo $max caracteres."
            return t
        }
        val senal = cat.opcion(CatalogosOperacion.SENAL_ALERTA, request.senal)
        if (senal == null) errores["senal"] = if (request.senal.isNullOrBlank()) "Elige la señal de alerta." else "Elige una opción de la lista."
        val senalTexto = texto("senalTexto", request.senalTexto, 300)
        if (senal?.pideTexto == true && senalTexto == null) errores["senalTexto"] = "Describe la señal de alerta."
        val fuente = cat.opcion(CatalogosOperacion.FUENTE_SENAL, request.fuente)
        if (fuente == null) errores["fuente"] = if (request.fuente.isNullOrBlank()) "Elige la fuente de la señal." else "Elige una opción de la lista."
        val hoy = LocalDate.now(ZONA_LIMA)
        when {
            request.fechaDeteccion == null -> errores["fechaDeteccion"] = "Indica la fecha de detección."
            request.fechaDeteccion.isAfter(hoy) -> errores["fechaDeteccion"] = "La fecha de detección no puede ser futura."
        }
        val descripcionSenal = texto("descripcionSenal", request.descripcionSenal, 4000)
        val descripcionOperacion = texto("descripcionOperacion", request.descripcionOperacion, 4000)
        val analisis = texto("analisis", request.analisis, 4000)
        if (errores.isNotEmpty()) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.DATOS_INVALIDOS, errores.values.first(), errores.toMap())
        }
        val ahora = LocalDateTime.now(ZONA_LIMA).withNano(0)
        val a = actual ?: AnalisisOperacionEntity(registroId = id, estado = EN_ANALISIS)
        a.senal = senal!!.codigo
        a.senalTexto = if (senal.pideTexto) senalTexto else null
        a.fuente = fuente!!.codigo
        a.fechaDeteccion = request.fechaDeteccion
        a.descripcionSenal = descripcionSenal
        a.descripcionOperacion = descripcionOperacion
        a.analisis = analisis
        a.actualizadoEn = ahora
        a.actualizadoPor = autor?.nombreCompleto
        val guardado = analisisRepository.save(a)
        historial.anotar(
            id, EventoOperacionEntity.ANALISIS, "Análisis de inusualidad guardado",
            "Señal: ${senal.nombre} · Fuente: ${fuente.nombre}", autor, ahora
        )
        return respuesta(r, guardado, cat, puedeCalificar)
    }

    @Transactional
    fun decidir(id: Long, request: DecisionRequest, autor: AutorEvento?): InusualidadResponse {
        val r = registro(id)
        val a = analisisRepository.findByRegistroId(id)
            ?: throw CodigosOperaciones.conflicto(CodigosOperaciones.SIN_ANALISIS, "Primero guarda el análisis.")
        if (a.estado != EN_ANALISIS) throw cerrado()
        val decision = request.decision?.trim()?.uppercase()
        if (decision != NO_SOSPECHOSA && decision != SOSPECHOSA) {
            throw CodigosOperaciones.invalido("decision", "Elige si es sospechosa o no.")
        }
        if (a.analisis.isNullOrBlank()) {
            throw CodigosOperaciones.invalido("analisis", "Escribe el análisis y evaluación antes de decidir: es el sustento de la decisión.")
        }
        val ahora = LocalDateTime.now(ZONA_LIMA).withNano(0)
        a.decision = decision
        a.estado = if (decision == SOSPECHOSA) SOSPECHOSA else DESCARTADA
        a.calificadaEn = ahora
        a.calificadaPor = autor?.nombreCompleto
        a.venceEn = if (decision == SOSPECHOSA) ahora.plusHours(PLAZO_HORAS) else null
        a.actualizadoEn = ahora
        a.actualizadoPor = autor?.nombreCompleto
        analisisRepository.save(a)
        if (decision == SOSPECHOSA) {
            historial.anotar(id, EventoOperacionEntity.ANALISIS, "Calificada como sospechosa",
                "El ROS debe comunicarse a la UIF-Perú hasta el ${FotoOperacion.fecha(a.venceEn!!.toLocalDate())} " +
                    "${"%02d:%02d".format(a.venceEn!!.hour, a.venceEn!!.minute)}.", autor, ahora)
        } else {
            historial.anotar(id, EventoOperacionEntity.ANALISIS, "Descartada: no es sospechosa",
                "Se conserva el análisis y el motivo por el cual no se reporta.", autor, ahora)
        }
        return respuesta(r, a, catalogos.cargar(), true)
    }

    @Transactional(readOnly = true)
    fun ros(id: Long, puedeRegistrar: Boolean): RosResponse {
        val r = registro(id)
        val a = analisisRepository.findByRegistroId(id)?.takeIf { it.estado == SOSPECHOSA }
            ?: throw CodigosOperaciones.conflicto(CodigosOperaciones.SIN_ROS, "Este registro no está calificado como sospechoso.")
        return rosRespuesta(r, a, puedeRegistrar)
    }

    @Transactional
    fun registrarRos(id: Long, request: RosRequest, autor: AutorEvento?): RosResponse {
        val r = registro(id)
        val a = analisisRepository.findByRegistroId(id)?.takeIf { it.estado == SOSPECHOSA }
            ?: throw CodigosOperaciones.conflicto(CodigosOperaciones.SIN_ROS, "Este registro no está calificado como sospechoso.")
        if (a.numeroRos != null) throw CodigosOperaciones.conflicto(CodigosOperaciones.ROS_YA_REGISTRADO, "El número de ROS ya está registrado.")
        val errores = linkedMapOf<String, String>()
        val numero = request.numeroRos?.trim()?.takeIf { it.isNotEmpty() }
        when {
            numero == null -> errores["numeroRos"] = "Escribe el número de ROS."
            numero.length > 50 -> errores["numeroRos"] = "Máximo 50 caracteres."
        }
        val ahora = LocalDateTime.now(ZONA_LIMA).withNano(0)
        val enviado = request.enviadoEn?.withNano(0)
        when {
            enviado == null -> errores["enviadoEn"] = "Indica la fecha y hora de envío."
            enviado.isAfter(ahora.plusMinutes(1)) -> errores["enviadoEn"] = "La fecha de envío no puede ser futura."
            a.calificadaEn?.let { enviado.isBefore(it) } == true -> errores["enviadoEn"] = "La fecha de envío no puede ser anterior a la calificación."
        }
        if (errores.isNotEmpty()) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.DATOS_INVALIDOS, errores.values.first(), errores.toMap())
        }
        a.numeroRos = numero
        a.rosEnviadoEn = enviado
        a.rosRegistradoEn = ahora
        a.rosRegistradoPor = autor?.nombreCompleto
        analisisRepository.save(a)
        historial.anotar(id, EventoOperacionEntity.ANALISIS, "Número de ROS registrado",
            "ROS $numero enviado el ${FotoOperacion.fecha(enviado!!.toLocalDate())} ${"%02d:%02d".format(enviado.hour, enviado.minute)}.",
            autor, ahora)
        return rosRespuesta(r, a, true)
    }

    @Transactional
    fun adjuntar(id: Long, archivo: MultipartFile, autor: AutorEvento?): AdjuntoResponse {
        registro(id)
        val a = analisisRepository.findByRegistroId(id)
            ?: throw CodigosOperaciones.conflicto(CodigosOperaciones.SIN_ANALISIS, "Primero guarda el análisis.")
        if (a.estado != EN_ANALISIS) throw cerrado()
        val documento = validadorDocumento.validar(archivo)
        val ruta = "operaciones/analisis/$id/${UUID.randomUUID()}-${documento.nombreArchivo}"
        val guardado = almacenamiento.guardar(ruta, documento.contenido, documento.tipoContenido)
        return try {
            adjuntos.save(
                AdjuntoAnalisisEntity(
                    registroId = id, nombre = documento.nombreOriginal, tipoContenido = documento.tipoContenido,
                    tamanoBytes = documento.tamanoBytes, ruta = guardado.rutaArchivo, sha256 = documento.sha256,
                    subidoEn = LocalDateTime.now(ZONA_LIMA).withNano(0), subidoPor = autor?.nombreCompleto
                )
            ).respuesta()
        } catch (e: Exception) {
            runCatching { almacenamiento.eliminar(guardado.rutaArchivo) }
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun descargarAdjunto(id: Long, adjuntoId: Long): ArchivoOperacion {
        val adj = adjuntos.findByIdAndRegistroId(adjuntoId, id)
            ?: throw ResourceNotFoundException("No existe ese adjunto", CodigosOperaciones.ADJUNTO_NO_ENCONTRADO)
        val archivo = almacenamiento.obtener(adj.ruta)
        return ArchivoOperacion(adj.nombre, adj.tipoContenido ?: archivo.tipoContenido, archivo.contenido)
    }

    /** Resumen para el detalle del registro. */
    @Transactional(readOnly = true)
    fun resumen(id: Long, cat: Catalogos): InusualidadResumen {
        val a = analisisRepository.findByRegistroId(id)
        return InusualidadResumen(
            estado = a?.estado ?: SIN_ANALISIS,
            senalNombre = cat.nombre(CatalogosOperacion.SENAL_ALERTA, a?.senal),
            fechaDeteccion = a?.fechaDeteccion, calificadaEn = a?.calificadaEn, venceEn = a?.venceEn, numeroRos = a?.numeroRos
        )
    }

    // ---------- Respuestas ----------

    private fun cerrado() = CodigosOperaciones.conflicto(CodigosOperaciones.ANALISIS_CERRADO, "El análisis ya fue calificado y no se puede cambiar.")

    private fun AdjuntoAnalisisEntity.respuesta() = AdjuntoResponse(requireNotNull(id), nombre, tipoContenido, tamanoBytes, subidoEn, subidoPor)

    private fun respuesta(r: RegistroOperacionesEntity, a: AnalisisOperacionEntity?, cat: Catalogos, puedeCalificar: Boolean): InusualidadResponse {
        val id = requireNotNull(r.id)
        val o = r.operacion
        return InusualidadResponse(
            id = id, numeroRegistro = r.numeroRegistro.orEmpty(),
            estado = a?.estado ?: SIN_ANALISIS,
            senal = a?.senal, senalNombre = cat.nombre(CatalogosOperacion.SENAL_ALERTA, a?.senal), senalTexto = a?.senalTexto,
            fuente = a?.fuente, fuenteNombre = cat.nombre(CatalogosOperacion.FUENTE_SENAL, a?.fuente),
            fechaDeteccion = a?.fechaDeteccion, descripcionSenal = a?.descripcionSenal,
            descripcionOperacion = a?.descripcionOperacion, analisis = a?.analisis,
            decision = a?.decision, calificadaEn = a?.calificadaEn, calificadaPor = a?.calificadaPor, venceEn = a?.venceEn,
            adjuntos = adjuntos.findAllByRegistroIdOrderBySubidoEnAscIdAsc(id).map { it.respuesta() },
            datosOperacion = DatosOperacionAnalisis(
                fechaOperacion = o.fechaOperacion,
                tipoOperacion = cat.nombre(CatalogosOperacion.TIPO_OPERACION, o.tipoOperacion),
                monto = o.monto,
                moneda = cat.moneda(o.moneda)?.codigo ?: o.moneda,
                modalidadPago = cat.nombre(CatalogosOperacion.MODALIDAD_PAGO, o.modalidadPago),
                origenFondos = cat.nombre(CatalogosOperacion.ORIGEN_FONDOS, o.origenFondos),
                numeroComprobante = o.numeroComprobante,
                participantes = participantes(r)
            ),
            actualizadoEn = a?.actualizadoEn, actualizadoPor = a?.actualizadoPor,
            puedeCalificar = puedeCalificar
        )
    }

    private fun rosRespuesta(r: RegistroOperacionesEntity, a: AnalisisOperacionEntity, puedeRegistrar: Boolean): RosResponse {
        val ahora = LocalDateTime.now(ZONA_LIMA)
        val vence = a.venceEn
        val estadoPlazo = when {
            a.numeroRos != null -> "CERRADO"
            vence != null && ahora.isAfter(vence) -> "VENCIDO"
            else -> "EN_PLAZO"
        }
        val restante = if (estadoPlazo == "EN_PLAZO" && vence != null) Duration.between(ahora, vence).seconds.coerceAtLeast(0) else 0L
        val o = r.operacion
        val minimos = listOf(
            DatoMinimoResponse("DATOS_OPERACION", "Datos de la operación inusual",
                o.tipoOperacion.isNotBlank() && o.monto.signum() > 0 && o.moneda.isNotBlank() && o.modalidadPago.isNotBlank() && !o.origenFondos.isNullOrBlank()),
            DatoMinimoResponse("PERSONAS", "Personas que participan",
                !r.personaFisica.numeroDocumento.isNullOrBlank() || !r.personaFisica.motivoSinDocumento.isNullOrBlank()) ,
            DatoMinimoResponse("SENALES", "Señales de alerta identificadas", !a.senal.isNullOrBlank()),
            DatoMinimoResponse("ANALISIS_DECISION", "Análisis y decisión documentados", !a.analisis.isNullOrBlank() && a.decision != null)
        ).let { lista ->
            // Personas: la que realiza y al menos un beneficiario
            lista.map { if (it.codigo == "PERSONAS") it.copy(completo = it.completo && r.beneficiarios.isNotEmpty()) else it }
        }
        return RosResponse(
            id = requireNotNull(r.id), numeroRegistro = r.numeroRegistro.orEmpty(),
            calificadaEn = a.calificadaEn, calificadaPor = a.calificadaPor, venceEn = vence,
            restanteSegundos = restante, estadoPlazo = estadoPlazo,
            datosMinimos = minimos, datosListos = minimos.all { it.completo },
            numeroRos = a.numeroRos, enviadoEn = a.rosEnviadoEn, registradoEn = a.rosRegistradoEn, registradoPor = a.rosRegistradoPor,
            enviadoFueraDePlazo = if (a.numeroRos == null || a.rosEnviadoEn == null || vence == null) null else a.rosEnviadoEn!!.isAfter(vence),
            puedeRegistrar = puedeRegistrar
        )
    }

    companion object {
        const val NO_SOSPECHOSA = "NO_SOSPECHOSA"
        const val PLAZO_HORAS = 24L

        /** Personas distintas (por tipo y numero de documento; sin numero, por nombre) entre las que participan. */
        fun participantes(r: RegistroOperacionesEntity): Int {
            val claves = mutableSetOf<String>()
            fun agregar(tipo: String?, numero: String?, nombre: String?) {
                val n = numero?.trim()?.takeIf { it.isNotEmpty() }
                val clave = if (n != null) "${tipo?.trim()?.uppercase()}|$n" else nombre?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                if (clave != null) claves.add(clave)
            }
            val pf = r.personaFisica
            agregar(pf.tipoDocumento, pf.numeroDocumento, listOfNotNull(pf.nombres, pf.apellidos).joinToString(" "))
            r.personaRepresentada?.let { p ->
                agregar(p.tipoDocumento, p.numeroDocumento, p.razonSocial ?: listOfNotNull(p.nombres, p.apellidos).joinToString(" "))
            }
            r.beneficiarios.forEach { agregar(it.tipoDocumento, it.numeroDocumento, it.nombre) }
            return claves.size
        }
    }
}
