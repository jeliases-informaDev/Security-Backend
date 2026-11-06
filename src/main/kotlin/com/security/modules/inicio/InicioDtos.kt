package com.security.modules.inicio

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * Tablero de Inicio de PLAFT (pagina principal). Contrato: CONTRATO-INICIO.md (lo tiene quien hace las pantallas);
 * que mide cada cifra y de donde sale: docs/INICIO.md. Los nombres y la forma de los campos NO se cambian: lo que no se
 * puede calcular va null (o 0 / lista vacia) y se anota en docs/INICIO.md.
 */

class InicioException(val status: HttpStatus, val code: String, message: String) : RuntimeException(message)

/** Hora de Lima. Se puede fijar en las pruebas. */
@Component
class RelojInicio {
    var clock: Clock = Clock.system(ZONA)

    fun hoy(): LocalDate = LocalDate.now(clock)
    fun ahora(): LocalDateTime = LocalDateTime.now(clock).withNano(0)

    companion object {
        val ZONA: ZoneId = ZoneId.of("America/Lima")
    }
}

data class ResumenInicioResponse(
    val periodo: String,
    val periodoEtiqueta: String,
    val demo: DemoEstado,
    val listas: ListasInicio?,
    val operaciones: OperacionesInicio?,
    val oficial: OficialInicio?,
    val denuncias: DenunciasInicio?,
    // CONTRATO-CABINA.md, PARTE B: null si la cuenta no mira toda la empresa
    val cabina: CabinaInicio? = null
)

data class DemoEstado(val cargado: Boolean, val puedeGestionar: Boolean)

data class ListasInicio(
    val consultas: Long,
    val consultasVariacionPct: Double?,
    val consultasSerie: List<Long>,
    val conCoincidenciaPct: Double?,
    val conCoincidenciaSerie: List<Double>,
    val pepDetectados: Long,
    val pepSerie: List<Long>,
    val homonimosDescartados: Long,
    val homonimosSerie: List<Long>,
    val resultado: ResultadoConsultas,
    val porFuente: List<FuenteCoincidencias>,
    val criticasSinDecidir: CriticasSinDecidir
)

data class ResultadoConsultas(val criticaPct: Double, val altaPct: Double, val mediaPct: Double, val sinCoincidenciaPct: Double)

data class FuenteCoincidencias(val fuente: String, val coincidencias: Long)

data class CriticasSinDecidir(val cantidad: Long, val masAntiguaDias: Int?)

data class OperacionesInicio(
    val registros: Long,
    val montoTotalUsd: Double,
    val inusuales: Long,
    val sobreUmbral: Long,
    val mismoDiaPct: Double?,
    val metaMismoDiaPct: Double,
    val ultimos: List<UltimoRegistro>
)

data class UltimoRegistro(
    val id: Long,
    val codigo: String?,
    val persona: String?,
    val tipo: String?,
    val monto: BigDecimal?,
    val moneda: String?,
    val estado: String
)

data class OficialInicio(
    val estado: String,
    val nombre: String?,
    val cargo: String?,
    val alDia: Boolean?,
    val comunicadoUif: ComunicadoUif?,
    val declaraciones: DeclaracionesOficial?,
    val alterno: Any?
)

data class ComunicadoUif(val fecha: LocalDate, val enPlazo: Boolean?)

data class DeclaracionesOficial(val vigentes: Int, val total: Int)

data class DenunciasInicio(
    val periodoEtiqueta: String,
    val vencidas: Long?,
    val recibidas: Long,
    val porAtender: Long,
    val sinAbrir: Long,
    val plazosATiempoPct: Double?,
    val porSituacion: List<SituacionDenuncias>,
    val cierres: CierresDenuncias?,
    val tiempoPromedioCierreDias: Int?,
    val derivadasAlOficial: Long?
)

data class SituacionDenuncias(val situacion: String, val cantidad: Long)

data class CierresDenuncias(val fundadaPct: Double, val infundadaPct: Double, val archivadaPct: Double)

data class DemoCargaResponse(val cargado: Boolean, val yaEstaba: Boolean, val creados: Map<String, Int>)

data class DemoBorradoResponse(val cargado: Boolean, val eliminados: Map<String, Int>)

/** Porcentaje entero (como Double, igual que el contrato); null si no hay base. */
internal fun porcentaje(parte: Long, total: Long): Double? =
    if (total <= 0L) null else Math.round(parte * 100.0 / total).toDouble()
