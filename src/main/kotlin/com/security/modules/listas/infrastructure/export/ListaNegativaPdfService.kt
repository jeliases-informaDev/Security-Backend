package com.security.modules.listas.infrastructure.export

import com.security.modules.listas.dto.ResultadoBusquedaResponse
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ListaNegativaPdfService {

    private val azulPrincipal = Color(27, 69, 137)   // #1B4589
    private val azulSecundario = Color(84, 122, 167) // #547AA7
    private val rojoAlerta = Color(220, 53, 69)      // #DC3545
    private val verdeExito = Color(40, 167, 69)      // #28A745
    private val grisFondo = Color(245, 247, 250)     // #F5F7FA
    private val grisBorde = Color(222, 226, 230)     // #DEE2E6
    private val negroTexto = Color(33, 37, 41)       // #212529
    private val grisTexto = Color(108, 117, 125)     // #6C757D
    private val blanco = Color.WHITE

    private val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val fontRegular = PDType1Font(Standard14Fonts.FontName.HELVETICA)

    private val margenIzq = 36f
    private val anchoPagina = 595.27f
    private val altoPagina = 841.89f
    private val anchoUtil = anchoPagina - (margenIzq * 2)

    fun generar(entidad: ResultadoBusquedaResponse, username: String): ByteArray {
        val doc = PDDocument()
        val logoBytes = try {
            ClassPathResource("static/images/Informa_Peru_Logo_Completo_Color_PNG_transparente.png")
                .inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }

        val logo = logoBytes?.let { PDImageXObject.createFromByteArray(doc, it, "logo") }
        val fechaEmision = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))

        val pages = mutableListOf<PDPage>()
        var page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        pages.add(page)

        var stream = PDPageContentStream(doc, page)
        var y = altoPagina - 36f

        fun verificarSalto(espacioRequerido: Float) {
            if (y - espacioRequerido < 50f) {
                stream.close()
                page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                pages.add(page)
                stream = PDPageContentStream(doc, page)
                y = altoPagina - 36f
                dibujarEncabezadoPagina(stream, logo, entidad.documento, fechaEmision)
                y -= 25f
            }
        }

        // 1. Cabecera principal (primera página)
        y = dibujarCabeceraPrincipal(stream, logo, fechaEmision, username)

        // 2. Sección: Datos de la Entidad Consultada
        verificarSalto(110f)
        y = dibujarSeccionTitulo(stream, y, "DATOS DE LA ENTIDAD CONSULTADA")
        y = dibujarFichaEntidad(stream, y, entidad)

        // 3. Resumen de Coincidencias / Estado de Riesgo
        verificarSalto(55f)
        val tieneManchas = entidad.manchas.isNotEmpty()
        y = dibujarBadgeResumen(stream, y, tieneManchas, entidad.manchas.size)

        // 4. Detalle de Coincidencias
        if (tieneManchas) {
            verificarSalto(40f)
            y = dibujarSeccionTitulo(stream, y, "DETALLE DE COINCIDENCIAS Y LISTAS RESTRICTIVAS")

            for ((index, mancha) in entidad.manchas.withIndex()) {
                val alturaEstimada = calcularAlturaMancha(mancha)
                verificarSalto(alturaEstimada + 15f)
                y = dibujarTarjetaMancha(stream, y, mancha, index + 1)
            }
        }

        // 5. Pie de certificación legal
        verificarSalto(65f)
        dibujarCertificacionLegal(stream, y)

        stream.close()

        // Dibujar pie de página con número de página en todas las hojas
        val totalPaginas = pages.size
        for ((idx, p) in pages.withIndex()) {
            PDPageContentStream(doc, p, PDPageContentStream.AppendMode.APPEND, true).use { s ->
                dibujarPiePagina(s, idx + 1, totalPaginas)
            }
        }

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun dibujarCabeceraPrincipal(stream: PDPageContentStream, logo: PDImageXObject?, fecha: String, usuario: String): Float {
        val yBase = altoPagina - 36f

        if (logo != null) {
            val maxW = 120f
            val maxH = 38f
            val scale = minOf(maxW / logo.width, maxH / logo.height)
            val w = logo.width * scale
            val h = logo.height * scale
            stream.drawImage(logo, margenIzq, yBase - h, w, h)
        }

        // Título del reporte (alineado a la derecha)
        val titulo = "REPORTE DE COINCIDENCIA EN LISTAS"
        val subtitulo = "Sistema de Prevención de Lavado de Activos (PLAFT)"
        val infoEmision = "Emisión: $fecha | Usuario: $usuario"

        escribirTextoDerecha(stream, titulo, anchoPagina - margenIzq, yBase - 12f, 13f, fontBold, azulPrincipal)
        escribirTextoDerecha(stream, subtitulo, anchoPagina - margenIzq, yBase - 25f, 8.5f, fontRegular, grisTexto)
        escribirTextoDerecha(stream, infoEmision, anchoPagina - margenIzq, yBase - 36f, 8f, fontRegular, negroTexto)

        // Línea divisoria
        val yLinea = yBase - 46f
        stream.setStrokingColor(azulPrincipal)
        stream.setLineWidth(1.5f)
        stream.moveTo(margenIzq, yLinea)
        stream.lineTo(anchoPagina - margenIzq, yLinea)
        stream.stroke()

        return yLinea - 15f
    }

    private fun dibujarEncabezadoPagina(stream: PDPageContentStream, logo: PDImageXObject?, docNum: String, fecha: String) {
        val yBase = altoPagina - 30f
        if (logo != null) {
            val scale = minOf(70f / logo.width, 22f / logo.height)
            stream.drawImage(logo, margenIzq, yBase - (logo.height * scale), logo.width * scale, logo.height * scale)
        }
        val texto = "Reporte de Coincidencia - Doc: $docNum | $fecha"
        escribirTextoDerecha(stream, texto, anchoPagina - margenIzq, yBase - 14f, 8f, fontRegular, grisTexto)

        stream.setStrokingColor(grisBorde)
        stream.setLineWidth(0.8f)
        stream.moveTo(margenIzq, yBase - 20f)
        stream.lineTo(anchoPagina - margenIzq, yBase - 20f)
        stream.stroke()
    }

    private fun dibujarSeccionTitulo(stream: PDPageContentStream, y: Float, titulo: String): Float {
        val altoBarra = 18f
        stream.setNonStrokingColor(azulPrincipal)
        stream.addRect(margenIzq, y - altoBarra, anchoUtil, altoBarra)
        stream.fill()

        escribirTexto(stream, titulo, margenIzq + 8f, y - 13f, 9.5f, fontBold, blanco)
        return y - altoBarra - 6f
    }

    private fun dibujarFichaEntidad(stream: PDPageContentStream, y: Float, entidad: ResultadoBusquedaResponse): Float {
        val colW = anchoUtil / 2f
        val altoFila = 18f
        var currentY = y

        val camposFila1 = listOf(
            "Tipo de Documento" to entidad.tipoDocumento,
            "Número de Documento" to entidad.documento
        )
        val camposFila2 = listOf(
            "Nombre / Razón Social" to entidad.nombreCompleto,
            "Tipo de Entidad" to entidad.tipoEntidad
        )
        val camposFila3 = listOf(
            "País" to (entidad.pais ?: "Perú"),
            "Alias / Otros" to (entidad.alias ?: entidad.pasaporte ?: "-")
        )

        val filas = listOf(camposFila1, camposFila2, camposFila3)
        for ((idx, f) in filas.withIndex()) {
            val fondo = if (idx % 2 == 0) grisFondo else blanco
            stream.setNonStrokingColor(fondo)
            stream.addRect(margenIzq, currentY - altoFila, anchoUtil, altoFila)
            stream.fill()

            stream.setStrokingColor(grisBorde)
            stream.setLineWidth(0.5f)
            stream.addRect(margenIzq, currentY - altoFila, anchoUtil, altoFila)
            stream.stroke()

            // Col 1
            escribirTexto(stream, "${f[0].first}:", margenIzq + 6f, currentY - 12f, 8f, fontBold, azulSecundario)
            val val1 = truncarTexto(f[0].second, 34)
            escribirTexto(stream, val1, margenIzq + 95f, currentY - 12f, 8.5f, fontRegular, negroTexto)

            // Col 2
            escribirTexto(stream, "${f[1].first}:", margenIzq + colW + 6f, currentY - 12f, 8f, fontBold, azulSecundario)
            val val2 = truncarTexto(f[1].second, 32)
            escribirTexto(stream, val2, margenIzq + colW + 95f, currentY - 12f, 8.5f, fontRegular, negroTexto)

            currentY -= altoFila
        }

        return currentY - 10f
    }

    private fun dibujarBadgeResumen(stream: PDPageContentStream, y: Float, tieneManchas: Boolean, cantManchas: Int): Float {
        val altoBox = 26f
        val colorFondo = if (tieneManchas) Color(253, 237, 237) else Color(237, 247, 237)
        val colorBorde = if (tieneManchas) rojoAlerta else verdeExito
        val textoTitulo = if (tieneManchas) {
            "ATENCIÓN: SE IDENTIFICARON $cantManchas COINCIDENCIA(S) EN LISTAS"
        } else {
            "RESULTADO: NO REGISTRA COINCIDENCIAS NI SANCIONES EN LISTAS"
        }

        stream.setNonStrokingColor(colorFondo)
        stream.addRect(margenIzq, y - altoBox, anchoUtil, altoBox)
        stream.fill()

        stream.setStrokingColor(colorBorde)
        stream.setLineWidth(1.2f)
        stream.addRect(margenIzq, y - altoBox, anchoUtil, altoBox)
        stream.stroke()

        escribirTexto(stream, textoTitulo, margenIzq + 12f, y - 17f, 9.5f, fontBold, colorBorde)
        return y - altoBox - 12f
    }

    private fun calcularAlturaMancha(mancha: com.security.modules.listas.dto.ManchaResponse): Float {
        val desc = mancha.descripcion ?: "Sin descripción"
        val lineas = dividirTexto(desc, anchoUtil - 20f, 8f, fontRegular)
        return 50f + (lineas.size * 11f)
    }

    private fun dibujarTarjetaMancha(stream: PDPageContentStream, y: Float, mancha: com.security.modules.listas.dto.ManchaResponse, num: Int): Float {
        val desc = mancha.descripcion ?: "Sin descripción"
        val lineas = dividirTexto(desc, anchoUtil - 20f, 8f, fontRegular)
        val altoTotal = 46f + (lineas.size * 11f)

        // Fondo y borde de tarjeta
        stream.setNonStrokingColor(grisFondo)
        stream.addRect(margenIzq, y - altoTotal, anchoUtil, altoTotal)
        stream.fill()

        stream.setStrokingColor(grisBorde)
        stream.setLineWidth(0.8f)
        stream.addRect(margenIzq, y - altoTotal, anchoUtil, altoTotal)
        stream.stroke()

        // Banda superior de la tarjeta
        val altoCab = 18f
        val colorBanda = if (mancha.esPep) Color(245, 235, 220) else Color(235, 240, 248)
        val colorTextoBanda = if (mancha.esPep) Color(175, 80, 0) else azulPrincipal

        stream.setNonStrokingColor(colorBanda)
        stream.addRect(margenIzq, y - altoCab, anchoUtil, altoCab)
        stream.fill()

        val tituloTarjeta = "#$num. ${mancha.tipoListaNombre ?: "LISTA RESTRICTIVA"} ${if (mancha.esPep) "[PEP]" else ""}"
        escribirTexto(stream, tituloTarjeta, margenIzq + 8f, y - 12f, 8.5f, fontBold, colorTextoBanda)

        // Detalles: Cargo / Institución / Periodo
        var cursorY = y - altoCab - 12f
        if (!mancha.cargo.isNullOrBlank() || !mancha.institucion.isNullOrBlank()) {
            val cargoInst = listOfNotNull(mancha.cargo?.let { "Cargo: $it" }, mancha.institucion?.let { "Entidad: $it" }).joinToString(" | ")
            escribirTexto(stream, truncarTexto(cargoInst, 85), margenIzq + 10f, cursorY, 8f, fontBold, negroTexto)
            cursorY -= 12f
        }

        // Descripción envuelta
        for (l in lineas) {
            escribirTexto(stream, l, margenIzq + 10f, cursorY, 8f, fontRegular, negroTexto)
            cursorY -= 11f
        }

        // Fechas de vigencia al pie de la tarjeta
        val fechaReg = mancha.fechaRegistro?.toString() ?: "-"
        val fDesde = mancha.periodoDesde ?: "-"
        val fHasta = mancha.periodoHasta ?: mancha.fechaHasta?.toString() ?: "-"
        val infoFechas = "Registro: $fechaReg  |  Vigencia: $fDesde a $fHasta"
        escribirTexto(stream, infoFechas, margenIzq + 10f, y - altoTotal + 6f, 7.5f, fontRegular, grisTexto)

        return y - altoTotal - 10f
    }

    private fun dibujarCertificacionLegal(stream: PDPageContentStream, y: Float) {
        val altoBox = 36f
        stream.setStrokingColor(grisBorde)
        stream.setLineWidth(0.6f)
        stream.addRect(margenIzq, y - altoBox, anchoUtil, altoBox)
        stream.stroke()

        val txt1 = "CERTIFICADO DE CONFORMIDAD Y CUMPLIMIENTO PLAFT:"
        val txt2 = "El presente documento certifica la consulta realizada en el Sistema Integral de Cumplimiento Normativo."
        val txt3 = "La información emitida es confidencial y refleja fielmente los registros oficiales al momento de la consulta."

        escribirTexto(stream, txt1, margenIzq + 8f, y - 11f, 7.5f, fontBold, azulSecundario)
        escribirTexto(stream, txt2, margenIzq + 8f, y - 21f, 7f, fontRegular, grisTexto)
        escribirTexto(stream, txt3, margenIzq + 8f, y - 30f, 7f, fontRegular, grisTexto)
    }

    private fun dibujarPiePagina(stream: PDPageContentStream, pagActual: Int, totalPags: Int) {
        val yPie = 24f
        val textoPie = "Informa Perú PLAFT  |  Documento Electrónico Generado con Validez Institucional"
        val textoPag = "Página $pagActual de $totalPags"

        escribirTexto(stream, textoPie, margenIzq, yPie, 7.5f, fontRegular, grisTexto)
        escribirTextoDerecha(stream, textoPag, anchoPagina - margenIzq, yPie, 7.5f, fontBold, grisTexto)
    }

    // =========================================================
    // UTILIDADES DE TEXTO
    // =========================================================

    private fun escribirTexto(stream: PDPageContentStream, texto: String, x: Float, y: Float, tamano: Float, fuente: PDType1Font, color: Color) {
        val limpio = limpiarTexto(texto)
        stream.beginText()
        stream.setFont(fuente, tamano)
        stream.setNonStrokingColor(color)
        stream.newLineAtOffset(x, y)
        stream.showText(limpio)
        stream.endText()
    }

    private fun escribirTextoDerecha(stream: PDPageContentStream, texto: String, xDerecha: Float, y: Float, tamano: Float, fuente: PDType1Font, color: Color) {
        val limpio = limpiarTexto(texto)
        val ancho = (fuente.getStringWidth(limpio) / 1000f) * tamano
        escribirTexto(stream, limpio, xDerecha - ancho, y, tamano, fuente, color)
    }

    private fun dividirTexto(texto: String, anchoMax: Float, tamano: Float, fuente: PDType1Font): List<String> {
        val palabras = limpiarTexto(texto).split(" ")
        val lineas = mutableListOf<String>()
        var actual = StringBuilder()

        for (p in palabras) {
            val candidato = if (actual.isEmpty()) p else "$actual $p"
            val ancho = (fuente.getStringWidth(candidato) / 1000f) * tamano
            if (ancho > anchoMax && actual.isNotEmpty()) {
                lineas.add(actual.toString())
                actual = StringBuilder(p)
            } else {
                actual = StringBuilder(candidato)
            }
        }
        if (actual.isNotEmpty()) {
            lineas.add(actual.toString())
        }
        return if (lineas.isEmpty()) listOf("-") else lineas
    }

    private fun truncarTexto(texto: String?, maxLen: Int): String {
        if (texto.isNullOrBlank()) return "-"
        return if (texto.length <= maxLen) texto else texto.take(maxLen - 3) + "..."
    }

    private fun limpiarTexto(texto: String): String {
        return texto
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\t", " ")
            .replace("ñ", "n")
            .replace("Ñ", "N")
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
            .replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U")
            .filter { it.code in 32..126 }
    }
}
