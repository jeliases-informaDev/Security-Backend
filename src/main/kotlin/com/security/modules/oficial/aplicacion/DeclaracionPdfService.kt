package com.security.modules.oficial.aplicacion

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

/** La hoja de la declaracion jurada de requisitos (OC 5) en PDF A4, para imprimir, firmar y subir. */
@Service
class DeclaracionPdfService {

    private val negrita = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val normal = PDType1Font(Standard14Fonts.FontName.HELVETICA)

    fun generar(hoja: HojaDeclaracionResponse): ByteArray {
        PDDocument().use { documento ->
            val pagina = PDPage(PDRectangle.A4)
            documento.addPage(pagina)
            val margen = 64f
            val ancho = pagina.mediaBox.width - 2 * margen
            var y = pagina.mediaBox.height - 80f
            PDPageContentStream(documento, pagina).use { s ->
                for (linea in dividir(hoja.titulo, ancho, 12f, negrita)) {
                    centrado(s, linea, pagina.mediaBox.width, y, 12f, negrita)
                    y -= 17f
                }
                y -= 18f
                for (linea in dividir(hoja.encabezado, ancho, 10.5f, normal)) {
                    escribir(s, linea, margen, y, 10.5f, normal)
                    y -= 15f
                }
                y -= 8f
                for (punto in hoja.puntos) {
                    val lineas = dividir(punto.texto, ancho - 22f, 10.5f, normal)
                    escribir(s, "${punto.letra})", margen, y, 10.5f, negrita)
                    for (linea in lineas) {
                        escribir(s, linea, margen + 22f, y, 10.5f, normal)
                        y -= 15f
                    }
                    y -= 5f
                }
                y -= 24f
                escribir(s, hoja.lugarFecha, margen, y, 10.5f, normal)
                y -= 80f
                s.moveTo(margen, y + 14f)
                s.lineTo(margen + 220f, y + 14f)
                s.stroke()
                escribir(s, hoja.firmaNombre, margen, y, 10.5f, negrita)
                y -= 15f
                escribir(s, hoja.firmaDocumento, margen, y, 10.5f, normal)
            }
            val salida = ByteArrayOutputStream()
            documento.save(salida)
            return salida.toByteArray()
        }
    }

    private fun escribir(s: PDPageContentStream, texto: String, x: Float, y: Float, tamano: Float, fuente: PDType1Font) {
        s.beginText()
        s.setFont(fuente, tamano)
        s.newLineAtOffset(x, y)
        s.showText(limpiar(texto))
        s.endText()
    }

    private fun centrado(s: PDPageContentStream, texto: String, anchoPagina: Float, y: Float, tamano: Float, fuente: PDType1Font) {
        val ancho = fuente.getStringWidth(limpiar(texto)) / 1000f * tamano
        escribir(s, texto, (anchoPagina - ancho) / 2f, y, tamano, fuente)
    }

    private fun dividir(texto: String, anchoMax: Float, tamano: Float, fuente: PDType1Font): List<String> {
        val lineas = mutableListOf<String>()
        var actual = ""
        for (palabra in limpiar(texto).split(" ").filter { it.isNotEmpty() }) {
            val candidato = if (actual.isEmpty()) palabra else "$actual $palabra"
            if (fuente.getStringWidth(candidato) / 1000f * tamano > anchoMax && actual.isNotEmpty()) {
                lineas += actual
                actual = palabra
            } else actual = candidato
        }
        if (actual.isNotEmpty()) lineas += actual
        return lineas.ifEmpty { listOf("") }
    }

    /** Helvetica estandar solo codifica WinAnsi: se conservan las tildes y la ñ; lo demas se cambia por "?". */
    private fun limpiar(texto: String): String = texto
        .replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
        .replace('–', '-').replace('—', '-').replace('“', '"').replace('”', '"').replace('’', '\'')
        .map { if (it.code in 32..126 || it.code in 160..255) it else '?' }
        .joinToString("")
}
