package com.security.modules.operaciones.infrastructure.adapter.out.pdf

import com.security.modules.operaciones.application.service.CatalogosOperacion
import com.security.modules.operaciones.application.service.FotoOperacion
import com.security.modules.operaciones.domain.model.*
import com.security.modules.operaciones.domain.port.out.GeneradorPdfPort
import com.security.modules.operaciones.domain.port.out.PiePdf
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.util.Matrix
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

/**
 * PDF del registro con el diseño nuevo (RO 9): una sola pagina con la persona que realiza, la representada, la operacion
 * y los beneficiarios, y el pie con quien lo genero, el codigo de verificacion y la huella SHA-256 del contenido.
 * Primero se mide y, si no cabe, todo se reduce en la misma proporcion (nunca se cortan filas ni valores).
 */
@Component
class PdfBoxGeneradorPdfAdapter : GeneradorPdfPort {

    override fun generar(operacion: OperacionExportacion, pie: PiePdf?): ByteArray {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val margen = 40f
            val ancho = page.mediaBox.width - 2 * margen
            val disponible = page.mediaBox.height - 2 * margen
            val alto = Hoja(null, ancho).dibujar(operacion, pie)
            val escala = minOf(1f, disponible / alto)
            PDPageContentStream(document, page).use { stream ->
                stream.saveGraphicsState()
                stream.transform(Matrix(escala, 0f, 0f, escala, margen + (ancho - ancho * escala) / 2f, page.mediaBox.height - margen))
                Hoja(stream, ancho).dibujar(operacion, pie)
                stream.restoreGraphicsState()
            }
            return ByteArrayOutputStream().use { salida ->
                document.save(salida)
                salida.toByteArray()
            }
        }
    }
}

private data class Dato(val etiqueta: String, val valor: String?)

/** Dibuja (o solo mide, si stream es null) con el origen en la esquina superior izquierda y `top` hacia abajo. */
private class Hoja(private val stream: PDPageContentStream?, private val ancho: Float) {
    private val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val negrita = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val mono = PDType1Font(Standard14Fonts.FontName.COURIER)

    private val tinta = Color(23, 23, 28)
    private val gris = Color(110, 112, 122)
    private val lila = Color(108, 76, 230)
    private val linea = Color(225, 226, 232)

    private val pad = 24f
    private val interior get() = ancho - 2 * pad
    private val fechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun dibujar(o: OperacionExportacion, pie: PiePdf?): Float {
        var top = pad
        // Cabecera
        if (stream != null) {
            redondeado(pad, top, 18f, 18f, 4f, lila)
            stream.setStrokingColor(Color.WHITE)
            stream.setLineWidth(1.6f)
            stream.moveTo(pad + 5f, -(top + 9.5f))
            stream.lineTo(pad + 8f, -(top + 12.5f))
            stream.lineTo(pad + 13.5f, -(top + 6f))
            stream.stroke()
        }
        texto("PLAFT · Registro de operaciones", pad + 26f, top + 1f, 11f, negrita, tinta)
        val empresa = o.registro.empresa.trim().uppercase()
        texto("$empresa · DOCUMENTO CONFIDENCIAL", pad + 26f, top + 14f, 6.5f, mono, gris, ancho - 2 * pad - 26f - 110f)
        textoDerecha(o.codigoOperacion, ancho - pad, top + 4f, 9f, mono, tinta)
        top += 32f
        raya(top)
        top += 14f

        // Persona que realiza
        val pf = o.personaFisica
        top = seccion("Persona que realiza", top)
        top = rejilla(listOf(
            Dato("Nombre", nombre(pf)),
            Dato("Documento", documento(pf)),
            Dato("Nacionalidad", pf.nacionalidad),
            Dato("Ocupación", pf.ocupacion),
            Dato("Teléfono", pf.telefono),
            Dato("Domicilio", pf.domicilio)
        ), top)

        // Persona representada
        o.personaRepresentada?.let { pr ->
            top = seccion("Persona representada", top)
            val datos = if (pr.tipoPersona == CatalogosOperacion.JURIDICA) listOf(
                Dato("Razón social", pr.razonSocial),
                Dato(etiquetaDocumento(pr), pr.numeroDocumento.ifBlank { pr.motivoSinDocumento }),
                Dato("Representante", pr.representanteLegal)
            ) else listOf(
                Dato("Nombre", nombre(pr)),
                Dato("Documento", documento(pr))
            )
            top = rejilla(datos, top)
        }

        // Operacion
        val op = o.operacion
        top = seccion("Operación", top)
        val modalidad = listOfNotNull(op.modalidadPagoNombre ?: op.modalidadPago, op.modalidadPagoTexto).joinToString(" · ")
        val origen = listOfNotNull(op.origenFondosNombre ?: op.origenFondos, op.origenFondosTexto).joinToString(" · ").ifBlank { null }
        top = rejilla(listOf(
            Dato("Fecha", FotoOperacion.fecha(op.fechaOperacion)),
            Dato("Tipo", op.tipoOperacionNombre ?: op.tipoOperacion),
            Dato("Modalidad", modalidad),
            Dato("Moneda y monto", "${op.monedaCodigo ?: op.moneda} ${FotoOperacion.monto(op.monto)}"),
            Dato("Origen de fondos", origen),
            Dato("Comprobante", op.numeroComprobante)
        ), top)

        // Beneficiarios
        top = seccion("Beneficiarios", top)
        top = beneficiarios(o.beneficiarios, top)

        // Pie
        raya(top)
        top += 10f
        val generado = pie?.let { "Generado el ${it.generadoEn.format(fechaHora)} por ${it.usuario} · Oficina ${o.registro.oficina}" }
            ?: "Oficina ${o.registro.oficina}"
        top = parrafo(generado, pad, top, interior, 6.5f, mono, gris, 9f)
        if (pie != null) {
            top = parrafo("Código de verificación ${pie.codigoVerificacion} · SHA-256 ${pie.huella}", pad, top + 2f, interior, 6.5f, mono, gris, 9f)
        }
        val alto = top + pad
        if (stream != null) {
            stream.setStrokingColor(linea)
            stream.setLineWidth(0.8f)
            stream.addRect(0f, -alto, ancho, alto)
            stream.stroke()
        }
        return alto
    }

    // ---------- Bloques ----------

    private fun seccion(titulo: String, top: Float): Float {
        texto(titulo.uppercase(), pad, top, 7f, mono, lila)
        return top + 16f
    }

    private fun rejilla(datos: List<Dato>, top: Float): Float {
        val columnas = 3
        val separacion = 16f
        val w = (interior - (columnas - 1) * separacion) / columnas
        var y = top
        datos.chunked(columnas).forEach { fila ->
            val alturas = fila.map { lineas(it.valor, w, 9f, regular).size }
            fila.forEachIndexed { i, dato ->
                val x = pad + i * (w + separacion)
                texto(dato.etiqueta, x, y, 7f, regular, gris)
                lineas(dato.valor, w, 9f, regular).forEachIndexed { j, l -> texto(l, x, y + 10f + j * 11f, 9f, regular, tinta) }
            }
            y += 10f + alturas.max() * 11f + 8f
        }
        return y + 4f
    }

    private fun beneficiarios(lista: List<BeneficiarioExportacion>, top: Float): Float {
        if (lista.isEmpty()) {
            texto("Sin beneficiarios registrados", pad, top, 9f, regular, gris)
            return top + 20f
        }
        val separacion = 16f
        val w = (interior - separacion) / 2
        var y = top
        lista.withIndex().chunked(2).forEach { fila ->
            val alturas = fila.map { lineas(it.value.nombre, w, 9f, regular).size }
            fila.forEachIndexed { i, (indice, b) ->
                val x = pad + i * (w + separacion)
                texto("${indice + 1}", x, y, 7f, regular, gris)
                val ls = lineas(b.nombre, w, 9f, regular)
                ls.forEachIndexed { j, l -> texto(l, x, y + 10f + j * 11f, 9f, regular, tinta) }
                val doc = listOf(b.tipoDocumento, b.numeroDocumento).filter { it.isNotBlank() }.joinToString(" ")
                if (doc.isNotBlank()) texto(doc, x, y + 10f + ls.size * 11f + 1f, 6.5f, mono, gris, w)
            }
            y += 10f + alturas.max() * 11f + 14f
        }
        return y + 4f
    }

    private fun nombre(p: PersonaExportacion): String? =
        listOf(p.apellidos, p.nombres).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ").ifBlank { null }

    private fun documento(p: PersonaExportacion): String? =
        if (p.tipoDocumento.equals(CatalogosOperacion.SIN_DOCUMENTO, ignoreCase = true)) {
            listOfNotNull("SIN DOCUMENTO", p.motivoSinDocumento).joinToString(" · ")
        } else listOf(p.tipoDocumento, p.numeroDocumento).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }

    private fun etiquetaDocumento(p: PersonaExportacion): String =
        if (p.tipoDocumento.equals(CatalogosOperacion.SIN_DOCUMENTO, ignoreCase = true)) "Sin documento"
        else p.tipoDocumento.ifBlank { "Documento" }

    // ---------- Texto y formas ----------

    private fun limpio(valor: String, fuente: PDType1Font): String = buildString {
        valor.replace("\r", " ").replace("\n", " ").replace("\t", " ").codePoints().forEach { cp ->
            val s = String(Character.toChars(cp))
            append(try { fuente.encode(s); s } catch (_: IllegalArgumentException) { "?" })
        }
    }

    private fun anchoTexto(valor: String, size: Float, fuente: PDType1Font) = fuente.getStringWidth(limpio(valor, fuente)) / 1000f * size

    private fun lineas(valor: String?, maximo: Float, size: Float, fuente: PDType1Font): List<String> {
        val texto = limpio(valor?.takeIf { it.isNotBlank() }?.trim() ?: "—".let { if (runCatching { fuente.encode(it) }.isSuccess) it else "-" }, fuente)
        val resultado = mutableListOf<String>()
        var actual = ""
        fun cabe(v: String) = fuente.getStringWidth(v) / 1000f * size <= maximo
        for (palabra in texto.split(Regex("\\s+"))) {
            val candidato = if (actual.isEmpty()) palabra else "$actual $palabra"
            if (cabe(candidato)) { actual = candidato; continue }
            if (actual.isNotEmpty()) { resultado.add(actual); actual = "" }
            for (c in palabra) {
                if (!cabe(actual + c) && actual.isNotEmpty()) { resultado.add(actual); actual = "" }
                actual += c
            }
        }
        if (actual.isNotEmpty()) resultado.add(actual)
        return resultado.ifEmpty { listOf("-") }
    }

    /** Una linea; si se da `maximo`, se corta con «…» para que no invada lo de al lado. */
    private fun texto(valor: String, x: Float, top: Float, size: Float, fuente: PDType1Font, color: Color, maximo: Float? = null) {
        val s = stream ?: return
        var t = limpio(valor, fuente)
        if (maximo != null) while (t.length > 1 && anchoTexto(t, size, fuente) > maximo) t = t.dropLast(2) + "…".let { if (runCatching { fuente.encode(it) }.isSuccess) it else "." }
        s.setNonStrokingColor(color)
        s.beginText()
        s.setFont(fuente, size)
        s.newLineAtOffset(x, -top - size)
        s.showText(t)
        s.endText()
    }

    private fun textoDerecha(valor: String, derecha: Float, top: Float, size: Float, fuente: PDType1Font, color: Color) =
        texto(valor, derecha - anchoTexto(valor, size, fuente), top, size, fuente, color)

    private fun parrafo(valor: String, x: Float, top: Float, maximo: Float, size: Float, fuente: PDType1Font, color: Color, alto: Float): Float {
        val ls = lineas(valor, maximo, size, fuente)
        ls.forEachIndexed { i, l -> texto(l, x, top + i * alto, size, fuente, color) }
        return top + ls.size * alto
    }

    private fun raya(top: Float) {
        val s = stream ?: return
        s.setStrokingColor(linea)
        s.setLineWidth(0.6f)
        s.moveTo(pad, -top)
        s.lineTo(ancho - pad, -top)
        s.stroke()
    }

    private fun redondeado(x: Float, top: Float, w: Float, h: Float, r: Float, color: Color) {
        val s = stream ?: return
        val k = r * 0.5523f
        val yt = -top
        val yb = -top - h
        s.setNonStrokingColor(color)
        s.moveTo(x + r, yt)
        s.lineTo(x + w - r, yt)
        s.curveTo(x + w - r + k, yt, x + w, yt - r + k, x + w, yt - r)
        s.lineTo(x + w, yb + r)
        s.curveTo(x + w, yb + r - k, x + w - r + k, yb, x + w - r, yb)
        s.lineTo(x + r, yb)
        s.curveTo(x + r - k, yb, x, yb + r - k, x, yb + r)
        s.lineTo(x, yt - r)
        s.curveTo(x, yt - r + k, x + r - k, yt, x + r, yt)
        s.closePath()
        s.fill()
    }
}
