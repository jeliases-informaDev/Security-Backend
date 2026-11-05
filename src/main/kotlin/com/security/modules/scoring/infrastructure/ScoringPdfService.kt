package com.security.modules.scoring.infrastructure

import com.security.modules.scoring.dto.CoincidenciaListas
import com.security.modules.scoring.dto.ResultadoScoringResponse
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
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * PDF de una evaluacion de scoring, con el mismo contenido que la pantalla de resultado: cliente, nivel y puntaje,
 * factores por grupo, reglas activadas, consulta a listas, que hacer ahora y la decision si existe.
 */
@Service
class ScoringPdfService {

    private val azul = Color(27, 69, 137)
    private val gris = Color(108, 117, 125)
    private val grisFondo = Color(245, 247, 250)
    private val grisBorde = Color(222, 226, 230)
    private val negro = Color(33, 37, 41)
    private val colorNivel = mapOf("BAJO" to Color(40, 167, 69), "MEDIO" to Color(214, 140, 0), "ALTO" to Color(220, 53, 69))
    private val etiquetaNivel = mapOf("BAJO" to "Bajo", "MEDIO" to "Medio", "ALTO" to "Alto")

    private val negrita = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val normal = PDType1Font(Standard14Fonts.FontName.HELVETICA)

    private val margen = 40f
    private val anchoPagina = PDRectangle.A4.width
    private val altoPagina = PDRectangle.A4.height
    private val anchoUtil = anchoPagina - 2 * margen
    private val fechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val fechaCorta = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun generar(r: ResultadoScoringResponse, username: String): ByteArray {
        PDDocument().use { doc ->
            val lienzo = Lienzo(doc)
            val logo = try {
                ClassPathResource("static/images/Informa_Peru_Logo_Completo_Color_PNG_transparente.png").inputStream.use { it.readBytes() }
                    .let { PDImageXObject.createFromByteArray(doc, it, "logo") }
            } catch (_: Exception) {
                null
            }

            // Cabecera
            logo?.let {
                val alto = 28f
                val ancho = it.width * alto / it.height
                lienzo.s.drawImage(it, margen, lienzo.y - alto, ancho, alto)
            }
            lienzo.texto("Scoring de riesgo LA/FT", negrita, 16f, azul, x = anchoPagina - margen - ancho("Scoring de riesgo LA/FT", negrita, 16f))
            lienzo.y -= 14f
            val emision = "Emitido el ${LocalDateTime.now().format(fechaHora)} por $username"
            lienzo.texto(emision, normal, 8f, gris, x = anchoPagina - margen - ancho(emision, normal, 8f))
            lienzo.y -= 26f

            // Cliente
            lienzo.titulo("Cliente")
            val c = r.cliente
            lienzo.dato("Nombre", c.nombre ?: "Sin dato")
            lienzo.dato("Documento", listOfNotNull(c.tipoDocumento, c.documento).joinToString(" ").ifEmpty { "Sin dato" })
            lienzo.dato("Tipo de persona", when (c.tipoPersona) { "NATURAL" -> "Natural"; "JURIDICA" -> "Jurídica"; else -> c.tipoPersona ?: "Sin dato" })
            c.ocupacion?.let { lienzo.dato("Ocupación o actividad", it) }
            c.departamento?.let { lienzo.dato("Departamento", it) }
            lienzo.dato("Fecha de la evaluación", r.fecha.format(fechaHora))
            lienzo.dato("Metodología", "Versión ${r.version}")
            lienzo.y -= 8f

            // Nivel y puntaje
            lienzo.titulo("Resultado")
            val nivel = r.nivel
            if (nivel != null) {
                lienzo.saltoSiFalta(40f)
                val color = colorNivel[nivel] ?: negro
                lienzo.s.setNonStrokingColor(color)
                lienzo.s.addRect(margen, lienzo.y - 26f, 120f, 26f)
                lienzo.s.fill()
                lienzo.texto("Riesgo ${etiquetaNivel[nivel] ?: nivel}", negrita, 13f, Color.WHITE, x = margen + 10f, base = lienzo.y - 17f)
                lienzo.texto("Puntaje ${num(r.puntaje)} de 4", negrita, 13f, negro, x = margen + 135f, base = lienzo.y - 17f)
                lienzo.y -= 36f
                r.nivelPorPromedio?.let { lienzo.parrafo("Nivel por el promedio: ${etiquetaNivel[it] ?: it}.", normal, 9f, gris) }
                r.avisoRegla?.let {
                    lienzo.parrafo(it.titulo, negrita, 10f, negro)
                    lienzo.parrafo(it.detalle, normal, 9f, negro)
                }
            } else {
                lienzo.parrafo("Puntaje ${num(r.puntaje)}" + (r.categoria?.let { ". Categoría de entonces: $it" } ?: "") + ".", negrita, 11f, negro)
                lienzo.parrafo("Evaluación hecha con la metodología anterior (versión ${r.version}).", normal, 9f, gris)
            }
            lienzo.y -= 6f

            // Factores por grupo
            if (r.grupos.isNotEmpty()) {
                lienzo.titulo("Por qué salió este nivel")
                val columnas = floatArrayOf(150f, 200f, 50f, 45f, 70f)
                lienzo.fila(listOf("Factor", "Dato", "Puntaje", "Peso", "Aporte"), columnas, negrita, fondo = grisFondo)
                r.grupos.forEach { g ->
                    lienzo.fila(listOf("${g.nombre} (${g.peso}%)", "", "", "", "Promedio ${num(g.promedio)}"), columnas, negrita, fondo = grisFondo)
                    g.factores.forEach { f ->
                        val nombre = if (f.automatico) "${f.nombre} (automático)" else f.nombre
                        lienzo.fila(listOf(nombre, f.dato ?: "Sin dato", f.puntaje?.toString() ?: "—", "${f.peso}%", num(f.aporte)), columnas, normal)
                    }
                }
                r.montoNota?.let { lienzo.y -= 4f; lienzo.parrafo(it, normal, 8f, gris) }
                lienzo.y -= 8f
            }

            // Reglas
            if (r.version >= 2) {
                lienzo.titulo("Reglas activadas")
                if (r.reglas.isEmpty()) lienzo.parrafo("Ninguna regla fijó el nivel.", normal, 9f, negro)
                r.reglas.forEach { lienzo.parrafo("• ${it.titulo}: ${it.detalle}", normal, 9f, negro) }
                lienzo.y -= 6f
            }

            // Consulta a listas
            r.consulta?.let { q ->
                lienzo.titulo("Consulta a Listas Negativas (${q.fecha.format(fechaHora)})")
                lienzo.dato("PEP", coincidencias(q.pep))
                lienzo.dato("Parientes y empresas vinculadas a un PEP", coincidencias(q.vinculados))
                lienzo.dato("Sanciones y listas LA/FT", coincidencias(q.sanciones))
                lienzo.dato("Noticias y medios", coincidencias(q.noticias))
                q.homonimo?.let { lienzo.dato("Homónimo", it.texto) }
                lienzo.y -= 6f
            }

            // Que hacer ahora
            r.queHacer?.let { q ->
                lienzo.titulo("Qué hacer ahora")
                lienzo.dato("Debida diligencia", q.debidaDiligencia)
                lienzo.dato("Quién decide", q.decide)
                lienzo.dato("Revisión del perfil", "cada ${q.revisionMeses} meses" + (q.proximaRevision?.let { " (próxima: ${it.format(fechaCorta)})" } ?: ""))
                if (q.reunir.isNotEmpty()) {
                    lienzo.parrafo("Antes de aprobar, reunir:", negrita, 9f, negro)
                    q.reunir.forEach { lienzo.parrafo("• $it", normal, 9f, negro) }
                }
                lienzo.y -= 6f
            }

            // Decision
            lienzo.titulo("Decisión del analista")
            val d = r.decision
            if (d == null) {
                lienzo.parrafo("Todavía sin decisión.", normal, 9f, gris)
            } else {
                val accion = when (d.accion) {
                    "APROBAR" -> "Aprobada"
                    "ENVIAR_A_APROBACION" -> "Enviada a aprobación"
                    "RECHAZAR" -> "Rechazada"
                    else -> d.accion
                }
                lienzo.dato("Decisión", accion)
                lienzo.dato("Por", listOfNotNull(d.por, d.fecha?.format(fechaHora)).joinToString(" · "))
                d.comentario?.let { lienzo.parrafo("Comentario: $it", normal, 9f, negro) }
            }

            lienzo.cerrar()
            val salida = ByteArrayOutputStream()
            doc.save(salida)
            return salida.toByteArray()
        }
    }

    private fun coincidencias(c: CoincidenciaListas): String = when {
        !c.revisado -> "Sin dato (el sistema aún no puede comprobarlo)"
        c.coincidencias == 0 -> "Sin coincidencias"
        c.coincidencias == 1 -> "1 coincidencia"
        else -> "${c.coincidencias} coincidencias"
    }

    private fun num(v: BigDecimal?): String = v?.setScale(2, java.math.RoundingMode.HALF_UP)?.toPlainString() ?: "—"

    private fun ancho(texto: String, fuente: PDType1Font, tamano: Float): Float =
        fuente.getStringWidth(seguro(texto, fuente)) / 1000f * tamano

    /** Las fuentes estandar solo tienen WinAnsi: lo que no se puede escribir se reemplaza. */
    private fun seguro(texto: String, fuente: PDType1Font): String = buildString {
        texto.forEach { ch ->
            val s = ch.toString()
            val ok = try {
                fuente.encode(s); true
            } catch (_: Exception) {
                false
            }
            append(if (ok) s else when (ch) { '≥' -> ">="; '≤' -> "<="; '\n', '\t' -> " "; else -> "?" })
        }
    }

    /** Pagina actual con su cursor vertical y salto de pagina automatico. */
    private inner class Lienzo(private val doc: PDDocument) {
        var s: PDPageContentStream
        var y: Float = altoPagina - margen

        init {
            val pagina = PDPage(PDRectangle.A4)
            doc.addPage(pagina)
            s = PDPageContentStream(doc, pagina)
        }

        fun saltoSiFalta(alto: Float) {
            if (y - alto < margen) {
                s.close()
                val pagina = PDPage(PDRectangle.A4)
                doc.addPage(pagina)
                s = PDPageContentStream(doc, pagina)
                y = altoPagina - margen
            }
        }

        fun cerrar() = s.close()

        fun texto(t: String, fuente: PDType1Font, tamano: Float, color: Color, x: Float = margen, base: Float = y - tamano) {
            s.beginText()
            s.setFont(fuente, tamano)
            s.setNonStrokingColor(color)
            s.newLineAtOffset(x, base)
            s.showText(seguro(t, fuente))
            s.endText()
        }

        fun titulo(t: String) {
            saltoSiFalta(40f)
            texto(t.uppercase(), negrita, 10f, azul)
            y -= 14f
            s.setStrokingColor(grisBorde)
            s.moveTo(margen, y)
            s.lineTo(anchoPagina - margen, y)
            s.stroke()
            y -= 8f
        }

        fun dato(etiqueta: String, valor: String) {
            val lineas = partir(valor, normal, 9f, anchoUtil - 170f)
            saltoSiFalta(12f * lineas.size + 2f)
            texto(etiqueta, negrita, 9f, gris)
            lineas.forEachIndexed { i, l -> texto(l, normal, 9f, negro, x = margen + 170f, base = y - 9f - i * 12f) }
            y -= 12f * lineas.size + 2f
        }

        fun parrafo(t: String, fuente: PDType1Font, tamano: Float, color: Color) {
            partir(t, fuente, tamano, anchoUtil).forEach { l ->
                saltoSiFalta(tamano + 4f)
                texto(l, fuente, tamano, color)
                y -= tamano + 4f
            }
        }

        fun fila(celdas: List<String>, anchos: FloatArray, fuente: PDType1Font, fondo: Color? = null) {
            val tamano = 8f
            val partidas = celdas.mapIndexed { i, c -> partir(c, fuente, tamano, anchos[i] - 6f) }
            val alto = partidas.maxOf { it.size } * 10f + 6f
            saltoSiFalta(alto)
            if (fondo != null) {
                s.setNonStrokingColor(fondo)
                s.addRect(margen, y - alto, anchos.sum(), alto)
                s.fill()
            }
            var x = margen
            partidas.forEachIndexed { i, lineas ->
                lineas.forEachIndexed { j, l -> texto(l, fuente, tamano, negro, x = x + 3f, base = y - 11f - j * 10f) }
                x += anchos[i]
            }
            s.setStrokingColor(grisBorde)
            s.moveTo(margen, y - alto)
            s.lineTo(margen + anchos.sum(), y - alto)
            s.stroke()
            y -= alto
        }

        private fun partir(t: String, fuente: PDType1Font, tamano: Float, maximo: Float): List<String> {
            if (t.isEmpty()) return listOf("")
            val lineas = mutableListOf<String>()
            var actual = ""
            t.split(" ").forEach { palabra ->
                val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
                if (ancho(prueba, fuente, tamano) <= maximo || actual.isEmpty()) {
                    actual = prueba
                } else {
                    lineas.add(actual)
                    actual = palabra
                }
            }
            if (actual.isNotEmpty()) lineas.add(actual)
            return lineas
        }
    }
}
