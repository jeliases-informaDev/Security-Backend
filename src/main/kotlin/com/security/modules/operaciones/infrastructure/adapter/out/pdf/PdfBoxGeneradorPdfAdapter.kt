package com.security.modules.operaciones.infrastructure.adapter.out.pdf

import com.security.modules.operaciones.domain.model.*
import com.security.modules.operaciones.domain.port.out.GeneradorPdfPort
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class PdfBoxGeneradorPdfAdapter : GeneradorPdfPort {
    override fun generar(operacion: OperacionExportacion): ByteArray {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                val form = FormularioPdf(stream)
                val r = operacion.registro
                val o = operacion.operacion

                val resumen = listOf(
                    Campo("N.º de registro", operacion.codigoOperacion),
                    Campo("Fecha de operación", fecha(o.fechaOperacion)),
                    Campo("Monto", "${o.moneda} ${o.monto.toPlainString()}"),
                    Campo("Oficina", r.oficina)
                )

                val sections = listOf(
                    Seccion("I", "Información del registro", listOf(
                        listOf(Campo("Usuario que registra", r.usuario), Campo("Empresa", r.empresa)),
                        listOf(Campo("N.º de registro", operacion.codigoOperacion), Campo("Oficina", r.oficina),
                            Campo("Fecha del registro", fecha(r.fechaRegistro)))
                    )),
                    Seccion("II", "Persona que realiza físicamente la operación", persona(operacion.personaFisica)),
                    Seccion("III", "Persona en cuyo nombre se realiza la operación",
                        operacion.personaRepresentada?.let(::persona)
                            ?: listOf(listOf(Campo("Persona representada", "No registrada")))),
                    Seccion("IV", "Descripción de la operación", listOf(
                        listOf(Campo("Fecha de operación", fecha(o.fechaOperacion)), Campo("Lugar de realización", o.lugar),
                            Campo("Modalidad de pago", o.modalidadPago), Campo("Tipo de moneda", o.moneda)),
                        listOf(Campo("Monto de la operación", o.monto.toPlainString()), Campo("Tipo de operación", o.tipoOperacion),
                            Campo("N.º de cuenta 1", o.cuenta1), Campo("N.º de cuenta 2", o.cuenta2)),
                        listOf(Campo("N.º de cuenta 3", o.cuenta3))
                    ))
                )
                val beneficiaries = operacion.beneficiarios.mapIndexed { index, b ->
                    listOf((index + 1).toString(), b.nombre, b.tipoDocumento, b.numeroDocumento, fecha(b.fechaNacimiento))
                }

                val fullHeight = form.alturaResumen(resumen) + form.separacion +
                        sections.sumOf { (form.altura(it) + form.separacion).toDouble() }.toFloat() +
                        form.alturaBeneficiarios(beneficiaries)
                val available = page.mediaBox.height - 150f
                val scale = minOf(1f, available / fullHeight)
                val width = 539f
                val left = (page.mediaBox.width - width * scale) / 2f

                // Cabecera fija: el logo conserva su proporción original.
                stream.saveGraphicsState()
                stream.transform(Matrix.getTranslateInstance(28f, page.mediaBox.height - 24f))
                val logoBytes = ClassPathResource("static/images/Informa_Peru_Logo_Completo_Color_PNG_transparente.png")
                    .inputStream.use { it.readBytes() }
                val logo = PDImageXObject.createFromByteArray(document, logoBytes, "Informa Peru")
                val logoScale = minOf(125f / logo.width, 40f / logo.height)
                stream.drawImage(logo, 0f, -40f, logo.width * logoScale, logo.height * logoScale)
                form.cabeceraPagina("${operacion.id}", fecha(r.fechaRegistro))
                stream.restoreGraphicsState()

                // Contenido (se reduce solo si no cabe en una hoja).
                stream.saveGraphicsState()
                stream.transform(Matrix(scale, 0f, 0f, scale, left, page.mediaBox.height - 100f))
                var top = form.resumen(resumen, 0f) + form.separacion
                sections.forEach { top = form.seccion(it, top) }
                form.beneficiarios(beneficiaries, top)
                stream.restoreGraphicsState()

                // Pie de página.
                stream.saveGraphicsState()
                stream.transform(Matrix.getTranslateInstance(28f, 27f))
                form.pie(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                stream.restoreGraphicsState()
            }
            return ByteArrayOutputStream().use { output ->
                document.save(output)
                output.toByteArray()
            }
        }
    }

    private fun persona(p: PersonaExportacion) = listOf(
        listOf(Campo("Documento de identidad", p.tipoDocumento), Campo("N.º de documento", p.numeroDocumento),
            Campo("Nombres", p.nombres), Campo("Apellidos", p.apellidos)),
        listOf(Campo("Fecha de nacimiento", fecha(p.fechaNacimiento)), Campo("Nacionalidad", p.nacionalidad),
            Campo("Teléfono", p.telefono), Campo("Profesión u ocupación", p.ocupacion)),
        listOf(Campo("Domicilio", p.domicilio), Campo("Código postal", p.codigoPostal),
            Campo("Departamento", p.departamento), Campo("Provincia", p.provincia)),
        listOf(Campo("Distrito", p.distrito))
    )
}

private fun fecha(value: LocalDate?) = value?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "-"
private data class Campo(val etiqueta: String, val valor: String?)
private data class Seccion(val numero: String, val titulo: String, val filas: List<List<Campo>>)

/** Mide antes de dibujar: el ajuste a una hoja nunca elimina filas ni recorta valores. */
private class FormularioPdf(private val stream: PDPageContentStream) {
    private val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    // Paleta corporativa del sistema.
    private val blue = Color(27, 69, 137)    // #1B4589
    private val steel = Color(84, 122, 167)  // #547AA7
    private val pale = Color(232, 236, 243)  // #E8ECF3
    private val line = Color(198, 208, 226)  // #C6D0E2
    private val soft = Color(247, 249, 252)  // #F7F9FC
    private val ink = Color(35, 31, 32)      // #231F20
    private val red = Color(220, 38, 38)

    private val width = 539f
    private val tableWidths = listOf(28f, 175f, 108f, 100f, 100f) // suma 511 = width - 28
    val separacion = 10f

    // ---------- Texto ----------

    private fun limpio(value: String): String = buildString {
        value.replace("\r", " ").replace("\n", " ").replace("\t", " ").codePoints().forEach { cp ->
            val s = String(Character.toChars(cp))
            append(try { regular.encode(s); s } catch (_: IllegalArgumentException) { "?" })
        }
    }

    private fun ancho(value: String, size: Float, strong: Boolean = false): Float =
        (if (strong) bold else regular).getStringWidth(limpio(value)) / 1000f * size

    private fun lineas(value: String?, maxWidth: Float, size: Float, fuente: PDType1Font = regular): List<String> {
        val text = limpio(value?.takeIf { it.isNotBlank() } ?: "-")
        val result = mutableListOf<String>()
        var actual = ""
        fun cabe(value: String) = fuente.getStringWidth(value) / 1000f * size <= maxWidth
        for (word in text.split(Regex("\\s+"))) {
            val candidate = if (actual.isEmpty()) word else "$actual $word"
            if (cabe(candidate)) {
                actual = candidate
                continue
            }
            if (actual.isNotEmpty()) {
                result.add(actual)
                actual = ""
            }
            // Solo divide palabras cuando una palabra o código supera el ancho completo.
            for (char in word) {
                if (!cabe(actual + char) && actual.isNotEmpty()) {
                    result.add(actual)
                    actual = ""
                }
                actual += char
            }
        }
        if (actual.isNotEmpty()) result.add(actual.trimEnd())
        return result.ifEmpty { listOf("-") }
    }

    private fun texto(value: String, x: Float, top: Float, size: Float, strong: Boolean = false, color: Color = ink) {
        stream.setNonStrokingColor(color)
        stream.beginText()
        stream.setFont(if (strong) bold else regular, size)
        stream.newLineAtOffset(x, -top - size)
        stream.showText(limpio(value))
        stream.endText()
    }

    private fun textoDerecha(value: String, right: Float, top: Float, size: Float, strong: Boolean = false, color: Color = ink) =
        texto(value, right - ancho(value, size, strong), top, size, strong, color)

    private fun textoCentrado(value: String, cx: Float, top: Float, size: Float, strong: Boolean = false, color: Color = ink) =
        texto(value, cx - ancho(value, size, strong) / 2f, top, size, strong, color)

    // ---------- Formas ----------

    private fun rect(x: Float, top: Float, w: Float, h: Float, color: Color) {
        stream.setNonStrokingColor(color)
        stream.addRect(x, -top - h, w, h)
        stream.fill()
    }

    private fun trazoLinea(x1: Float, x2: Float, top: Float, color: Color, lw: Float) {
        stream.setStrokingColor(color)
        stream.setLineWidth(lw)
        stream.moveTo(x1, -top)
        stream.lineTo(x2, -top)
        stream.stroke()
    }

    private fun trazoVertical(x: Float, top1: Float, top2: Float, color: Color, lw: Float) {
        stream.setStrokingColor(color)
        stream.setLineWidth(lw)
        stream.moveTo(x, -top1)
        stream.lineTo(x, -top2)
        stream.stroke()
    }

    private fun rutaRedondeada(x: Float, top: Float, w: Float, h: Float, r: Float) {
        val k = r * 0.5523f
        val yt = -top
        val yb = -top - h
        stream.moveTo(x + r, yt)
        stream.lineTo(x + w - r, yt)
        stream.curveTo(x + w - r + k, yt, x + w, yt - r + k, x + w, yt - r)
        stream.lineTo(x + w, yb + r)
        stream.curveTo(x + w, yb + r - k, x + w - r + k, yb, x + w - r, yb)
        stream.lineTo(x + r, yb)
        stream.curveTo(x + r - k, yb, x, yb + r - k, x, yb + r)
        stream.lineTo(x, yt - r)
        stream.curveTo(x, yt - r + k, x + r - k, yt, x + r, yt)
        stream.closePath()
    }

    private fun tarjeta(x: Float, top: Float, w: Float, h: Float, r: Float, fill: Color?, stroke: Color? = null, lw: Float = 0.7f) {
        // Sin relleno ni borde no hay nada que dibujar.
        if (fill == null && stroke == null) return

        rutaRedondeada(x, top, w, h, r)
        if (fill != null) stream.setNonStrokingColor(fill)
        if (stroke != null) {
            stream.setStrokingColor(stroke)
            stream.setLineWidth(lw)
        }
        if (fill != null && stroke != null) stream.fillAndStroke()
        else if (fill != null) stream.fill()
        else stream.stroke()
    }

    // ---------- Cabecera y pie de página ----------

    /** Se dibuja con el origen en la esquina superior izquierda de la cabecera. */
    fun cabeceraPagina(id: String, fechaRegistro: String) {
        val eyebrow = "REGISTRO DE OPERACIONES"
        textoDerecha(eyebrow, width, 2f, 7f, true, steel)
        tarjeta(width - ancho(eyebrow, 7f, true) - 9f - 2.2f, 6.5f - 2.2f, 4.4f, 4.4f, 2.2f, red)
        textoDerecha("Registro de operación", width, 14f, 17f, true, blue)
        textoDerecha("Información del registro y personas involucradas", width, 36f, 8f, false, steel)
        textoDerecha("ID: $id  |  Fecha: $fechaRegistro", width, 48f, 8f, false, ink)
        rect(0f, 62f, width, 1.2f, blue)
        rect(0f, 62f, 40f, 1.2f, red)
    }

    /** Se dibuja con el origen en la esquina inferior izquierda (28, 27). */
    fun pie(generado: String) {
        trazoLinea(0f, width, -9f, line, 0.6f)
        texto("INFORMA PERÚ  |  Registro de operaciones  |  Generado el $generado", 0f, 0f, 7f, false, steel)
        textoDerecha("Página 1 de 1", width, 0f, 7f, true, steel)
    }

    // ---------- Franja de resumen ----------

    private val pesosResumen = listOf(2.3f, 1f, 1.1f, 0.9f)

    private fun anchosResumen(n: Int): List<Float> {
        val pesos = if (n == pesosResumen.size) pesosResumen else List(n) { 1f }
        val total = pesos.sum()
        return pesos.map { (width - 32f) * it / total }
    }

    fun alturaResumen(items: List<Campo>): Float {
        val anchos = anchosResumen(items.size)
        return 22f + items.indices.maxOf { lineas(items[it].valor, anchos[it] - 14f, 9f, bold).size } * 11f + 10f
    }

    fun resumen(items: List<Campo>, top: Float): Float {
        val h = alturaResumen(items)
        val anchos = anchosResumen(items.size)
        tarjeta(0f, top, width, h, 12f, pale, line, 0.7f)
        rect(0f, top + 10f, 3f, h - 20f, blue) // acento lateral

        var x = 16f
        items.forEachIndexed { i, item ->
            if (i > 0) trazoVertical(x - 8f, top + 12f, top + h - 12f, line, 0.6f)
            texto(item.etiqueta.uppercase(), x, top + 10f, 6.3f, true, steel)
            lineas(item.valor, anchos[i] - 14f, 9f, bold).forEachIndexed { j, linea ->
                texto(linea, x, top + 22f + j * 11f, 9f, true, blue)
            }
            x += anchos[i]
        }
        return top + h
    }

    // ---------- Secciones ----------

    private fun encabezadoTarjeta(numero: String, titulo: String, top: Float) {
        tarjeta(14f, top + 8f, 16f, 16f, 8f, blue)
        textoCentrado(numero, 22f, top + 11f, 8f, true, Color.WHITE)
        texto(titulo, 38f, top + 9.5f, 10f, true, ink)
        trazoLinea(14f, width - 14f, top + 31f, pale, 0.6f)
    }

    private fun anchoCampo(columnas: Int) = (width - 28f - (columnas - 1) * 12f) / columnas

    private fun filaAltura(fields: List<Campo>, w: Float): Float =
        11f + fields.maxOf { lineas(it.valor, w, 8.5f).size } * 10.5f + 9f

    fun altura(section: Seccion): Float {
        val w = anchoCampo(section.filas.maxOf { it.size })
        return 40f + section.filas.sumOf { filaAltura(it, w).toDouble() }.toFloat() + 6f
    }

    fun seccion(section: Seccion, top: Float): Float {
        val h = altura(section)
        val columnas = section.filas.maxOf { it.size }
        val w = anchoCampo(columnas)
        tarjeta(0f, top, width, h, 10f, Color.WHITE, line, 0.7f)
        encabezadoTarjeta(section.numero, section.titulo, top)

        var y = top + 40f
        section.filas.forEach { fields ->
            val fh = filaAltura(fields, w)
            fields.forEachIndexed { i, field ->
                val x = 14f + i * (w + 12f)
                texto(field.etiqueta.uppercase(), x, y, 6.3f, true, steel)
                lineas(field.valor, w, 8.5f).forEachIndexed { j, linea ->
                    texto(linea, x, y + 11f + j * 10.5f, 8.5f, false, ink)
                }
                trazoLinea(x, x + w, y + fh - 4f, pale, 0.6f)
            }
            y += fh
        }
        return top + h + separacion
    }

    // ---------- Beneficiarios ----------

    private fun tableHeight(row: List<String>): Float =
        row.indices.maxOf { lineas(row[it], tableWidths[it] - 12f, 7.5f).size } * 9f + 12f

    fun alturaBeneficiarios(rows: List<List<String>>): Float =
        40f + 22f + (if (rows.isEmpty()) 22f else rows.sumOf { tableHeight(it).toDouble() }.toFloat()) + 8f

    fun beneficiarios(rows: List<List<String>>, top: Float) {
        val h = alturaBeneficiarios(rows)
        tarjeta(0f, top, width, h, 10f, Color.WHITE, line, 0.7f)
        encabezadoTarjeta("V", "Beneficiarios múltiples (${rows.size})", top)

        val labels = listOf("N.º", "Apellidos o razón social", "Tipo de documento", "N.º de documento", "Fecha de nacimiento")
        val y0 = top + 40f
        tarjeta(14f, y0, width - 28f, 20f, 5f, pale)
        var x = 14f
        labels.forEachIndexed { i, label ->
            texto(label.uppercase(), x + 6f, y0 + 7f, 6.3f, true, steel)
            x += tableWidths[i]
        }

        var y = y0 + 22f
        if (rows.isEmpty()) texto("Sin beneficiarios registrados", 20f, y + 6f, 8f, false, steel)
        rows.forEachIndexed { index, row ->
            val rh = tableHeight(row)
            if (index % 2 == 1) rect(14f, y, width - 28f, rh, soft)
            x = 14f
            row.forEachIndexed { i, value ->
                lineas(value, tableWidths[i] - 12f, 7.5f).forEachIndexed { j, linea ->
                    texto(linea, x + 6f, y + 6f + j * 9f, 7.5f, i == 0, if (i == 0) steel else ink)
                }
                x += tableWidths[i]
            }
            trazoLinea(14f, width - 14f, y + rh, pale, 0.5f)
            y += rh
        }
    }
}