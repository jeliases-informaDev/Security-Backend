package com.security.modules.inicio

import com.security.modules.denuncias.application.service.PlazoDenunciasService
import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.dominio.ESTADO_SIN_LICENCIA
import com.security.modules.oficial.aplicacion.AccesoOficialService
import com.security.modules.oficial.aplicacion.FichaOficialService
import com.security.modules.oficial.aplicacion.OficialException
import com.security.modules.oficial.aplicacion.ReglasOficialService
import com.security.modules.oficial.aplicacion.ReglasPorDefecto
import com.security.modules.oficial.dominio.Plazo
import com.security.modules.scoring.domain.metodologia.CalculoScoring
import com.security.modules.scoring.infrastructure.CatalogoPaisesIso
import com.security.modules.scoring.domain.metodologia.MetodologiaV2
import com.security.modules.scoring.domain.metodologia.Nivel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------- respuesta (CONTRATO-CABINA.md, PARTE B)

data class CabinaInicio(
    val scoring: ScoringCabina?,
    val tendencia: TendenciaCabina?,
    val mapa: MapaCabina?,
    val plazos: List<PlazoCabina>?,
    val licencia: LicenciaCabina?,
    // CONTRATO-MUNDO.md: mapa mundial de calor (peso del riesgo por pais)
    val mundo: MundoCabina? = null
)

data class MundoCabina(val periodoEtiqueta: String, val scoring: MundoScoring?, val listas: MundoListas?)

data class MundoScoring(val paises: List<PaisScoring>, val sinPais: Long)

data class PaisScoring(val nombre: String, val codigo: String?, val evaluaciones: Long, val altoRiesgo: Long, val puntajePromedio: Double?, val pesoPct: Double)

data class MundoListas(val paises: List<PaisListas>, val sinPais: Long)

data class PaisListas(val nombre: String, val codigo: String?, val consultas: Long, val conCoincidencia: Long, val pep: Long, val pesoPct: Double)

data class ScoringCabina(
    val evaluaciones: Long,
    val porNivel: PorNivelScoring,
    val puntajePromedio: Double?,
    val puntajePromedioAnterior: Double?,
    val escala: EscalaScoring,
    val radar: List<FactorRadar>
)

data class PorNivelScoring(val bajo: Long, val medio: Long, val alto: Long)

data class EscalaScoring(val min: Double, val max: Double, val corteMedio: Double, val corteAlto: Double)

data class FactorRadar(val codigo: String, val factor: String, val valor: Double?, val anterior: Double?)

data class TendenciaCabina(
    val dias: Int,
    val desde: LocalDate,
    val consultas: List<Long>?,
    val coincidencias: List<Long>?,
    val registros: List<Long>?,
    val evaluaciones: List<Long>?,
    val denuncias: List<Long>?
)

data class MapaCabina(val departamentos: List<DepartamentoMapa>)

data class DepartamentoMapa(
    val codigo: String?,
    val nombre: String,
    val registros: Long,
    val montoUsd: Double,
    val evaluaciones: Long,
    val altos: Long
)

data class PlazoCabina(val tipo: String, val titulo: String, val vence: LocalDate, val dias: Long, val enlace: String)

data class LicenciaCabina(
    val plan: String?,
    val tokens: UsoLicencia?,
    val usuarios: CupoLicencia?,
    val vence: String?,
    val diasRestantes: Long?
)

data class UsoLicencia(val usados: Long?, val total: Long?)

data class CupoLicencia(val usados: Long?, val cupo: Long?)

/** Que puede ver la cuenta en la cabina (sale de InicioService, con las mismas reglas de cada bloque). */
data class AlcancesCabina(
    val listas: AlcanceInicio?,
    val scoring: AlcanceInicio?,
    val empresaOperaciones: String?,
    val denuncias: AlcanceInicio?,
    val empresaLicencia: String?,
    val veLicencia: Boolean
)

/**
 * Bloque `cabina` del tablero de Inicio (instrumentos). Todo se cuenta y agrupa en la base: una consulta por serie o
 * por instrumento, nada por fila. El radar lee el JSON del resultado de las evaluaciones del mes (una sola consulta).
 */
@Service
class CabinaService(
    private val consultas: InicioConsultas,
    private val reloj: RelojInicio,
    private val plazoDenuncias: PlazoDenunciasService,
    private val accesoOficial: AccesoOficialService,
    private val fichasOficial: FichaOficialService,
    private val reglasOficial: ReglasOficialService,
    private val licencias: LicenciaService,
    private val licenciaContexto: LicenciaContexto
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val json = jacksonObjectMapper()
    private val metodologia = MetodologiaV2.ACTUAL

    companion object {
        const val DIAS_TENDENCIA = 30
        const val MAX_PLAZOS = 6

        /** Codigo INEI de 2 digitos por nombre normalizado (sin tildes, mayusculas) y el nombre para mostrar. */
        val INEI: Map<String, Pair<String, String>> = listOf(
            "01" to "Amazonas", "02" to "Áncash", "03" to "Apurímac", "04" to "Arequipa", "05" to "Ayacucho",
            "06" to "Cajamarca", "07" to "Callao", "08" to "Cusco", "09" to "Huancavelica", "10" to "Huánuco",
            "11" to "Ica", "12" to "Junín", "13" to "La Libertad", "14" to "Lambayeque", "15" to "Lima",
            "16" to "Loreto", "17" to "Madre de Dios", "18" to "Moquegua", "19" to "Pasco", "20" to "Piura",
            "21" to "Puno", "22" to "San Martín", "23" to "Tacna", "24" to "Tumbes", "25" to "Ucayali"
        ).associate { (codigo, nombre) -> CalculoScoring.normalizar(nombre) to (codigo to nombre) } +
            mapOf("CUZCO" to ("08" to "Cusco"))

        private fun redondear(v: Double?): Double? = v?.let { BigDecimal(it).setScale(2, RoundingMode.HALF_UP).toDouble() }
    }

    fun cabina(a: AlcancesCabina, periodo: YearMonth, finSerie: LocalDate): CabinaInicio =
        CabinaInicio(
            scoring = parte("scoring") { a.scoring?.let { scoring(it, periodo) } },
            tendencia = parte("tendencia") { tendencia(a, finSerie) },
            mapa = parte("mapa") { mapa(a, periodo) },
            plazos = parte("plazos") { plazos(a) },
            licencia = if (a.veLicencia) parte("licencia") { licencia(a.empresaLicencia) } else null,
            mundo = parte("mundo") { mundo(a, periodo) }
        )

    private fun <T> parte(nombre: String, calculo: () -> T?): T? = try {
        calculo()
    } catch (e: Exception) {
        log.warn("Inicio (cabina): no se pudo calcular {}: {}", nombre, e.message)
        null
    }

    // ================================================================== scoring

    private fun scoring(a: AlcanceInicio, periodo: YearMonth): ScoringCabina {
        val desde = periodo.atDay(1).atStartOfDay()
        val hasta = periodo.plusMonths(1).atDay(1).atStartOfDay()
        val anteriorDesde = periodo.minusMonths(1).atDay(1).atStartOfDay()
        val niveles = consultas.scoringPorNivel(a, desde, hasta)
        val anteriores = consultas.scoringPorNivel(a, anteriorDesde, desde)
        fun promedio(filas: List<Triple<String?, Long, BigDecimal>>): Double? {
            val n = filas.sumOf { it.second }
            if (n == 0L) return null
            return redondear(filas.fold(BigDecimal.ZERO) { s, f -> s.add(f.third) }.toDouble() / n)
        }
        fun cuantas(nivel: Nivel) = niveles.filter { it.first == nivel.name }.sumOf { it.second }

        val actual = promediosFactores(consultas.scoringResultados(a, desde, hasta))
        val previo = promediosFactores(consultas.scoringResultados(a, anteriorDesde, desde))
        val bandas = metodologia.bandas.associateBy { it.nivel }
        return ScoringCabina(
            evaluaciones = niveles.sumOf { it.second },
            porNivel = PorNivelScoring(cuantas(Nivel.BAJO), cuantas(Nivel.MEDIO), cuantas(Nivel.ALTO)),
            puntajePromedio = promedio(niveles),
            puntajePromedioAnterior = promedio(anteriores),
            escala = EscalaScoring(
                min = metodologia.bandas.first().desde.toDouble(),
                max = metodologia.puntajeMaximo.toDouble(),
                corteMedio = bandas.getValue(Nivel.MEDIO).desde.toDouble(),
                corteAlto = bandas.getValue(Nivel.ALTO).desde.toDouble()
            ),
            radar = if (actual.isEmpty()) emptyList() else metodologia.factores.map { f ->
                FactorRadar(f.codigo.name, f.nombre, actual[f.codigo.name], previo[f.codigo.name])
            }
        )
    }

    /** Promedio del puntaje de cada factor (codigo -> promedio) en los resultados guardados. */
    private fun promediosFactores(resultados: List<String>): Map<String, Double?> {
        val sumas = mutableMapOf<String, Pair<Double, Int>>()
        resultados.forEach { texto ->
            val raiz: JsonNode = try {
                json.readTree(texto)
            } catch (_: Exception) {
                return@forEach
            }
            raiz.path("grupos").forEach { g ->
                g.path("factores").forEach { f ->
                    val codigo = f.get("codigo")?.takeIf { !it.isNull }?.toString()?.trim('"')
                    val puntaje = f.path("puntaje")
                    if (codigo != null && puntaje.isNumber) {
                        val (s, n) = sumas[codigo] ?: (0.0 to 0)
                        sumas[codigo] = (s + puntaje.asDouble()) to (n + 1)
                    }
                }
            }
        }
        return sumas.mapValues { (_, v) -> redondear(v.first / v.second) }
    }

    // ================================================================== tendencia

    private fun tendencia(a: AlcancesCabina, fin: LocalDate): TendenciaCabina {
        val inicio = fin.minusDays((DIAS_TENDENCIA - 1).toLong())
        val desde = inicio.atStartOfDay()
        val hasta = fin.plusDays(1).atStartOfDay()
        fun serie(datos: Map<LocalDate, Long>): List<Long> = (0 until DIAS_TENDENCIA).map { datos[inicio.plusDays(it.toLong())] ?: 0L }
        val listas = a.listas?.let { al -> consultas.consultasPorDia(al, desde, hasta).associateBy { it.dia } }
        return TendenciaCabina(
            dias = DIAS_TENDENCIA,
            desde = inicio,
            consultas = listas?.let { m -> serie(m.mapValues { it.value.total }) },
            coincidencias = listas?.let { m -> serie(m.mapValues { it.value.conCoincidencia }) },
            registros = a.empresaOperaciones?.let { serie(consultas.registrosPorDia(it, inicio, fin)) },
            evaluaciones = a.scoring?.let { serie(consultas.scoringPorDia(it, desde, hasta)) },
            denuncias = a.denuncias?.let { serie(consultas.denunciasPorDia(it, desde, hasta)) }
        )
    }

    // ================================================================== mapa

    private class Acumulado(var registros: Long = 0, var monto: BigDecimal = BigDecimal.ZERO, var evaluaciones: Long = 0, var altos: Long = 0)

    private fun mapa(a: AlcancesCabina, periodo: YearMonth): MapaCabina? {
        if (a.empresaOperaciones == null && a.scoring == null) return null
        val porNombre = linkedMapOf<String, Acumulado>()
        a.empresaOperaciones?.let { empresa ->
            val ubicaciones = consultas.registrosPorUbicacion(empresa, periodo.atDay(1), periodo.atEndOfMonth())
            if (ubicaciones.isNotEmpty()) {
                // La persona guarda el departamento y la provincia como texto (nombre) o, en registros viejos, como id.
                val deptos = consultas.departamentos()
                val provincias = if (ubicaciones.any { it.departamento.isNullOrBlank() && !it.provincia.isNullOrBlank() }) consultas.provincias() else emptyList()
                ubicaciones.forEach { u ->
                    val nombre = departamentoDe(u, deptos, provincias) ?: "Sin departamento"
                    val acc = porNombre.getOrPut(nombre) { Acumulado() }
                    acc.registros += u.registros
                    acc.monto = acc.monto.add(u.montoUsd)
                }
            }
        }
        a.scoring?.let { al ->
            consultas.scoringPorDepartamento(al, periodo.atDay(1).atStartOfDay(), periodo.plusMonths(1).atDay(1).atStartOfDay()).forEach { (nombre, n, altos) ->
                val acc = porNombre.getOrPut(nombre) { Acumulado() }
                acc.evaluaciones += n
                acc.altos += altos
            }
        }
        // Se juntan los que caen en el mismo codigo INEI (p. ej. «Cusco» y «Cuzco»).
        val juntos = linkedMapOf<String, Pair<String?, Acumulado>>()
        porNombre.forEach { (nombre, acc) ->
            val codigo = INEI[CalculoScoring.normalizar(nombre)]?.first
            val clave = codigo ?: "?$nombre"
            val previo = juntos[clave]?.second
            if (previo == null) {
                juntos[clave] = codigo to acc
            } else {
                previo.registros += acc.registros
                previo.monto = previo.monto.add(acc.monto)
                previo.evaluaciones += acc.evaluaciones
                previo.altos += acc.altos
            }
        }
        return MapaCabina(
            juntos.map { (clave, par) ->
                val (codigo, acc) = par
                DepartamentoMapa(
                    codigo = codigo,
                    nombre = codigo?.let { c -> INEI.values.first { it.first == c }.second } ?: clave.removePrefix("?"),
                    registros = acc.registros,
                    montoUsd = acc.monto.setScale(2, RoundingMode.HALF_UP).toDouble(),
                    evaluaciones = acc.evaluaciones,
                    altos = acc.altos
                )
            }.sortedWith(compareByDescending<DepartamentoMapa> { it.registros + it.evaluaciones }.thenBy { it.nombre })
        )
    }

    private fun departamentoDe(u: UbicacionRegistros, deptos: Map<Int, String>, provincias: List<Triple<Int, String, String>>): String? {
        val d = u.departamento?.trim()?.takeIf { it.isNotEmpty() }
        if (d != null) return d.toIntOrNull()?.let { deptos[it] } ?: d
        val p = u.provincia?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        p.toIntOrNull()?.let { id -> return provincias.firstOrNull { it.first == id }?.third }
        // Por nombre solo si la provincia no se repite entre departamentos.
        return provincias.filter { CalculoScoring.normalizar(it.second) == CalculoScoring.normalizar(p) }.map { it.third }.distinct().singleOrNull()
    }

    // ================================================================== plazos

    private fun plazos(a: AlcancesCabina): List<PlazoCabina> {
        val hoy = reloj.hoy()
        val lista = mutableListOf<PlazoCabina>()
        fun fila(tipo: String, titulo: String, vence: LocalDate, enlace: String) {
            lista += PlazoCabina(tipo, titulo, vence, ChronoUnit.DAYS.between(hoy, vence), enlace)
        }
        fun deOficial(tipo: String, titulo: String, p: Plazo?, enlace: String) { if (p != null) fila(tipo, titulo, p.vence, enlace) }

        // Oficial de cumplimiento: comunicaciones pendientes con la UIF y revision de la declaracion jurada.
        try {
            val ctx = accesoOficial.contexto(null)
            val estado = fichasOficial.estado(ctx)
            deOficial("COMUNICACION_UIF", "Comunicar la designación a la UIF", estado.plazoComunicacion, "/oficial-cumplimiento/comunicacion")
            estado.pendientes.forEach { deOficial("COMUNICACION_UIF", it.titulo, it.plazo, "/oficial-cumplimiento") }
            estado.vacancia?.let { v ->
                deOficial("COMUNICACION_UIF", "Comunicar la remoción a la UIF", v.plazoComunicarRemocion, "/oficial-cumplimiento")
                deOficial("VACANCIA_OFICIAL", "Designar un nuevo oficial de cumplimiento", v.plazoDesignar, "/oficial-cumplimiento/designar")
            }
            fichasOficial.titularActivo(ctx.empresaId)?.let { d ->
                val desde = d.djReqFechaFirma ?: d.fechaDesignacion
                fila("DECLARACION", "Revisar la declaración jurada de requisitos del oficial",
                    reglasOficial.motor().vencimiento(ReglasPorDefecto.REVISION_SUGERIDA, desde), "/oficial-cumplimiento/ficha")
            }
        } catch (_: OficialException) {
            // Sin empresa: no hay oficial que mirar.
        }

        // Denuncias abiertas: las mas antiguas son las primeras en vencer. Solo el codigo de seguimiento.
        a.denuncias?.let { al ->
            consultas.denunciasAbiertasMasAntiguas(al, MAX_PLAZOS).forEach { (id, codigo, creada) ->
                fila("DENUNCIA", "Denuncia ${codigo ?: "N.º $id"}", creada.toLocalDate().plusDays(plazoDenuncias.dias), "/denuncias")
            }
        }

        // Registros del mes anterior sin constancia de envio (vence: fin del mes en curso, SUPUESTO; docs/INICIO.md).
        a.empresaOperaciones?.let { empresa ->
            val anterior = YearMonth.from(hoy).minusMonths(1)
            val pendientes = consultas.pendientesDeEnvio(empresa, anterior.atDay(1), anterior.atEndOfMonth())
            if (pendientes > 0) {
                fila("REGISTRO_MENSUAL", "Enviar a la UIF $pendientes ${if (pendientes == 1L) "registro" else "registros"} de ${InicioService.etiqueta(anterior)}",
                    YearMonth.from(hoy).atEndOfMonth(), "/operaciones/exportar")
            }
        }
        return lista.sortedWith(compareBy<PlazoCabina> { it.vence }.thenBy { it.tipo }).take(MAX_PLAZOS)
    }

    // ================================================================== mapa mundial

    /** Peso de un pais = su parte (en %) de la suma de puntajes (scoring) o de porcentajes (listas) de todos los paises. */
    private fun mundo(a: AlcancesCabina, periodo: YearMonth): MundoCabina {
        val desde = periodo.atDay(1).atStartOfDay()
        val hasta = periodo.plusMonths(1).atDay(1).atStartOfDay()
        fun peso(parte: Double, total: Double): Double =
            if (total <= 0.0) 0.0 else BigDecimal(parte * 100.0 / total).setScale(1, RoundingMode.HALF_UP).toDouble()
        // Se agrupa por codigo ISO cuando el nombre se reconoce (suma los estilos distintos del mismo pais; nombre = el de la
        // fila con mas datos) y por nombre cuando no.
        fun <F> agrupar(filas: List<F>, nombre: (F) -> String, cantidad: (F) -> Long): List<Triple<String, String?, List<F>>> =
            filas.groupBy { f -> CatalogoPaisesIso.codigo(nombre(f))?.let { "ISO:$it" } ?: "NOMBRE:${nombre(f)}" }
                .map { (clave, grupo) ->
                    Triple(nombre(grupo.maxBy { cantidad(it) }), clave.removePrefix("ISO:").takeIf { clave.startsWith("ISO:") }, grupo)
                }
        val scoring = a.scoring?.let { al ->
            val filas = consultas.scoringPorPais(al, desde, hasta).filter { !it.pais.isNullOrBlank() }
            val total = filas.sumOf { it.sumaPuntaje.toDouble() }
            MundoScoring(
                paises = agrupar(filas, { it.pais!!.trim() }, { it.evaluaciones }).map { (nombre, codigo, g) ->
                    val evaluaciones = g.sumOf { it.evaluaciones }
                    val suma = g.sumOf { it.sumaPuntaje.toDouble() }
                    PaisScoring(
                        nombre = nombre, codigo = codigo, evaluaciones = evaluaciones, altoRiesgo = g.sumOf { it.altas },
                        puntajePromedio = if (evaluaciones == 0L) null else redondear(suma / evaluaciones),
                        pesoPct = peso(suma, total)
                    )
                }.sortedWith(compareByDescending<PaisScoring> { it.pesoPct }.thenBy { it.nombre }),
                sinPais = consultas.scoringSinPais(al, desde, hasta)
            )
        }
        val listas = a.listas?.let { al ->
            val (con, sin) = consultas.consultasPorPais(al, desde, hasta).partition { !it.pais.isNullOrBlank() }
            val total = con.sumOf { it.sumaPorcentaje }.toDouble()
            MundoListas(
                paises = agrupar(con, { it.pais!!.trim() }, { it.consultas }).map { (nombre, codigo, g) ->
                    PaisListas(
                        nombre, codigo, g.sumOf { it.consultas }, g.sumOf { it.conCoincidencia }, g.sumOf { it.pep },
                        peso(g.sumOf { it.sumaPorcentaje }.toDouble(), total)
                    )
                }.sortedWith(compareByDescending<PaisListas> { it.pesoPct }.thenBy { it.nombre }),
                sinPais = sin.sumOf { it.consultas }
            )
        }
        return MundoCabina(InicioService.etiqueta(periodo), scoring, listas)
    }

    // ================================================================== licencia

    private fun licencia(empresa: String?): LicenciaCabina? {
        val uuid = empresa?.let { licenciaContexto.uuidDeEmpresa(it) } ?: return null
        // Usa la cache del modulo de licencias y su timeout (3 s); si falla, licencia null y el resto sale igual.
        val l = licencias.licenciaPublica(uuid) ?: return null
        if (l.estado == ESTADO_SIN_LICENCIA) return null
        val t = l.tokens
        return LicenciaCabina(
            plan = l.plan,
            tokens = t?.let { UsoLicencia(it.usados, it.capacidad ?: listOfNotNull(it.bolsa, it.extra).takeIf { x -> x.isNotEmpty() }?.sum()) },
            usuarios = CupoLicencia(l.usuariosActivos, l.usuariosMax),
            vence = l.vencimiento,
            diasRestantes = l.diasParaVencer
        )
    }
}
