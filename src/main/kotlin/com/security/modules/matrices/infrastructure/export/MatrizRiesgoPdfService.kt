package com.security.modules.matrices.infrastructure.export

import com.security.modules.matrices.application.dto.MatrizRiesgoDetalleResponse
import com.security.modules.matrices.application.service.HeatmapMatrizService
import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

@Service
class MatrizRiesgoPdfService(
    private val heatmapMatrizService: HeatmapMatrizService
) {

    /*
     * Por ahora utilizamos Helvetica.
     *
     * El manual de marca establece Leelawadee UI como
     * tipografía institucional. Si posteriormente la empresa
     * incorpora legalmente los archivos de fuente al proyecto,
     * podemos sustituir estas fuentes.
     */
    private val fontRegular =
        PDType1Font(Standard14Fonts.FontName.HELVETICA)

    private val fontBold =
        PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    // =========================================================
    // PALETA OFICIAL INFORMA PERÚ
    // =========================================================

    private val azulInstitucional =
        Color(27, 69, 137) // #1B4589

    private val rojoInstitucional =
        Color(237, 28, 36) // #ED1C24

    private val azul75 =
        Color(84, 122, 167) // #547AA7

    private val azul25 =
        Color(198, 208, 226) // #C6D0E2

    private val azul10 =
        Color(232, 236, 243) // #E8ECF3

    private val negroTexto =
        Color(35, 31, 32) // #231F20

    private val blanco =
        Color.WHITE

    // =========================================================
    // FORMATOS
    // =========================================================

    private val formatterFecha =
        DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private val formatterFechaHora =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    private val formatterMonto =
        DecimalFormat("#,##0.00")

    companion object {

        private const val MARGIN = 36f

        private const val LABEL_WIDTH = 145f

        /*
         * Ahora las filas tienen un poco más de altura.
         * Antes eran 20f.
         */
        private const val ALTO_FILA_MINIMO = 22f

        /*
         * Espacio entre bloques completos:
         *
         * RIESGO IDENTIFICADO
         *      ↓
         * RIESGO INHERENTE
         */
        private const val ESPACIO_ENTRE_BLOQUES = 12f

        /*
         * Separación especial antes de cada heatmap.
         */
        private const val ESPACIO_ANTES_HEATMAP = 14f

        private const val LOGO_PATH =
            "static/images/Informa_Peru_Logo_Completo_Color_PNG_transparente.png"
    }

    // =========================================================
    // GENERACIÓN PRINCIPAL
    // =========================================================

    fun generar(
        analisis: MatrizRiesgoDetalleResponse,
        username: String
    ): ByteArray {

        PDDocument().use { document ->

            /*
             * El PNG tiene bastante espacio transparente alrededor.
             *
             * Ahora lo recortamos automáticamente antes de insertarlo
             * en el PDF para que el logo visible pueda aprovechar
             * realmente el tamaño disponible.
             */
            val logo =
                cargarLogoInstitucional(document)

            val heatmap =
                heatmapMatrizService.obtenerHeatmap()

            // =====================================================
            // PÁGINA 1
            // CABECERA INSTITUCIONAL SOLO AQUÍ
            // =====================================================

            val pagina1 =
                PDPage(PDRectangle.A4)

            document.addPage(pagina1)

            PDPageContentStream(
                document,
                pagina1
            ).use { contenido ->

                var y =
                    dibujarCabecera(
                        contenido = contenido,
                        pagina = pagina1,
                        logo = logo,
                        usuario = username,
                        fecha = analisis.fechaActualizacion
                            .format(formatterFechaHora)
                    )

                // -------------------------------------------------
                // RIESGO IDENTIFICADO
                // -------------------------------------------------

                y = dibujarTituloSeccion(
                    contenido = contenido,
                    y = y,
                    titulo = "RIESGO IDENTIFICADO"
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Título",
                    valor = analisis.titulo ?: "-",
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Detalle de Riesgo",
                    valor = analisis.detalleRiesgo ?: "-",
                    alterna = true
                )

                /*
                 * Más aire entre bloques.
                 */
                y -= ESPACIO_ENTRE_BLOQUES

                // -------------------------------------------------
                // RIESGO INHERENTE
                // -------------------------------------------------

                y = dibujarTituloSeccion(
                    contenido = contenido,
                    y = y,
                    titulo = "RIESGO INHERENTE"
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Tipo de empresa",
                    valor = etiquetaEnum(
                        analisis.tipoEmpresa
                    ),
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Área de la empresa",
                    valor = analisis.area ?: "-",
                    alterna = true
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Proceso",
                    valor = analisis.proceso ?: "-",
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Factor",
                    valor = etiquetaEnum(
                        analisis.factor
                    ),
                    alterna = true
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Probabilidad",
                    valor =
                        analisis.probabilidad
                            ?.let {
                                "${etiquetaEnum(it)} - ${it.descripcion}"
                            }
                            ?: "-",
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Impacto estimado S/",
                    valor = formatearMonto(
                        analisis.impactoEstimado
                    ),
                    alterna = true
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Impacto calculado",
                    valor = etiquetaEnum(
                        analisis.impactoInherente
                    ),
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Riesgo inherente",
                    valor = etiquetaRiesgo(
                        analisis.riesgoInherente
                    ),
                    alterna = true
                )

                y -= ESPACIO_ANTES_HEATMAP

                // -------------------------------------------------
                // HEATMAP INHERENTE
                // -------------------------------------------------

                y = dibujarHeatmap(
                    contenido = contenido,
                    ySuperior = y,
                    titulo = "MATRIZ DE RIESGO INHERENTE",

                    probabilidades =
                        heatmap.probabilidades.map {
                            it.codigo
                        },

                    impactos =
                        heatmap.impactos.map {
                            it.codigo
                        },

                    obtenerRiesgo = {
                            probabilidad,
                            impacto ->

                        heatmap.celdas
                            .first {
                                it.probabilidad == probabilidad &&
                                        it.impacto == impacto
                            }
                            .riesgo
                    },

                    marcadorProbabilidad =
                        analisis.probabilidad,

                    marcadorImpacto =
                        analisis.impactoInherente
                )

                /*
                 * Separación entre la tarjeta del heatmap
                 * y la siguiente sección.
                 */
                y -= ESPACIO_ENTRE_BLOQUES

                // -------------------------------------------------
                // CONTROLES
                // -------------------------------------------------

                y = dibujarTituloSeccion(
                    contenido = contenido,
                    y = y,
                    titulo =
                        "IDENTIFICACIÓN DE CONTROLES"
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta =
                        "Descripción del Control",
                    valor =
                        analisis.controlDescripcion
                            ?: "-",
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Documento Fuente",
                    valor =
                        analisis.controlDocumento
                            ?: "-",
                    alterna = true
                )

                dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Área de ejecución",
                    valor =
                        analisis.controlArea
                            ?: "-",
                    alterna = false
                )
            }

            // =====================================================
            // PÁGINA 2
            //
            // IMPORTANTE:
            // YA NO REPETIMOS LOGO, TÍTULO, USUARIO NI FECHA.
            // =====================================================

            val pagina2 =
                PDPage(PDRectangle.A4)

            document.addPage(pagina2)

            PDPageContentStream(
                document,
                pagina2
            ).use { contenido ->

                /*
                 * Empezamos directamente desde el margen superior.
                 *
                 * No llamamos a dibujarCabecera().
                 */
                var y =
                    pagina2.mediaBox.height -
                            MARGIN

                // -------------------------------------------------
                // DISEÑO Y EJECUCIÓN
                // -------------------------------------------------

                y = dibujarTituloSeccion(
                    contenido = contenido,
                    y = y,
                    titulo = "DISEÑO Y EJECUCIÓN"
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Periodicidad",
                    valor = etiquetaEnum(
                        analisis.periodicidad
                    ),
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Operatividad",
                    valor = etiquetaEnum(
                        analisis.operatividad
                    ),
                    alterna = true
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Tipo de control",
                    valor = etiquetaEnum(
                        analisis.tipoControl
                    ),
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Supervisión",
                    valor = etiquetaEnum(
                        analisis.supervision
                    ),
                    alterna = true
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Frecuencia oportuna",
                    valor = siNo(
                        analisis.frecuenciaOportuna
                    ),
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Seguimiento adecuado",
                    valor = siNo(
                        analisis.seguimientoAdecuado
                    ),
                    alterna = true
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Mitigación",
                    valor = porcentaje(
                        analisis.mitigacion
                    ),
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Riesgo residual",
                    valor = etiquetaRiesgo(
                        analisis.riesgoResidual
                    ),
                    alterna = true
                )

                y -= ESPACIO_ANTES_HEATMAP

                // -------------------------------------------------
                // HEATMAP RESIDUAL
                // -------------------------------------------------

                y = dibujarHeatmap(
                    contenido = contenido,
                    ySuperior = y,
                    titulo =
                        "MATRIZ DE RIESGO RESIDUAL",

                    probabilidades =
                        heatmap.probabilidades.map {
                            it.codigo
                        },

                    impactos =
                        heatmap.impactos.map {
                            it.codigo
                        },

                    obtenerRiesgo = {
                            probabilidad,
                            impacto ->

                        heatmap.celdas
                            .first {
                                it.probabilidad == probabilidad &&
                                        it.impacto == impacto
                            }
                            .riesgo
                    },

                    marcadorProbabilidad =
                        analisis.probabilidadResidual,

                    marcadorImpacto =
                        analisis.impactoResidual
                )

                y -= ESPACIO_ENTRE_BLOQUES

                // -------------------------------------------------
                // TRATAMIENTO
                // -------------------------------------------------

                y = dibujarTituloSeccion(
                    contenido = contenido,
                    y = y,
                    titulo =
                        "TRATAMIENTO E IMPLEMENTACIÓN"
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Plan de acción",
                    valor =
                        analisis.planAccion
                            ?: "-",
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Área responsable",
                    valor =
                        analisis.areaResponsable
                            ?: "-",
                    alterna = true
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Fecha Inicio",
                    valor =
                        analisis.fechaInicio
                            ?.format(formatterFecha)
                            ?: "-",
                    alterna = false
                )

                y = dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Fecha Fin",
                    valor =
                        analisis.fechaCierre
                            ?.format(formatterFecha)
                            ?: "-",
                    alterna = true
                )

                dibujarFila(
                    contenido = contenido,
                    ySuperior = y,
                    etiqueta = "Estado",
                    valor = etiquetaEnum(
                        analisis.estado
                    ),
                    alterna = false
                )
            }

            ByteArrayOutputStream().use { salida ->

                document.save(salida)

                return salida.toByteArray()
            }
        }
    }

    // =========================================================
    // LOGO
    // =========================================================

    private fun cargarLogoInstitucional(
        document: PDDocument
    ): PDImageXObject {

        val imagenOriginal =
            ClassPathResource(LOGO_PATH)
                .inputStream
                .use { input ->

                    requireNotNull(
                        ImageIO.read(input)
                    ) {
                        "No se pudo leer el logo institucional"
                    }
                }

        /*
         * Elimina márgenes transparentes del PNG.
         */
        val imagenRecortada =
            recortarTransparencia(
                imagenOriginal
            )

        return LosslessFactory.createFromImage(
            document,
            imagenRecortada
        )
    }

    private fun recortarTransparencia(
        imagen: BufferedImage
    ): BufferedImage {

        var minX =
            imagen.width

        var minY =
            imagen.height

        var maxX =
            -1

        var maxY =
            -1

        /*
         * Buscamos todos los píxeles realmente visibles.
         */
        for (
        y in 0 until imagen.height
        ) {

            for (
            x in 0 until imagen.width
            ) {

                val alpha =
                    imagen.getRGB(
                        x,
                        y
                    )
                        .ushr(24) and 0xFF

                /*
                 * Ignoramos transparencia y pequeños residuos
                 * de antialiasing casi invisibles.
                 */
                if (
                    alpha > 10
                ) {

                    if (x < minX) {
                        minX = x
                    }

                    if (x > maxX) {
                        maxX = x
                    }

                    if (y < minY) {
                        minY = y
                    }

                    if (y > maxY) {
                        maxY = y
                    }
                }
            }
        }

        /*
         * Si por alguna razón no encontramos transparencia
         * utilizable, devolvemos la imagen original.
         */
        if (
            maxX < minX ||
            maxY < minY
        ) {

            return imagen
        }

        /*
         * Pequeño margen de seguridad.
         */
        val margen =
            8

        val xInicial =
            maxOf(
                0,
                minX - margen
            )

        val yInicial =
            maxOf(
                0,
                minY - margen
            )

        val xFinal =
            minOf(
                imagen.width - 1,
                maxX + margen
            )

        val yFinal =
            minOf(
                imagen.height - 1,
                maxY + margen
            )

        return imagen.getSubimage(
            xInicial,
            yInicial,
            xFinal - xInicial + 1,
            yFinal - yInicial + 1
        )
    }

    // =========================================================
    // CABECERA INSTITUCIONAL
    // SOLO SE UTILIZA EN LA PRIMERA PÁGINA
    // =========================================================

    private fun dibujarCabecera(
        contenido: PDPageContentStream,
        pagina: PDPage,
        logo: PDImageXObject,
        usuario: String,
        fecha: String
    ): Float {

        val altoPagina =
            pagina.mediaBox.height

        val anchoPagina =
            pagina.mediaBox.width

        /*
         * Logo ahora más grande.
         *
         * Como ya recortamos el espacio transparente,
         * prácticamente todo este ancho corresponde
         * al logo realmente visible.
         */
        dibujarLogo(
            contenido = contenido,
            logo = logo,
            x = MARGIN + 4f,
            ySuperior = altoPagina - 25f,
            anchoMaximo = 175f,
            altoMaximo = 78f
        )

        escribirTexto(
            contenido = contenido,
            texto = "Matriz de Riesgo",
            x = anchoPagina - 245f,
            y = altoPagina - 46f,
            tamano = 16f,
            fuente = fontBold,
            color = azulInstitucional
        )

        escribirTexto(
            contenido = contenido,
            texto =
                "Corrupción / Lavado de Activos",
            x = anchoPagina - 245f,
            y = altoPagina - 66f,
            tamano = 10f,
            fuente = fontBold,
            color = negroTexto
        )

        escribirTexto(
            contenido = contenido,
            texto =
                "Usuario: $usuario",
            x = anchoPagina - 245f,
            y = altoPagina - 84f,
            tamano = 8.5f,
            fuente = fontRegular,
            color = azul75
        )

        escribirTexto(
            contenido = contenido,
            texto =
                "Fecha: $fecha",
            x = anchoPagina - 245f,
            y = altoPagina - 99f,
            tamano = 8.5f,
            fuente = fontRegular,
            color = azul75
        )

        /*
         * Línea institucional:
         * rojo como acento + azul principal.
         */
        contenido.setLineWidth(
            2.2f
        )

        contenido.setStrokingColor(
            rojoInstitucional
        )

        contenido.moveTo(
            MARGIN,
            altoPagina - 117f
        )

        contenido.lineTo(
            MARGIN + 30f,
            altoPagina - 117f
        )

        contenido.stroke()

        contenido.setLineWidth(
            1.5f
        )

        contenido.setStrokingColor(
            azulInstitucional
        )

        contenido.moveTo(
            MARGIN + 30f,
            altoPagina - 117f
        )

        contenido.lineTo(
            anchoPagina - MARGIN,
            altoPagina - 117f
        )

        contenido.stroke()

        /*
         * Espacio debajo de la cabecera.
         */
        return altoPagina - 140f
    }

    private fun dibujarLogo(
        contenido: PDPageContentStream,
        logo: PDImageXObject,
        x: Float,
        ySuperior: Float,
        anchoMaximo: Float,
        altoMaximo: Float
    ) {

        val escala =
            min(
                anchoMaximo /
                        logo.width.toFloat(),

                altoMaximo /
                        logo.height.toFloat()
            )

        val ancho =
            logo.width *
                    escala

        val alto =
            logo.height *
                    escala

        contenido.drawImage(
            logo,
            x,
            ySuperior - alto,
            ancho,
            alto
        )
    }

    // =========================================================
    // TÍTULOS DE SECCIÓN
    // =========================================================

    private fun dibujarTituloSeccion(
        contenido: PDPageContentStream,
        y: Float,
        titulo: String
    ): Float {

        val ancho =
            PDRectangle.A4.width -
                    (MARGIN * 2)

        contenido.setNonStrokingColor(
            azulInstitucional
        )

        contenido.addRect(
            MARGIN,
            y - 19f,
            ancho,
            19f
        )

        contenido.fill()

        /*
         * Acento rojo institucional.
         */
        contenido.setNonStrokingColor(
            rojoInstitucional
        )

        contenido.addRect(
            MARGIN,
            y - 19f,
            4f,
            19f
        )

        contenido.fill()

        escribirTexto(
            contenido = contenido,
            texto = titulo,
            x = MARGIN + 10f,
            y = y - 13.5f,
            tamano = 8.8f,
            fuente = fontBold,
            color = blanco
        )

        /*
         * Antes retornábamos y - 23f.
         *
         * Ahora dejamos más espacio antes
         * de comenzar la tabla.
         */
        return y - 28f
    }

    // =========================================================
    // FILAS DE DATOS
    // =========================================================

    private fun dibujarFila(
        contenido: PDPageContentStream,
        ySuperior: Float,
        etiqueta: String,
        valor: String,
        alterna: Boolean
    ): Float {

        val ancho =
            PDRectangle.A4.width -
                    (MARGIN * 2)

        val anchoValor =
            ancho -
                    LABEL_WIDTH -
                    18f

        val lineas =
            dividirTexto(
                texto = valor,
                anchoMaximo =
                    anchoValor,
                tamano = 8.5f,
                fuente = fontRegular
            )

        val cantidadLineas =
            max(
                1,
                lineas.size
            )

        /*
         * Antes la altura mínima era 20.
         *
         * Ahora usamos 22 para que las filas
         * respiren un poco más.
         */
        val alto =
            max(
                ALTO_FILA_MINIMO,
                10f +
                        cantidadLineas *
                        10.5f
            )

        if (
            alterna
        ) {

            contenido.setNonStrokingColor(
                azul10
            )

            contenido.addRect(
                MARGIN,
                ySuperior - alto,
                ancho,
                alto
            )

            contenido.fill()
        }

        /*
         * Separador de fila.
         */
        contenido.setStrokingColor(
            azul25
        )

        contenido.setLineWidth(
            0.35f
        )

        contenido.moveTo(
            MARGIN,
            ySuperior - alto
        )

        contenido.lineTo(
            MARGIN + ancho,
            ySuperior - alto
        )

        contenido.stroke()

        /*
         * Etiqueta.
         */
        escribirTexto(
            contenido = contenido,
            texto = etiqueta,
            x = MARGIN + 6f,
            y = ySuperior - 15f,
            tamano = 8.4f,
            fuente = fontBold,
            color = negroTexto
        )

        /*
         * Valor.
         */
        lineas.forEachIndexed {
                indice,
                linea ->

            escribirTexto(
                contenido = contenido,
                texto = linea,
                x =
                    MARGIN +
                            LABEL_WIDTH,
                y =
                    ySuperior -
                            15f -
                            (
                                    indice *
                                            10.5f
                                    ),
                tamano = 8.4f,
                fuente = fontRegular,
                color = negroTexto
            )
        }

        return ySuperior -
                alto
    }

    // =========================================================
    // HEATMAP INSTITUCIONAL
    // =========================================================

    private fun dibujarHeatmap(
        contenido: PDPageContentStream,
        ySuperior: Float,
        titulo: String,
        probabilidades: List<NivelProbabilidad>,
        impactos: List<NivelImpacto>,
        obtenerRiesgo:
            (
            NivelProbabilidad,
            NivelImpacto
        ) -> NivelRiesgo,
        marcadorProbabilidad:
        NivelProbabilidad?,
        marcadorImpacto:
        NivelImpacto?
    ): Float {

        val cardX =
            MARGIN

        val cardWidth =
            PDRectangle.A4.width -
                    MARGIN * 2

        val tituloAlto =
            23f

        val paddingSuperior =
            9f

        val bandaProbabilidadAncho =
            26f

        val espacioDespuesBanda =
            7f

        val anchoEtiquetasFila =
            69f

        val espacioAntesGrid =
            7f

        val anchoCelda =
            64f

        val altoCelda =
            27f

        val anchoGrid =
            anchoCelda *
                    impactos.size

        val altoGrid =
            altoCelda *
                    probabilidades.size

        val altoEtiquetasColumnas =
            18f

        val separacionImpacto =
            6f

        val altoBandaImpacto =
            20f

        val paddingInferior =
            10f

        val altoCard =
            tituloAlto +
                    paddingSuperior +
                    altoGrid +
                    altoEtiquetasColumnas +
                    separacionImpacto +
                    altoBandaImpacto +
                    paddingInferior

        val cardBottom =
            ySuperior -
                    altoCard

        // =====================================================
        // TARJETA GENERAL
        // =====================================================

        contenido.setNonStrokingColor(
            blanco
        )

        contenido.addRect(
            cardX,
            cardBottom,
            cardWidth,
            altoCard
        )

        contenido.fill()

        contenido.setStrokingColor(
            azul25
        )

        contenido.setLineWidth(
            0.8f
        )

        contenido.addRect(
            cardX,
            cardBottom,
            cardWidth,
            altoCard
        )

        contenido.stroke()

        // =====================================================
        // CABECERA DEL HEATMAP
        // =====================================================

        contenido.setNonStrokingColor(
            azul10
        )

        contenido.addRect(
            cardX,
            ySuperior - tituloAlto,
            cardWidth,
            tituloAlto
        )

        contenido.fill()

        contenido.setNonStrokingColor(
            rojoInstitucional
        )

        contenido.addRect(
            cardX,
            ySuperior - tituloAlto,
            4f,
            tituloAlto
        )

        contenido.fill()

        escribirTexto(
            contenido = contenido,
            texto = titulo,
            x = cardX + 11f,
            y = ySuperior - 15.5f,
            tamano = 9.2f,
            fuente = fontBold,
            color = azulInstitucional
        )

        // =====================================================
        // POSICIONES
        // =====================================================

        val gridTop =
            ySuperior -
                    tituloAlto -
                    paddingSuperior

        val bandaProbX =
            cardX +
                    10f

        val etiquetasFilaX =
            bandaProbX +
                    bandaProbabilidadAncho +
                    espacioDespuesBanda

        val gridX =
            etiquetasFilaX +
                    anchoEtiquetasFila +
                    espacioAntesGrid

        val gridBottom =
            gridTop -
                    altoGrid

        // =====================================================
        // PROBABILIDAD - BANDA VERTICAL
        // =====================================================

        contenido.setNonStrokingColor(
            azul10
        )

        contenido.addRect(
            bandaProbX,
            gridBottom,
            bandaProbabilidadAncho,
            altoGrid
        )

        contenido.fill()

        contenido.setStrokingColor(
            azul25
        )

        contenido.setLineWidth(
            0.6f
        )

        contenido.addRect(
            bandaProbX,
            gridBottom,
            bandaProbabilidadAncho,
            altoGrid
        )

        contenido.stroke()

        escribirTextoVerticalCentrado(
            contenido = contenido,
            texto = "PROBABILIDAD",
            x = bandaProbX,
            y = gridBottom,
            anchoBanda =
                bandaProbabilidadAncho,
            altoBanda =
                altoGrid,
            tamano = 8.2f,
            fuente = fontBold,
            color =
                azulInstitucional
        )

        // =====================================================
        // CELDAS
        // =====================================================

        probabilidades.forEachIndexed {
                indiceFila,
                probabilidad ->

            val yCeldaSuperior =
                gridTop -
                        indiceFila *
                        altoCelda

            val yTexto =
                yCeldaSuperior -
                        altoCelda /
                        2f -
                        2.6f

            escribirTextoDerecha(
                contenido = contenido,
                texto =
                    etiquetaEnum(
                        probabilidad
                    ),
                x =
                    etiquetasFilaX,
                y =
                    yTexto,
                ancho =
                    anchoEtiquetasFila -
                            4f,
                tamano = 7.2f,
                fuente = fontBold,
                color = negroTexto
            )

            impactos.forEachIndexed {
                    indiceColumna,
                    impacto ->

                val x =
                    gridX +
                            indiceColumna *
                            anchoCelda

                val riesgo =
                    obtenerRiesgo(
                        probabilidad,
                        impacto
                    )

                /*
                 * Colores funcionales originales:
                 * NO se modifican.
                 */
                contenido.setNonStrokingColor(
                    colorRiesgo(
                        riesgo
                    )
                )

                contenido.addRect(
                    x,
                    yCeldaSuperior -
                            altoCelda,
                    anchoCelda,
                    altoCelda
                )

                contenido.fill()

                /*
                 * Separador blanco.
                 */
                contenido.setStrokingColor(
                    blanco
                )

                contenido.setLineWidth(
                    0.85f
                )

                contenido.addRect(
                    x,
                    yCeldaSuperior -
                            altoCelda,
                    anchoCelda,
                    altoCelda
                )

                contenido.stroke()

                /*
                 * Marcador.
                 */
                if (
                    probabilidad ==
                    marcadorProbabilidad &&
                    impacto ==
                    marcadorImpacto
                ) {

                    dibujarMarcador(
                        contenido =
                            contenido,
                        centroX =
                            x +
                                    anchoCelda /
                                    2f,
                        centroY =
                            yCeldaSuperior -
                                    altoCelda /
                                    2f
                    )
                }
            }
        }

        // =====================================================
        // ETIQUETAS DE IMPACTO
        // =====================================================

        impactos.forEachIndexed {
                indice,
                impacto ->

            val x =
                gridX +
                        indice *
                        anchoCelda

            escribirTextoCentrado(
                contenido = contenido,
                texto =
                    etiquetaEnum(
                        impacto
                    ),
                x = x,
                y =
                    gridBottom -
                            12f,
                ancho =
                    anchoCelda,
                tamano = 6.4f,
                fuente = fontBold,
                color = negroTexto
            )
        }

        // =====================================================
        // IMPACTO - BANDA HORIZONTAL
        // =====================================================

        val impactoBandY =
            gridBottom -
                    altoEtiquetasColumnas -
                    separacionImpacto -
                    altoBandaImpacto

        contenido.setNonStrokingColor(
            azul10
        )

        contenido.addRect(
            gridX,
            impactoBandY,
            anchoGrid,
            altoBandaImpacto
        )

        contenido.fill()

        contenido.setStrokingColor(
            azul25
        )

        contenido.setLineWidth(
            0.6f
        )

        contenido.addRect(
            gridX,
            impactoBandY,
            anchoGrid,
            altoBandaImpacto
        )

        contenido.stroke()

        escribirTextoCentrado(
            contenido = contenido,
            texto = "IMPACTO",
            x = gridX,
            y =
                impactoBandY +
                        6.3f,
            ancho =
                anchoGrid,
            tamano = 8.2f,
            fuente = fontBold,
            color =
                azulInstitucional
        )

        /*
         * Dejamos un poco de aire al terminar
         * la tarjeta.
         */
        return cardBottom -
                9f
    }

    // =========================================================
    // MARCADOR
    // =========================================================

    private fun dibujarMarcador(
        contenido: PDPageContentStream,
        centroX: Float,
        centroY: Float
    ) {

        /*
         * Aro blanco.
         */
        dibujarCirculo(
            contenido =
                contenido,
            centroX =
                centroX,
            centroY =
                centroY,
            radio = 7f,
            color = blanco
        )

        /*
         * Centro azul institucional.
         */
        dibujarCirculo(
            contenido =
                contenido,
            centroX =
                centroX,
            centroY =
                centroY,
            radio = 5.2f,
            color =
                azulInstitucional
        )
    }

    private fun dibujarCirculo(
        contenido: PDPageContentStream,
        centroX: Float,
        centroY: Float,
        radio: Float,
        color: Color
    ) {

        val k =
            0.55228475f

        contenido.setNonStrokingColor(
            color
        )

        contenido.moveTo(
            centroX + radio,
            centroY
        )

        contenido.curveTo(
            centroX + radio,
            centroY + k * radio,
            centroX + k * radio,
            centroY + radio,
            centroX,
            centroY + radio
        )

        contenido.curveTo(
            centroX - k * radio,
            centroY + radio,
            centroX - radio,
            centroY + k * radio,
            centroX - radio,
            centroY
        )

        contenido.curveTo(
            centroX - radio,
            centroY - k * radio,
            centroX - k * radio,
            centroY - radio,
            centroX,
            centroY - radio
        )

        contenido.curveTo(
            centroX + k * radio,
            centroY - radio,
            centroX + radio,
            centroY - k * radio,
            centroX + radio,
            centroY
        )

        contenido.fill()
    }

    // =========================================================
    // TEXTO NORMAL
    // =========================================================

    private fun escribirTexto(
        contenido: PDPageContentStream,
        texto: String,
        x: Float,
        y: Float,
        tamano: Float,
        fuente: PDType1Font,
        color: Color
    ) {

        contenido.beginText()

        contenido.setNonStrokingColor(
            color
        )

        contenido.setFont(
            fuente,
            tamano
        )

        contenido.newLineAtOffset(
            x,
            y
        )

        contenido.showText(
            limpiarTexto(
                texto
            )
        )

        contenido.endText()
    }

    // =========================================================
    // TEXTO CENTRADO
    // =========================================================

    private fun escribirTextoCentrado(
        contenido: PDPageContentStream,
        texto: String,
        x: Float,
        y: Float,
        ancho: Float,
        tamano: Float,
        fuente: PDType1Font,
        color: Color
    ) {

        val textoLimpio =
            limpiarTexto(
                texto
            )

        val anchoTexto =
            fuente
                .getStringWidth(
                    textoLimpio
                ) /
                    1000f *
                    tamano

        escribirTexto(
            contenido = contenido,
            texto = textoLimpio,
            x =
                x +
                        (
                                ancho -
                                        anchoTexto
                                ) /
                        2f,
            y = y,
            tamano = tamano,
            fuente = fuente,
            color = color
        )
    }

    // =========================================================
    // TEXTO DERECHA
    // =========================================================

    private fun escribirTextoDerecha(
        contenido: PDPageContentStream,
        texto: String,
        x: Float,
        y: Float,
        ancho: Float,
        tamano: Float,
        fuente: PDType1Font,
        color: Color
    ) {

        val textoLimpio =
            limpiarTexto(
                texto
            )

        val anchoTexto =
            fuente
                .getStringWidth(
                    textoLimpio
                ) /
                    1000f *
                    tamano

        escribirTexto(
            contenido = contenido,
            texto = textoLimpio,
            x =
                x +
                        ancho -
                        anchoTexto,
            y = y,
            tamano = tamano,
            fuente = fuente,
            color = color
        )
    }

    // =========================================================
    // TEXTO VERTICAL
    // =========================================================

    private fun escribirTextoVerticalCentrado(
        contenido: PDPageContentStream,
        texto: String,
        x: Float,
        y: Float,
        anchoBanda: Float,
        altoBanda: Float,
        tamano: Float,
        fuente: PDType1Font,
        color: Color
    ) {

        val textoLimpio =
            limpiarTexto(
                texto
            )

        val longitudTexto =
            fuente
                .getStringWidth(
                    textoLimpio
                ) /
                    1000f *
                    tamano

        val origenX =
            x +
                    anchoBanda /
                    2f +
                    tamano /
                    3f

        val origenY =
            y +
                    (
                            altoBanda -
                                    longitudTexto
                            ) /
                    2f

        contenido.beginText()

        contenido.setNonStrokingColor(
            color
        )

        contenido.setFont(
            fuente,
            tamano
        )

        contenido.setTextMatrix(
            Matrix.getRotateInstance(
                Math.toRadians(
                    90.0
                ),
                origenX,
                origenY
            )
        )

        contenido.showText(
            textoLimpio
        )

        contenido.endText()
    }

    // =========================================================
    // WRAP DE TEXTO
    // =========================================================

    private fun dividirTexto(
        texto: String,
        anchoMaximo: Float,
        tamano: Float,
        fuente: PDType1Font
    ): List<String> {

        val limpio =
            limpiarTexto(
                texto
            )

        val palabras =
            limpio.split(" ")

        val lineas =
            mutableListOf<String>()

        var linea =
            StringBuilder()

        palabras.forEach {
                palabra ->

            val candidato =
                if (
                    linea.isEmpty()
                ) {
                    palabra
                } else {
                    "$linea $palabra"
                }

            val ancho =
                fuente
                    .getStringWidth(
                        candidato
                    ) /
                        1000f *
                        tamano

            if (
                ancho >
                anchoMaximo &&
                linea.isNotEmpty()
            ) {

                lineas.add(
                    linea.toString()
                )

                linea =
                    StringBuilder(
                        palabra
                    )

            } else {

                if (
                    linea.isNotEmpty()
                ) {

                    linea.append(
                        " "
                    )
                }

                linea.append(
                    palabra
                )
            }
        }

        if (
            linea.isNotEmpty()
        ) {

            lineas.add(
                linea.toString()
            )
        }

        return if (
            lineas.isEmpty()
        ) {
            listOf("-")
        } else {
            lineas
        }
    }

    // =========================================================
    // COLORES FUNCIONALES DEL HEATMAP
    // NO MODIFICAR
    // =========================================================

    private fun colorRiesgo(
        riesgo: NivelRiesgo
    ): Color {

        return when (
            riesgo
        ) {

            NivelRiesgo.MINIMO ->
                Color(
                    91,
                    169,
                    224
                )

            NivelRiesgo.LEVE ->
                Color(
                    67,
                    160,
                    71
                )

            NivelRiesgo.MODERADO ->
                Color(
                    255,
                    235,
                    59
                )

            NivelRiesgo.ALTO ->
                Color(
                    255,
                    152,
                    0
                )

            NivelRiesgo.MUY_ALTO ->
                Color(
                    244,
                    67,
                    54
                )
        }
    }

    // =========================================================
    // ETIQUETAS DE RIESGO
    // =========================================================

    private fun etiquetaRiesgo(
        riesgo: NivelRiesgo?
    ): String {

        return when (
            riesgo
        ) {

            NivelRiesgo.MINIMO ->
                "Muy Bajo"

            NivelRiesgo.LEVE ->
                "Bajo"

            NivelRiesgo.MODERADO ->
                "Medio"

            NivelRiesgo.ALTO ->
                "Alto"

            NivelRiesgo.MUY_ALTO ->
                "Extremo"

            null ->
                "-"
        }
    }

    // =========================================================
    // ENUMS
    // =========================================================

    private fun etiquetaEnum(
        valor: Enum<*>?
    ): String {

        if (
            valor == null
        ) {
            return "-"
        }

        return valor.name
            .lowercase()
            .split("_")
            .joinToString(
                " "
            ) {
                    palabra ->

                palabra
                    .replaceFirstChar {
                            caracter ->

                        caracter.uppercase()
                    }
            }
            .replace(
                "Tecnologia",
                "Tecnología"
            )
            .replace(
                "Automatico",
                "Automático"
            )
            .replace(
                "Semiautomatico",
                "Semiautomático"
            )
            .replace(
                "Periodico",
                "Periódico"
            )
            .replace(
                "Catastrofico",
                "Catastrófico"
            )
    }

    // =========================================================
    // BOOLEANOS
    // =========================================================

    private fun siNo(
        valor: Boolean?
    ): String {

        return when (
            valor
        ) {

            true ->
                "Sí"

            false ->
                "No"

            null ->
                "-"
        }
    }

    // =========================================================
    // PORCENTAJE
    // =========================================================

    private fun porcentaje(
        valor: BigDecimal?
    ): String {

        if (
            valor == null
        ) {
            return "-"
        }

        return "${
            valor
                .multiply(
                    BigDecimal("100")
                )
                .stripTrailingZeros()
                .toPlainString()
        } %"
    }

    // =========================================================
    // MONTO
    // =========================================================

    private fun formatearMonto(
        valor: BigDecimal?
    ): String {

        return valor
            ?.let {
                formatterMonto.format(
                    it
                )
            }
            ?: "-"
    }

    // =========================================================
    // LIMPIEZA DE TEXTO
    // =========================================================

    private fun limpiarTexto(
        texto: String
    ): String {

        return texto
            .replace(
                "\n",
                " "
            )
            .replace(
                "\r",
                " "
            )
            .replace(
                "\t",
                " "
            )
    }
}