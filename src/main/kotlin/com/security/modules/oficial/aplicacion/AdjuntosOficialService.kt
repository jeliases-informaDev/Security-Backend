package com.security.modules.oficial.aplicacion

import com.security.modules.denuncias.application.port.ArchivoStorageService
import com.security.modules.denuncias.application.service.ValidadorDocumentoDenunciaService
import com.security.modules.oficial.dominio.AmbitoAdjunto
import com.security.modules.oficial.dominio.enumDe
import com.security.modules.oficial.persistencia.OcAdjunto
import com.security.modules.oficial.persistencia.OcAdjuntoRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class ArchivoOc(val nombre: String, val tipoContenido: String, val contenido: ByteArray)

/**
 * Adjuntos del oficial: mismo almacenamiento (local u Oracle) y mismo validador que las denuncias (PDF, DOC, DOCX hasta
 * 10 MB, tipo por extension y por contenido). El archivo se guarda con nombre generado; el nombre original es solo un dato.
 */
@Service
class AdjuntosOficialService(
    private val repositorio: OcAdjuntoRepository,
    private val almacenamiento: ArchivoStorageService,
    private val validador: ValidadorDocumentoDenunciaService,
    private val reloj: RelojOficial
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun subir(ctx: ContextoOc, ambitoTexto: String?, archivo: MultipartFile?): AdjuntoResponse {
        ctx.exigirEscritura()
        val ambito = enumDe<AmbitoAdjunto>(ambitoTexto) ?: throw Oc.dato("ambito", "Indica para qué es el archivo.")
        if (archivo == null) throw OficialException(HttpStatus.BAD_REQUEST, Oc.ADJUNTO_INVALIDO, "Adjunta un archivo.")
        if (archivo.size > TAMANO_MAXIMO_ADJUNTO) {
            throw OficialException(HttpStatus.BAD_REQUEST, Oc.ADJUNTO_INVALIDO, "El documento supera el tamaño máximo permitido de 10 MB.")
        }
        val validado = try {
            validador.validar(archivo)
        } catch (e: ResponseStatusException) {
            throw OficialException(HttpStatus.BAD_REQUEST, Oc.ADJUNTO_INVALIDO, e.reason ?: "El documento no es válido.")
        }
        val uuid = UUID.randomUUID().toString()
        val carpeta = ctx.empresa.uuid ?: "empresa-${ctx.empresaId}"
        val ruta = "oficial/$carpeta/$uuid.${validado.extension}"
        val guardado = almacenamiento.guardar(ruta, validado.contenido, validado.tipoContenido)
        val adjunto = repositorio.save(
            OcAdjunto(
                uuid = uuid,
                empresaId = ctx.empresaId,
                ambito = ambito.name,
                nombreOriginal = validado.nombreOriginal,
                nombreArchivo = "$uuid.${validado.extension}",
                tipoContenido = validado.tipoContenido,
                rutaArchivo = guardado.rutaArchivo,
                tamanoBytes = validado.tamanoBytes,
                proveedorAlmacenamiento = guardado.proveedor.name,
                bucket = guardado.bucket,
                sha256 = validado.sha256,
                subidoPorId = ctx.actor.id,
                subidoPorUsuario = ctx.actor.usuario,
                subidoEn = reloj.ahora()
            )
        )
        return respuesta(adjunto)!!
    }

    @Transactional(readOnly = true)
    fun descargar(ctx: ContextoOc, id: String): ArchivoOc {
        val adjunto = repositorio.findByUuidAndEmpresaId(id, ctx.empresaId) ?: throw Oc.noEncontrado("No existe ese archivo.")
        val ambito = enumDe<AmbitoAdjunto>(adjunto.ambito)
        if (!ctx.actor.puedeEscribir && (ambito?.esDeclaracion == true || !adjunto.vinculado)) {
            throw Oc.sinPermiso("Tu rol no puede descargar este archivo.")
        }
        val archivo = try {
            almacenamiento.obtener(adjunto.rutaArchivo)
        } catch (e: Exception) {
            log.warn("No se pudo leer un adjunto del oficial de cumplimiento (id interno {}): {}", adjunto.id, e.javaClass.simpleName)
            throw Oc.noEncontrado("El archivo ya no está disponible.")
        }
        return ArchivoOc(adjunto.nombreOriginal, adjunto.tipoContenido ?: "application/octet-stream", archivo.contenido)
    }

    @Transactional
    fun borrar(ctx: ContextoOc, id: String) {
        ctx.exigirEscritura()
        val adjunto = repositorio.findByUuidAndEmpresaId(id, ctx.empresaId) ?: throw Oc.noEncontrado("No existe ese archivo.")
        if (adjunto.vinculado) {
            throw OficialException(HttpStatus.CONFLICT, Oc.ADJUNTO_EN_USO, "Ese archivo ya forma parte de un registro y no se puede borrar.")
        }
        repositorio.delete(adjunto)
        try {
            almacenamiento.eliminar(adjunto.rutaArchivo)
        } catch (e: Exception) {
            log.warn("No se pudo borrar del almacenamiento un adjunto suelto (id interno {}): {}", adjunto.id, e.javaClass.simpleName)
        }
    }

    /**
     * Resuelve un adjunto que manda el front para un campo: debe ser de la empresa, del ambito correcto y estar suelto
     * (o ya ligado a la misma designacion, si `actual` es su id). Deja el error en el validador y devuelve null si no vale.
     */
    fun resolver(
        ctx: ContextoOc, v: Validador, campo: String, id: String?, ambito: AmbitoAdjunto,
        obligatorio: Boolean, actual: Long? = null, faltaMensaje: String = "Adjunta el archivo."
    ): OcAdjunto? {
        val limpio = id?.trim()?.takeIf { it.isNotEmpty() }
        if (limpio == null) {
            if (obligatorio) v.error(campo, faltaMensaje)
            return null
        }
        val adjunto = repositorio.findByUuidAndEmpresaId(limpio, ctx.empresaId)
        if (adjunto == null) {
            v.error(campo, "No existe ese archivo.")
            return null
        }
        if (adjunto.ambito != ambito.name) {
            v.error(campo, "Ese archivo se subió para otra cosa: súbelo como «${ambito.nombre}».")
            return null
        }
        if (adjunto.vinculado && adjunto.id != actual) {
            throw OficialException(HttpStatus.CONFLICT, Oc.ADJUNTO_EN_USO, "Ese archivo ya forma parte de otro registro.")
        }
        return adjunto
    }

    fun vincular(adjunto: OcAdjunto?, designacionId: Long?) {
        if (adjunto == null) return
        adjunto.vinculado = true
        if (designacionId != null) adjunto.designacionId = designacionId
        repositorio.save(adjunto)
    }

    @Transactional(readOnly = true)
    fun porId(id: Long?, empresaId: Int): OcAdjunto? = id?.let { repositorio.findByIdAndEmpresaId(it, empresaId) }

    fun respuesta(adjunto: OcAdjunto?): AdjuntoResponse? = adjunto?.let {
        AdjuntoResponse(it.uuid, it.ambito, it.nombreOriginal, it.tipoContenido, it.tamanoBytes, it.subidoEn)
    }
}
