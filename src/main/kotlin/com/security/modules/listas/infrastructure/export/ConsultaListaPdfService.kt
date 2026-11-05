package com.security.modules.listas.infrastructure.export

import com.security.modules.listas.domain.reglas.Clasificacion
import com.security.modules.listas.domain.reglas.EstadoFila
import com.security.modules.listas.dto.ConsultaResponse
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Reporte PDF de una consulta a Listas Negativas: la persona, la clasificacion, el porcentaje, el detalle por lista,
 * los registros y noticias y la decision. Sale del resultado guardado en la consulta (no cambia si cambian las reglas).
 */
@Service
class ConsultaListaPdfService {

    private val azul = Color(27, 69, 137)
    private val gris = Color(108, 117, 125)
    private val negro = Color(33, 37, 41)
    private val rojo = Color(176, 0, 32)
    private val ambar = Color(166, 110, 0)
    private val verde = Color(30, 130, 60)
    private val borde = Color(222, 226, 230)

    private val negrita = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val normal = PDType1Font(Standard14Fonts.FontName.HELVETICA)

    private val margen = 40f
    private val ancho = PDRectangle.A4.width
    private val alto = PDRectangle.A4.height
    private val util = ancho - 2 * margen
    private val fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    private inner class Lienzo(val doc: PDDocument) {
        var pagina = PDPage(PDRectangle.A4).also { doc.addPage(it) }
        var stream = PDPageContentStream(doc, pagina)
        var y = alto - margen

        fun espacio(necesario: Float) {
            if (y - necesario < 50f) {
                stream.close()
                pagina = PDPage(PDRectangle.A4).also { doc.addPage(it) }
                stream = PDPageContentStream(doc, pagina)
                y = alto - margen
            }
        }

        fun texto(t: String, tamano: Float = 9f, fuente: PDType1Font = normal, color: Color = negro, sangria: Float = 0f) {
            val lineas = partir(t, util - sangria, tamano, fuente)
            lineas.forEach { l ->
                espacio(tamano + 4f)
                stream.beginText()
                stream.setFont(fuente, tamano)
                stream.setNonStrokingColor(color)
                stream.newLineAtOffset(margen + sangria, y - tamano)
                stream.showText(l)
                stream.endText()
                y -= tamano + 4f
            }
        }

        fun titulo(t: String) {
            espacio(30f)
            y -= 8f
            stream.setNonStrokingColor(azul)
            stream.addRect(margen, y - 16f, util, 16f)
            stream.fill()
            stream.beginText()
            stream.setFont(negrita, 9.5f)
            stream.setNonStrokingColor(Color.WHITE)
            stream.newLineAtOffset(margen + 6f, y - 11.5f)
            stream.showText(limpiar(t))
            stream.endText()
            y -= 22f
        }

        fun linea() {
            espacio(6f)
            stream.setStrokingColor(borde)
            stream.setLineWidth(0.6f)
            stream.moveTo(margen, y - 2f)
            stream.lineTo(ancho - margen, y - 2f)
            stream.stroke()
            y -= 6f
        }

        fun cerrar() = stream.close()
    }

    fun generar(c: ConsultaResponse, usuario: String): ByteArray {
        return PDDocument().use { doc ->
            val l = Lienzo(doc)
            val logo = try {
                ClassPathResource("static/images/Informa_Peru_Logo_Completo_Color_PNG_transparente.png").inputStream.use { it.readBytes() }
                    .let { PDImageXObject.createFromByteArray(doc, it, "logo") }
            } catch (_: Exception) {
                null
            }
            if (logo != null) {
                val escala = minOf(110f / logo.width, 34f / logo.height)
                l.stream.drawImage(logo, margen, l.y - logo.height * escala, logo.width * escala, logo.height * escala)
                l.y -= logo.height * escala + 8f
            }
            val r = c.resultado
            l.texto("REPORTE DE CONSULTA EN LISTAS NEGATIVAS", 13f, negrita, azul)
            l.texto("Consulta N.° ${c.id} · ${c.fecha.format(fecha)} · Hecha por ${c.usuario.nombre} · Consulta como ${c.tipoConsultaNombre}", 8.5f, color = gris)
            l.texto("Emitido el ${LocalDateTime.now().format(fecha)} por $usuario", 8.5f, color = gris)

            l.titulo("PERSONA CONSULTADA")
            l.texto(c.consultado.nombre ?: c.criterio.texto ?: "—", 11f, negrita)
            l.texto(
                listOfNotNull(
                    listOfNotNull(c.consultado.tipoDocumento, c.consultado.documento).joinToString(" ").ifBlank { null },
                    c.consultado.tipoPersona?.let { if (it == "JURIDICA") "PERSONA JURÍDICA" else "PERSONA NATURAL" },
                    c.consultado.pais,
                    if (r.esPep) "PEP" else null
                ).joinToString(" · ").ifBlank { "—" }, 9f, color = gris
            )
            l.texto("${c.porQueCoincide.titulo}${c.porQueCoincide.detalle?.let { ". $it" } ?: ""}", 9f)
            c.porQueCoincide.advertencia?.let { l.texto(it, 9f, negrita, ambar) }

            l.titulo("RESULTADO")
            val color = when (r.clasificacion) {
                Clasificacion.CRITICA -> rojo
                Clasificacion.MEDIA -> ambar
                else -> verde
            }
            l.texto("${r.clasificacionNombre} · ${r.porcentaje}% de coincidencia", 12f, negrita, color)
            l.texto("${r.listasConCoincidencia} de ${r.listasConsultadas} listas con coincidencia · ${r.registrosEnListas} registros en listas · ${r.noticiasEnMedios} noticias en medios", 9f)
            l.texto(r.aviso.titulo + if (r.aviso.fuentes.isNotEmpty()) ": " + r.aviso.fuentes.joinToString(" · ") else "", 9f, negrita)
            r.aviso.suma?.let { l.texto(it, 9f) }
            l.texto(r.aviso.texto, 9f)
            l.texto("Pesos: ${r.leyenda}. El porcentaje es la suma de los pesos de las listas con al menos un registro (tope 100%).", 8f, color = gris)
            if (c.homonimo != null) {
                l.texto(
                    "Descartada como homónimo el ${c.homonimo.fecha?.format(fecha) ?: "—"} por ${c.homonimo.usuario?.nombre ?: "—"}: ${c.homonimo.comentario ?: ""}",
                    9f, negrita, azul
                )
            }

            l.titulo("DETALLE POR LISTA")
            r.filas.forEach { f ->
                val estado = when (f.estado) {
                    EstadoFila.COINCIDENCIA -> f.etiquetaConteo
                    EstadoFila.SIN_COINCIDENCIAS -> "SIN COINCIDENCIAS"
                    else -> "PRONTO"
                }
                val act = f.actualizada?.let { " · act. ${it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}" } ?: ""
                val colorFila = when (f.estado) {
                    EstadoFila.COINCIDENCIA -> rojo
                    EstadoFila.SIN_COINCIDENCIAS -> verde
                    else -> gris
                }
                l.texto("${if (f.otraLista) "Otras listas · " else ""}${f.nombre} · ${f.peso}%$act — $estado", 9f, negrita, colorFila)
                if (f.estado == EstadoFila.PRONTO && f.motivo != null) l.texto(f.motivo, 8f, color = gris, sangria = 10f)
            }

            val conRegistros = r.filas.filter { it.items.isNotEmpty() }
            if (conRegistros.isNotEmpty()) {
                l.titulo("REGISTROS Y NOTICIAS")
                conRegistros.forEach { f ->
                    l.texto("${f.nombre} · ${f.etiquetaConteo}", 9.5f, negrita, azul)
                    f.items.forEach { i ->
                        val cabeza = listOfNotNull(
                            i.lista,
                            i.cargo?.takeIf { it.isNotBlank() }?.let { cargo -> listOfNotNull(cargo, i.institucion).joinToString(" — ") }
                        ).joinToString(" · ")
                        l.texto(cabeza, 8.5f, negrita, sangria = 8f)
                        i.descripcion?.takeIf { it.isNotBlank() }?.let { l.texto(it, 8.5f, sangria = 8f) }
                        val fechas = listOfNotNull(
                            i.fechaRegistro?.let { "Fecha: $it" },
                            i.fechaHasta?.let { "Hasta: $it" },
                            if (i.esPep) (if (i.sinPeriodo) "Sin período" else "Período: ${i.periodoDesde ?: "—"} a ${i.periodoHasta ?: "—"}") else null,
                            i.link?.let { "Enlace: $it" }
                        ).joinToString(" · ")
                        if (fechas.isNotBlank()) l.texto(fechas, 8f, color = gris, sangria = 8f)
                        l.linea()
                    }
                    f.avisos.forEach { l.texto(it, 8f, negrita, ambar, sangria = 8f) }
                }
                if (r.noticiasEnMedios > 0) l.texto(c.pieNoticias, 8f, color = gris)
            }

            if (c.decisiones.isNotEmpty()) {
                l.titulo("REGISTRO DE DECISIONES")
                c.decisiones.forEach { d ->
                    val accion = if (d.accion == "HOMONIMO") "Marcó «Es homónimo»" else "Deshizo «Es homónimo»"
                    l.texto("${d.fecha.format(fecha)} · ${d.usuario.nombre} · $accion${d.comentario?.let { ": $it" } ?: ""}", 8.5f)
                }
            }

            l.y -= 8f
            l.texto("La información refleja los registros de la base de listas al momento de la consulta. Documento confidencial.", 7.5f, color = gris)
            l.cerrar()

            val total = doc.numberOfPages
            doc.pages.forEachIndexed { i, p ->
                PDPageContentStream(doc, p, PDPageContentStream.AppendMode.APPEND, true).use { s ->
                    val t = limpiar("PLAFT · Listas Negativas · Consulta ${c.id} · Página ${i + 1} de $total")
                    s.beginText()
                    s.setFont(normal, 7.5f)
                    s.setNonStrokingColor(gris)
                    s.newLineAtOffset(margen, 24f)
                    s.showText(t)
                    s.endText()
                }
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            out.toByteArray()
        }
    }

    private fun partir(texto: String, anchoMax: Float, tamano: Float, fuente: PDType1Font): List<String> {
        val lineas = mutableListOf<String>()
        var actual = ""
        limpiar(texto).split(" ").filter { it.isNotEmpty() }.forEach { palabra ->
            var p = palabra
            // Una palabra mas ancha que la linea (un enlace largo) se corta en trozos.
            while (medir(p, tamano, fuente) > anchoMax && p.length > 1) {
                if (actual.isNotEmpty()) { lineas.add(actual); actual = "" }
                var corte = p.length
                while (corte > 1 && medir(p.substring(0, corte), tamano, fuente) > anchoMax) corte--
                lineas.add(p.substring(0, corte))
                p = p.substring(corte)
            }
            val candidato = if (actual.isEmpty()) p else "$actual $p"
            if (medir(candidato, tamano, fuente) > anchoMax && actual.isNotEmpty()) {
                lineas.add(actual)
                actual = p
            } else {
                actual = candidato
            }
        }
        if (actual.isNotEmpty()) lineas.add(actual)
        return lineas.ifEmpty { listOf("-") }
    }

    private fun medir(t: String, tamano: Float, fuente: PDType1Font) = fuente.getStringWidth(t) / 1000f * tamano

    /** Las fuentes estandar del PDF solo tienen letras basicas (WinAnsi): se quitan tildes y simbolos raros. */
    private fun limpiar(t: String): String {
        val sinSaltos = t.replace(Regex("[\\r\\n\\t]+"), " ")
            .replace("·", "-").replace("—", "-").replace("«", "\"").replace("»", "\"").replace("°", "o")
        return Normalizer.normalize(sinSaltos, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .filter { it.code in 32..126 }
    }
}
