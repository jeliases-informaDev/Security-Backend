package com.security.modules.scoring.domain.metodologia

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Definicion de una metodologia de scoring: grupos, factores, pesos, bandas, rangos de monto y reglas.
 * Es el UNICO lugar del codigo donde viven pesos y bandas. Las tablas de puntaje de cada opcion
 * (ocupaciones, departamentos, productos, canales, medios de pago y paises) no estan aqui: son datos
 * de la base, cargados desde los .csv de src/main/resources/scoring (ver docs/SCORING.md).
 */
enum class Nivel(val etiqueta: String) {
    BAJO("Bajo"),
    MEDIO("Medio"),
    ALTO("Alto")
}

/** Los ocho factores. El codigo es el que viaja al front. */
enum class CodigoFactor {
    OCUPACION, CONDICION, MONTO, HISTORIAL, PRODUCTO, CANAL_MEDIO_PAGO, DEPARTAMENTO, PAIS_FONDOS
}

/** Codigos de las reglas que fijan el nivel Alto (la regla 6, bloqueo, no tiene codigo: deja el resultado sin nivel). */
object CodigoRegla {
    const val PEP = "PEP"
    const val SANCIONES = "SANCIONES"
    const val PAIS_GAFI = "PAIS_GAFI"
    const val DOS_FACTORES_EN_4 = "DOS_FACTORES_EN_4"
    const val MONTO_ALTO_OCUPACION_BAJA = "MONTO_ALTO_OCUPACION_BAJA"
}

/** `corto` se usa al nombrar el factor dentro de una frase ("ocupación, condición y monto"). */
data class FactorDef(
    val codigo: CodigoFactor,
    val nombre: String,
    val corto: String,
    val peso: Int,
    val automatico: Boolean
)

data class GrupoDef(
    val codigo: String,
    val nombre: String,
    val peso: Int,
    val factores: List<FactorDef>
)

/** Incluye `desde` y excluye `hasta`, salvo la ultima banda, que incluye su `hasta`. */
data class Banda(val nivel: Nivel, val desde: BigDecimal, val hasta: BigDecimal)

/** Montos hasta `hasta` (incluido) valen `puntaje`; `hasta = null` es el ultimo tramo, sin tope. */
data class RangoMonto(val hasta: BigDecimal?, val puntaje: Int)

data class AccionNivel(
    val debidaDiligencia: String,
    val decide: String,
    val revisionMeses: Int,
    val reunir: List<String>
)

data class Metodologia(
    val version: Int,
    val fecha: LocalDate,
    val grupos: List<GrupoDef>,
    val bandas: List<Banda>,
    val rangosMonto: List<RangoMonto>,
    /** Puntaje mas alto de la escala (los factores "en 4" de la regla 4). */
    val puntajeMaximo: Int,
    /** Regla 4: cuantos factores con el puntaje maximo fijan Alto. */
    val minimoFactoresEnMaximo: Int,
    /** Regla 5: mas de este monto en la operacion con una ocupacion de estos puntajes fija Alto. */
    val montoAltoOcupacionBaja: BigDecimal,
    val puntajesOcupacionBaja: Set<Int>,
    /** Condicion del cliente: PEP (o vinculado) y sin coincidencias. */
    val puntajeCondicionPep: Int,
    val puntajeCondicionSinCoincidencias: Int,
    /** Historial en la relacion mientras no exista el registro de alertas. */
    val puntajeHistorialSinAlertas: Int,
    /** Grupos de Listas Negativas (por su `orden`) que cuentan como "sanciones o LA/FT" (regla 2). */
    val gruposListaSanciones: Set<Int>,
    /** Las listas cuyo nombre contiene esta palabra (sin tildes ni mayusculas) son "noticias y medios". */
    val palabraListaNoticias: String,
    /** Meses que se promedian del Registro de Operaciones del cliente. */
    val mesesPromedioOperaciones: Int,
    val titulosRegla: Map<String, String>,
    val acciones: Map<Nivel, AccionNivel>
) {
    init {
        require(grupos.sumOf { it.peso } == 100) { "Los pesos de los grupos deben sumar 100" }
        grupos.forEach { g ->
            require(g.factores.sumOf { it.peso } == g.peso) { "Los factores del grupo ${g.codigo} deben sumar ${g.peso}" }
        }
        require(bandas.isNotEmpty() && bandas.zipWithNext().all { (a, b) -> a.hasta.compareTo(b.desde) == 0 }) {
            "Las bandas deben ser contiguas"
        }
        require(rangosMonto.isNotEmpty() && rangosMonto.last().hasta == null) { "El ultimo rango de monto no tiene tope" }
        require(Nivel.entries.all { it in acciones }) { "Falta que hacer para algun nivel" }
    }

    val factores: List<FactorDef> get() = grupos.flatMap { it.factores }

    fun factor(codigo: CodigoFactor): FactorDef = factores.first { it.codigo == codigo }

    fun tituloRegla(codigo: String): String = titulosRegla[codigo] ?: codigo

    /** Nivel de un puntaje ya redondeado a dos decimales. */
    fun nivelDe(puntaje: BigDecimal): Nivel {
        bandas.forEachIndexed { i, b ->
            if (puntaje < b.hasta || i == bandas.lastIndex) return b.nivel
        }
        return bandas.last().nivel
    }

    /** Puntaje de un monto (en soles) segun los rangos. */
    fun puntajeMonto(monto: BigDecimal): Int =
        rangosMonto.first { it.hasta == null || monto <= it.hasta }.puntaje
}

/**
 * Metodologia del 2026-10-29 (version 2), aprobada por el dueno el 2026-11-05.
 * Todos los valores son propuesta a validar con el oficial de cumplimiento.
 */
object MetodologiaV2 {

    val ACTUAL = Metodologia(
        version = 2,
        fecha = LocalDate.of(2026, 10, 29),
        grupos = listOf(
            GrupoDef(
                "CLIENTES", "Clientes", 60, listOf(
                    FactorDef(CodigoFactor.OCUPACION, "Ocupación o actividad", "ocupación", 25, automatico = false),
                    FactorDef(CodigoFactor.CONDICION, "Condición del cliente", "condición", 10, automatico = true),
                    FactorDef(CodigoFactor.MONTO, "Monto y frecuencia", "monto", 20, automatico = false),
                    FactorDef(CodigoFactor.HISTORIAL, "Historial en la relación", "historial", 5, automatico = true)
                )
            ),
            GrupoDef(
                "PRODUCTOS", "Productos y servicios", 20, listOf(
                    FactorDef(CodigoFactor.PRODUCTO, "Producto o servicio", "producto", 10, automatico = false),
                    FactorDef(CodigoFactor.CANAL_MEDIO_PAGO, "Canal y medio de pago", "canal y medio de pago", 10, automatico = false)
                )
            ),
            GrupoDef(
                "ZONA", "Zona geográfica", 20, listOf(
                    FactorDef(CodigoFactor.DEPARTAMENTO, "Departamento", "departamento", 10, automatico = false),
                    FactorDef(CodigoFactor.PAIS_FONDOS, "País de los fondos", "país", 10, automatico = false)
                )
            )
        ),
        bandas = listOf(
            Banda(Nivel.BAJO, BigDecimal("1.00"), BigDecimal("1.75")),
            Banda(Nivel.MEDIO, BigDecimal("1.75"), BigDecimal("2.50")),
            Banda(Nivel.ALTO, BigDecimal("2.50"), BigDecimal("4.00"))
        ),
        rangosMonto = listOf(
            RangoMonto(BigDecimal("20000"), 1),
            RangoMonto(BigDecimal("100000"), 2),
            RangoMonto(BigDecimal("500000"), 3),
            RangoMonto(null, 4)
        ),
        puntajeMaximo = 4,
        minimoFactoresEnMaximo = 2,
        montoAltoOcupacionBaja = BigDecimal("100000"),
        puntajesOcupacionBaja = setOf(1, 2),
        puntajeCondicionPep = 4,
        puntajeCondicionSinCoincidencias = 1,
        puntajeHistorialSinAlertas = 1,
        gruposListaSanciones = setOf(1, 2, 3, 4),
        palabraListaNoticias = "NOTICIA",
        mesesPromedioOperaciones = 12,
        titulosRegla = mapOf(
            CodigoRegla.PEP to "Cliente PEP",
            CodigoRegla.SANCIONES to "Coincidencia en listas de sanciones o LA/FT",
            CodigoRegla.PAIS_GAFI to "País de alto riesgo del GAFI",
            CodigoRegla.DOS_FACTORES_EN_4 to "Dos o más factores en 4",
            CodigoRegla.MONTO_ALTO_OCUPACION_BAJA to "Monto alto para la ocupación"
        ),
        acciones = mapOf(
            Nivel.BAJO to AccionNivel("Simplificada o regular", "Analista", 24, emptyList()),
            Nivel.MEDIO to AccionNivel("Regular", "Analista", 12, emptyList()),
            Nivel.ALTO to AccionNivel(
                "Reforzada", "Gerencia más alta o comité", 6,
                listOf(
                    "Parientes hasta el 2.º grado y cónyuge",
                    "Empresas donde tiene 25% o más",
                    "Información adicional: proveedores y clientes principales"
                )
            )
        )
    )
}

/**
 * Metodologia version 1 (libro de 2020): ya no calcula nada; solo se conservan los pesos con los que el historial
 * muestra las evaluaciones antiguas. Ocupacion 20 %, cliente sensible 40 %, departamento 12 % y volumen 28 %.
 */
object MetodologiaV1 {
    const val PESO_OCUPACION = 20
    const val PESO_CLIENTE_SENSIBLE = 40
    const val PESO_DEPARTAMENTO = 12
    const val PESO_VOLUMEN = 28
}
