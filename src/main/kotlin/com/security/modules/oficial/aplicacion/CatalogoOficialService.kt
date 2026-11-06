package com.security.modules.oficial.aplicacion

import com.security.modules.oficial.dominio.AmbitoAdjunto
import com.security.modules.oficial.dominio.ConNombre
import com.security.modules.oficial.dominio.Dedicacion
import com.security.modules.oficial.dominio.DocumentoDesignacion
import com.security.modules.oficial.dominio.MotivoRemocion
import com.security.modules.oficial.dominio.QuienDesigna
import com.security.modules.oficial.dominio.ResultadoUif
import com.security.modules.oficial.dominio.TextosOc
import com.security.modules.oficial.dominio.TipoCargo
import com.security.modules.oficial.dominio.TipoDocumentoOc
import com.security.modules.oficial.dominio.TipoEmpresaOc
import com.security.modules.oficial.dominio.Vinculo
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

data class OpcionOc(val codigo: String, val nombre: String)
data class OpcionDisponible(val codigo: String, val nombre: String, val disponible: Boolean)
data class OpcionTipoEmpresa(val codigo: String, val nombre: String, val quienDesigna: String, val designantesPermitidos: List<String>)
data class OpcionMotivo(val codigo: String, val nombre: String, val ayuda: String, val disponible: Boolean)
data class PuntoDeclaracion(val letra: String, val texto: String)
data class TextoDeclaracion(val texto: String)
data class ObligacionOc(val nombre: String, val frecuencia: String)
data class ReglasAdjuntos(val extensiones: List<String>, val tamanoMaximoBytes: Long)
data class InfoFeriados(val cargados: Long, val cuentaFeriados: Boolean)
data class ProntoOc(
    val guiaRequisitos: Boolean = true,
    val designarAlterno: Boolean = true,
    val ausenciaTemporal: Boolean = true,
    val corporativo: Boolean = true,
    val pdfDeclaracionDesignante: Boolean = true,
    val avisos: Boolean = true
)

data class CatalogosOcResponse(
    val tiposCargo: List<OpcionDisponible>,
    val dedicaciones: List<OpcionOc>,
    val tiposEmpresa: List<OpcionTipoEmpresa>,
    val nacionalidades: List<OpcionOc>,
    val nacionalidadInicial: String,
    val tiposDocumento: List<OpcionOc>,
    val tipoDocumentoInicial: String,
    val vinculos: List<OpcionOc>,
    val quienDesigna: List<OpcionOc>,
    val documentosDesignacion: List<OpcionOc>,
    val resultadosUif: List<OpcionOc>,
    val motivosRemocion: List<OpcionMotivo>,
    val ambitosAdjunto: List<OpcionOc>,
    val reglas: List<ReglaResponse>,
    val declaracionRequisitos: List<PuntoDeclaracion>,
    val declaracionRequisitosPuntoH: Map<String, String>,
    val declaracionDesignante: TextoDeclaracion,
    val obligaciones: List<ObligacionOc>,
    val adjuntos: ReglasAdjuntos,
    val feriados: InfoFeriados,
    val pronto: ProntoOc
)

const val TAMANO_MAXIMO_ADJUNTO = 10L * 1024L * 1024L

@Service
class CatalogoOficialService(private val reglas: ReglasOficialService) {

    /** Paises ISO 3166 (alfa-2 + nombre en español), Peru primero: el mismo archivo que usa Scoring. */
    val paises: List<OpcionOc> by lazy { leerPaises() }

    fun nombrePais(codigo: String?): String? = paises.firstOrNull { it.codigo == codigo }?.nombre

    fun existePais(codigo: String?): Boolean = paises.any { it.codigo == codigo }

    fun catalogos(): CatalogosOcResponse {
        val feriados = reglas.cantidadFeriados()
        return CatalogosOcResponse(
            tiposCargo = TipoCargo.entries.map { OpcionDisponible(it.name, it.nombre, it.disponible) },
            dedicaciones = opciones(Dedicacion.entries),
            tiposEmpresa = TipoEmpresaOc.entries.map { OpcionTipoEmpresa(it.name, it.nombre, it.quienDesigna, it.designantes) },
            nacionalidades = paises,
            nacionalidadInicial = "PE",
            tiposDocumento = opciones(TipoDocumentoOc.entries),
            tipoDocumentoInicial = TipoDocumentoOc.DNI.name,
            vinculos = opciones(Vinculo.entries),
            quienDesigna = opciones(QuienDesigna.entries),
            documentosDesignacion = opciones(DocumentoDesignacion.entries),
            resultadosUif = opciones(ResultadoUif.entries),
            motivosRemocion = MotivoRemocion.entries.map { OpcionMotivo(it.name, it.nombre, it.ayuda, it.disponible) },
            ambitosAdjunto = opciones(AmbitoAdjunto.entries),
            reglas = reglas.reglas(),
            declaracionRequisitos = TextosOc.PUNTOS_REQUISITOS.map { PuntoDeclaracion(it.letra, it.texto) },
            declaracionRequisitosPuntoH = Vinculo.entries.associate { it.name to TextosOc.puntoH(it) },
            declaracionDesignante = TextoDeclaracion(TextosOc.DJ_DESIGNANTE),
            obligaciones = TextosOc.OBLIGACIONES.map { ObligacionOc(it.nombre, it.frecuencia) },
            adjuntos = ReglasAdjuntos(listOf("pdf", "doc", "docx"), TAMANO_MAXIMO_ADJUNTO),
            feriados = InfoFeriados(feriados, feriados > 0),
            pronto = ProntoOc()
        )
    }

    private fun opciones(lista: List<ConNombre>) = lista.map { OpcionOc(it.codigo, it.nombre) }

    private fun leerPaises(): List<OpcionOc> {
        val texto = ClassPathResource("scoring/paises-iso.csv").inputStream.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
        return texto.removePrefix("﻿").lines()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { linea ->
                val partes = linea.split(",")
                if (partes.size < 4) null else OpcionOc(partes[0].trim().uppercase(), partes[3].trim())
            }
            .distinctBy { it.codigo }
    }
}
