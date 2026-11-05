package com.security.modules.listas.domain.reglas

import java.net.URI
import java.text.Normalizer
import java.time.LocalDate

/*
 * Calculo del resultado de una consulta a Listas Negativas (ver docs/LISTAS-NEGATIVAS.md). Es puro: no toca la base
 * ni la red. Los pesos, los grupos de peso, las filas, las listas que las alimentan y la regla de "otras listas" llegan
 * como datos (ReglasListas, que sale de las tablas listas_grupo_peso, listas_regla y listas_regla_otras).
 */

/** Como se cuentan los registros de una fila: la palabra del conteo y si son noticias en medios. */
enum class TipoConteo(val singular: String, val plural: String, val esNoticiaEnMedios: Boolean) {
    REGISTROS("REGISTRO", "REGISTROS", false),
    NOTAS("NOTA", "NOTAS", false),
    NOTICIAS("NOTICIA", "NOTICIAS", true);

    fun etiqueta(n: Int) = "$n ${if (n == 1) singular else plural}"

    companion object {
        fun de(valor: String?): TipoConteo = entries.firstOrNull { it.name.equals(valor?.trim(), ignoreCase = true) } ?: REGISTROS
    }
}

data class GrupoPesoDef(
    val codigo: String,
    val nombre: String,
    val peso: Int,
    val orden: Int,
    /** true = una coincidencia en una fila de este grupo hace la consulta "Coincidencia critica". */
    val critica: Boolean
)

data class FilaDef(
    val codigo: String,
    val nombre: String,
    val subtitulo: String?,
    val orden: Int,
    val fuente: String,
    val fuenteNombre: String,
    val grupoPeso: String,
    /** false = "Pronto": la fila se muestra apagada y nunca cuenta. */
    val consultable: Boolean,
    val motivoPronto: String?,
    /** true = la alimentan todas las listas marcadas es_pep. */
    val todasPep: Boolean,
    /** Nombres de las listas (tipo_lista.nombre) que la alimentan; se comparan sin mayusculas ni tildes. */
    val listas: List<String>,
    val tipoConteo: TipoConteo,
    /** Como se nombra en el aviso ("Coincidencia detectada: PEP · Lista de investigados"). */
    val etiquetaAviso: String,
    /** Como se nombra en la suma ("Suma 30% (PEP 10 + investigados 10 ...)"). */
    val etiquetaSuma: String
)

/** Otras listas: el grupo de colorimetria (lista_grupo.orden) define su grupo de peso. */
data class OtrasDef(val ordenGrupo: Int, val grupoPeso: String)

data class ReglasListas(
    val gruposPeso: List<GrupoPesoDef>,
    val filas: List<FilaDef>,
    val otras: List<OtrasDef>
)

/** Una lista de tipo_lista, con lo justo para saber si una fila tiene fuente y cuando se cargo por ultima vez. */
data class ListaDisponible(val id: Int?, val nombre: String, val esPep: Boolean, val ultimaCarga: LocalDate?)

/** Un registro de la persona en una lista, tal como se guarda en la consulta (no cambia si la lista se recarga). */
data class RegistroLista(
    val id: Int? = null,
    val lista: String? = null,
    val esPep: Boolean = false,
    val descripcion: String? = null,
    val link: String? = null,
    val dominio: String? = null,
    val fechaRegistro: LocalDate? = null,
    val fechaHasta: LocalDate? = null,
    val institucion: String? = null,
    val cargo: String? = null,
    val tipoPep: String? = null,
    val periodoDesde: String? = null,
    val periodoHasta: String? = null,
    /** Solo PEP: true si el registro no indica periodo. */
    val sinPeriodo: Boolean = false
)

/** Una aparicion de la persona en una lista, con la lista y su grupo de colorimetria. */
data class AparicionConsulta(
    val tipoListaId: Int?,
    val nombreLista: String?,
    val esPep: Boolean,
    val ordenGrupo: Int?,
    val nombreGrupo: String?,
    val registro: RegistroLista
)

object EstadoFila {
    const val COINCIDENCIA = "COINCIDENCIA"
    const val SIN_COINCIDENCIAS = "SIN_COINCIDENCIAS"
    const val PRONTO = "PRONTO"
}

object Clasificacion {
    const val SIN_COINCIDENCIAS = "SIN_COINCIDENCIAS"
    const val MEDIA = "MEDIA"
    const val CRITICA = "CRITICA"

    fun nombre(c: String?): String = when (c) {
        SIN_COINCIDENCIAS -> "Sin coincidencias"
        MEDIA -> "Coincidencia media"
        CRITICA -> "Coincidencia crítica"
        else -> "Sin dato"
    }

    fun corto(c: String?): String = when (c) {
        SIN_COINCIDENCIAS -> "Sin coincidencias"
        MEDIA -> "Media"
        CRITICA -> "Crítica"
        else -> "Sin dato"
    }
}

data class FilaResultado(
    val codigo: String,
    val nombre: String,
    val subtitulo: String?,
    val orden: Int,
    val fuente: String,
    val fuenteNombre: String,
    val grupoPeso: String,
    val peso: Int,
    /** COINCIDENCIA, SIN_COINCIDENCIAS o PRONTO. */
    val estado: String,
    /** Por que esta "Pronto" (null si se consulta). */
    val motivo: String?,
    val registros: Int,
    val tipoConteo: TipoConteo,
    /** "3 REGISTROS", "1 NOTA", "SIN COINCIDENCIAS" o "PRONTO". */
    val etiquetaConteo: String,
    /** Fecha de la ultima carga de sus listas (la mas reciente); null = no se sabe y no se muestra. */
    val actualizada: LocalDate?,
    /** true = fila de "Otras listas" (una lista que no alimenta ninguna fila del diseño). */
    val otraLista: Boolean,
    val avisos: List<String>,
    val items: List<RegistroLista>
)

data class FuenteResultado(val codigo: String, val nombre: String, val estado: String)

data class AvisoResultado(
    /** SIN_COINCIDENCIAS, MEDIA o CRITICA */
    val tono: String,
    val titulo: String,
    val fuentes: List<String>,
    /** "Suma 30% (PEP 10 + investigados 10 + noticias 5 + 5): clasificación Media." (null sin coincidencias) */
    val suma: String?,
    val texto: String
)

data class ResultadoListas(
    val porcentaje: Int,
    val clasificacion: String,
    val clasificacionNombre: String,
    val listasConsultadas: Int,
    val listasConCoincidencia: Int,
    val registrosEnListas: Int,
    val noticiasEnMedios: Int,
    val esPep: Boolean,
    /** La ultima carga mas reciente entre las listas con fecha; null = no se sabe. */
    val fechaBase: LocalDate?,
    val leyenda: String,
    val filas: List<FilaResultado>,
    val fuentes: List<FuenteResultado>,
    val aviso: AvisoResultado
) {
    fun nombresConCoincidencia(): List<String> = filas.filter { it.estado == EstadoFila.COINCIDENCIA }.map { it.nombre }
}

/** Una fila tal como se ve antes de consultar (catalogo de la pantalla). */
data class FilaCatalogo(
    val codigo: String,
    val nombre: String,
    val subtitulo: String?,
    val orden: Int,
    val fuente: String,
    val fuenteNombre: String,
    val grupoPeso: String,
    val peso: Int,
    val disponible: Boolean,
    val motivo: String?,
    val actualizada: LocalDate?
)

object CalculoListas {

    const val PORCENTAJE_MAXIMO = 100
    const val FUENTE_OTRAS = "OTRAS"
    const val FUENTE_OTRAS_NOMBRE = "Otras listas"
    const val MOTIVO_SIN_FUENTE = "Ninguna de las listas de esta fila está cargada en la base: no se puede consultar."
    const val AVISO_PEP_SIN_PERIODO =
        "Los registros no indican período. Sin fechas no se puede saber si sigue en funciones o si dejó de ser PEP hace más de 5 años."

    /** Mayusculas, sin tildes y con un solo espacio entre palabras. */
    fun normalizar(texto: String?): String {
        if (texto == null) return ""
        val sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return sinTildes.uppercase().replace(Regex("\\s+"), " ").trim()
    }

    /** true si el texto del periodo no dice nada (vacio o "NO INDICA"). */
    fun periodoVacio(valor: String?): Boolean {
        val v = normalizar(valor)
        return v.isEmpty() || v == "NO INDICA" || v == "-" || v == "SIN PERIODO"
    }

    /** El dominio del enlace de una noticia ("diario.example.pe"); null si no es un enlace valido. */
    fun dominio(link: String?): String? {
        val l = link?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            URI(if (l.contains("://")) l else "https://$l").host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private class Contexto(reglas: ReglasListas, listas: List<ListaDisponible>) {
        val grupos = reglas.gruposPeso.associateBy { it.codigo.uppercase() }
        val porNombre = listas.groupBy { normalizar(it.nombre) }
        val pep = listas.filter { it.esPep }
        val otras = reglas.otras.associate { it.ordenGrupo to it.grupoPeso }
        val filas = reglas.filas.sortedBy { it.orden }

        fun grupo(codigo: String?): GrupoPesoDef? = codigo?.let { grupos[it.trim().uppercase()] }
        fun peso(codigo: String?): Int = grupo(codigo)?.peso ?: 0
        fun critica(codigo: String?): Boolean = grupo(codigo)?.critica == true

        /** Las listas de la base que alimentan la fila. */
        fun fuentesDe(f: FilaDef): List<ListaDisponible> {
            val nombradas = f.listas.flatMap { porNombre[normalizar(it)].orEmpty() }
            return ((if (f.todasPep) pep else emptyList()) + nombradas).distinct()
        }

        /** La fila se consulta de verdad: no esta "Pronto" y al menos una de sus listas existe. */
        fun disponible(f: FilaDef): Boolean = f.consultable && fuentesDe(f).isNotEmpty()

        fun motivo(f: FilaDef): String? = when {
            !f.consultable -> f.motivoPronto ?: "Todavía no hay una fuente de datos para esta fila."
            fuentesDe(f).isEmpty() -> MOTIVO_SIN_FUENTE
            else -> null
        }

        fun actualizada(f: FilaDef): LocalDate? = fuentesDe(f).mapNotNull { it.ultimaCarga }.maxOrNull()

        fun alimenta(f: FilaDef, a: AparicionConsulta): Boolean =
            (f.todasPep && a.esPep) || f.listas.any { normalizar(it) == normalizar(a.nombreLista) }
    }

    /** Las filas antes de consultar: cuales se consultan y cuales estan "Pronto". */
    fun catalogo(reglas: ReglasListas, listas: List<ListaDisponible>): List<FilaCatalogo> {
        val ctx = Contexto(reglas, listas)
        return ctx.filas.map { f ->
            FilaCatalogo(
                codigo = f.codigo, nombre = f.nombre, subtitulo = f.subtitulo, orden = f.orden, fuente = f.fuente,
                fuenteNombre = f.fuenteNombre, grupoPeso = f.grupoPeso, peso = ctx.peso(f.grupoPeso),
                disponible = ctx.disponible(f), motivo = ctx.motivo(f), actualizada = ctx.actualizada(f)
            )
        }
    }

    /** Las listas que las reglas nombran y que no existen en tipo_lista (para el log del arranque). */
    fun listasFaltantes(reglas: ReglasListas, listas: List<ListaDisponible>): List<Pair<String, String>> {
        val ctx = Contexto(reglas, listas)
        return ctx.filas.filter { it.consultable }.flatMap { f ->
            val faltan = f.listas.filter { ctx.porNombre[normalizar(it)].isNullOrEmpty() }.map { f.codigo to it }
            if (f.todasPep && ctx.pep.isEmpty()) faltan + (f.codigo to "(ninguna lista marcada es_pep)") else faltan
        }
    }

    /** "Críticas 15% · Altas 10% · Informativas 5%" (solo los grupos que pesan). */
    fun leyenda(reglas: ReglasListas): String =
        reglas.gruposPeso.filter { it.peso > 0 }.sortedBy { it.orden }.joinToString(" · ") { "${it.nombre} ${it.peso}%" }

    fun calcular(reglas: ReglasListas, listas: List<ListaDisponible>, apariciones: List<AparicionConsulta>): ResultadoListas {
        val ctx = Contexto(reglas, listas)
        val disponibles = ctx.filas.filter { ctx.disponible(it) }

        // Cada aparicion va a la primera fila disponible que la alimenta; si ninguna, a "Otras listas" (no se pierde).
        val porFila = mutableMapOf<String, MutableList<AparicionConsulta>>()
        val sueltas = mutableListOf<AparicionConsulta>()
        apariciones.forEach { a ->
            val fila = disponibles.firstOrNull { ctx.alimenta(it, a) }
            if (fila != null) porFila.getOrPut(fila.codigo) { mutableListOf() }.add(a) else sueltas.add(a)
        }

        val filas = ctx.filas.map { f ->
            val propias = porFila[f.codigo].orEmpty()
            val disponible = ctx.disponible(f)
            val estado = when {
                !disponible -> EstadoFila.PRONTO
                propias.isNotEmpty() -> EstadoFila.COINCIDENCIA
                else -> EstadoFila.SIN_COINCIDENCIAS
            }
            val items = propias.map { it.registro }
            FilaResultado(
                codigo = f.codigo, nombre = f.nombre, subtitulo = f.subtitulo, orden = f.orden,
                fuente = f.fuente, fuenteNombre = f.fuenteNombre, grupoPeso = f.grupoPeso, peso = ctx.peso(f.grupoPeso),
                estado = estado, motivo = ctx.motivo(f), registros = items.size, tipoConteo = f.tipoConteo,
                etiquetaConteo = etiquetaConteo(estado, items.size, f.tipoConteo), actualizada = ctx.actualizada(f),
                otraLista = false, avisos = avisosDe(items), items = items
            )
        } + otrasFilas(ctx, sueltas, ctx.filas.maxOfOrNull { it.orden } ?: 0)

        val conCoincidencia = filas.filter { it.estado == EstadoFila.COINCIDENCIA }
        val porcentaje = minOf(PORCENTAJE_MAXIMO, conCoincidencia.sumOf { it.peso })
        val clasificacion = when {
            conCoincidencia.isEmpty() -> Clasificacion.SIN_COINCIDENCIAS
            conCoincidencia.any { ctx.critica(it.grupoPeso) } -> Clasificacion.CRITICA
            else -> Clasificacion.MEDIA
        }
        val consultadas = filas.count { it.estado != EstadoFila.PRONTO }
        val esPep = apariciones.any { it.esPep }

        return ResultadoListas(
            porcentaje = porcentaje,
            clasificacion = clasificacion,
            clasificacionNombre = Clasificacion.nombre(clasificacion),
            listasConsultadas = consultadas,
            listasConCoincidencia = conCoincidencia.size,
            registrosEnListas = conCoincidencia.filter { !it.tipoConteo.esNoticiaEnMedios }.sumOf { it.registros },
            noticiasEnMedios = conCoincidencia.filter { it.tipoConteo.esNoticiaEnMedios }.sumOf { it.registros },
            esPep = esPep,
            fechaBase = listas.mapNotNull { it.ultimaCarga }.maxOrNull(),
            leyenda = leyenda(reglas),
            filas = filas,
            fuentes = fuentes(filas),
            aviso = aviso(
                clasificacion, porcentaje, conCoincidencia, consultadas, esPep,
                reglas.filas.associate { it.codigo to (it.etiquetaAviso to it.etiquetaSuma) }
            )
        )
    }

    private fun otrasFilas(ctx: Contexto, sueltas: List<AparicionConsulta>, ordenBase: Int): List<FilaResultado> =
        sueltas.groupBy { it.tipoListaId?.toString() ?: normalizar(it.nombreLista) }.values
            .sortedWith(compareBy({ it.first().ordenGrupo ?: Int.MAX_VALUE }, { normalizar(it.first().nombreLista) }))
            .mapIndexed { i, grupo ->
                val a = grupo.first()
                val grupoPeso = a.ordenGrupo?.let { ctx.otras[it] } ?: ctx.grupos.values.minByOrNull { it.peso }?.codigo ?: ""
                val items = grupo.map { it.registro }
                val nombre = a.nombreLista?.takeIf { it.isNotBlank() } ?: "Lista sin nombre"
                FilaResultado(
                    codigo = "OTRA_${a.tipoListaId ?: (i + 1)}", nombre = nombre, subtitulo = a.nombreGrupo?.uppercase(),
                    orden = ordenBase + 1 + i, fuente = FUENTE_OTRAS, fuenteNombre = FUENTE_OTRAS_NOMBRE,
                    grupoPeso = grupoPeso, peso = ctx.peso(grupoPeso), estado = EstadoFila.COINCIDENCIA, motivo = null,
                    registros = items.size, tipoConteo = TipoConteo.REGISTROS,
                    etiquetaConteo = TipoConteo.REGISTROS.etiqueta(items.size),
                    actualizada = ctx.porNombre[normalizar(a.nombreLista)].orEmpty().mapNotNull { it.ultimaCarga }.maxOrNull(),
                    otraLista = true, avisos = avisosDe(items), items = items
                )
            }

    private fun etiquetaConteo(estado: String, n: Int, tipo: TipoConteo) = when (estado) {
        EstadoFila.COINCIDENCIA -> tipo.etiqueta(n)
        EstadoFila.SIN_COINCIDENCIAS -> "SIN COINCIDENCIAS"
        else -> "PRONTO"
    }

    private fun avisosDe(items: List<RegistroLista>): List<String> =
        if (items.any { it.esPep && it.sinPeriodo }) listOf(AVISO_PEP_SIN_PERIODO) else emptyList()

    /** Un chip por fuente, en el orden de su primera fila: con coincidencia, consultada sin coincidencias o "Pronto". */
    private fun fuentes(filas: List<FilaResultado>): List<FuenteResultado> =
        filas.groupBy { it.fuente }.map { (codigo, fs) ->
            val estado = when {
                fs.any { it.estado == EstadoFila.COINCIDENCIA } -> EstadoFila.COINCIDENCIA
                fs.any { it.estado == EstadoFila.SIN_COINCIDENCIAS } -> EstadoFila.SIN_COINCIDENCIAS
                else -> EstadoFila.PRONTO
            }
            FuenteResultado(codigo, fs.first().fuenteNombre, estado)
        }

    /** "PEP 10 + investigados 10 + noticias 5 + 5": filas seguidas con la misma etiqueta se juntan. */
    fun textoSuma(filas: List<FilaResultado>, etiquetaDe: (FilaResultado) -> String): String {
        val partes = mutableListOf<Pair<String, MutableList<Int>>>()
        filas.forEach { f ->
            val etiqueta = etiquetaDe(f)
            val ultima = partes.lastOrNull()
            if (ultima != null && ultima.first == etiqueta) ultima.second.add(f.peso) else partes.add(etiqueta to mutableListOf(f.peso))
        }
        return partes.joinToString(" + ") { (etiqueta, pesos) -> "$etiqueta ${pesos.joinToString(" + ")}" }
    }

    /** etiquetas: codigo de fila -> (etiqueta del aviso, etiqueta de la suma). */
    private fun aviso(
        clasificacion: String,
        porcentaje: Int,
        conCoincidencia: List<FilaResultado>,
        consultadas: Int,
        esPep: Boolean,
        etiquetas: Map<String, Pair<String, String>>
    ): AvisoResultado {
        if (clasificacion == Clasificacion.SIN_COINCIDENCIAS) {
            val titulo = if (consultadas == 1) "Sin coincidencias en la lista consultada"
            else "Sin coincidencias en las $consultadas listas consultadas"
            return AvisoResultado(clasificacion, titulo, emptyList(), null,
                "La consulta queda guardada con fecha y usuario. Puedes continuar al scoring.")
        }
        val fuentes = conCoincidencia.map { etiquetas[it.codigo]?.first ?: if (it.otraLista) FUENTE_OTRAS_NOMBRE else it.nombre }.distinct()
        val suma = "Suma $porcentaje% (${textoSuma(conCoincidencia) { etiquetas[it.codigo]?.second ?: it.nombre }}): " +
            "clasificación ${Clasificacion.corto(clasificacion)}."
        val texto = when {
            clasificacion == Clasificacion.CRITICA ->
                "Escala al oficial de cumplimiento y no inicies ni mantengas la relación hasta descartar que sea un homónimo. " +
                    "Verifica las medidas de congelamiento (art. 12, literales i y j)."
            esPep -> "Si confirmas la coincidencia, el scoring marcará al cliente como PEP y aplicará debida diligencia reforzada."
            else -> "Revisa los registros antes de decidir. Si no es la misma persona, márcala como homónimo."
        }
        val titulo = if (clasificacion == Clasificacion.CRITICA) "Coincidencia crítica" else "Coincidencia detectada"
        return AvisoResultado(clasificacion, titulo, fuentes, suma, texto)
    }
}
