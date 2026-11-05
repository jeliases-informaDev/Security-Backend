package com.security.modules.scoring.domain.metodologia

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale

/** Una opcion elegida en un desplegable: su nombre y su puntaje (null = la metodologia no le da puntaje). */
data class OpcionElegida(val nombre: String, val puntaje: Int?)

/** Pais de los fondos: su grupo (1 a 4, que es su puntaje) y si esta en la lista de alto riesgo del GAFI. */
data class PaisElegido(val nombre: String, val grupo: Int?, val altoRiesgoGafi: Boolean)

/** Lo que dijo la consulta automatica a Listas Negativas. */
data class CondicionCliente(
    val puntaje: Int,
    val etiqueta: String,
    val esPep: Boolean,
    val coincidenciasSanciones: Int
)

/**
 * Datos de una evaluacion. Lo que falta va en null; `bloqueosPrevios` son los que ya detecto quien arma la entrada
 * (cliente sin datos, un id que no existe) y se informan tal cual, sin repetir el campo.
 */
data class EntradaCalculo(
    val ocupacion: OpcionElegida? = null,
    val condicion: CondicionCliente? = null,
    val montoOperacion: BigDecimal? = null,
    /** Promedio mensual del cliente en el Registro de Operaciones (null = no se pudo saber). */
    val promedioMensual: BigDecimal? = null,
    /** Frase sobre de donde salio (o por que no hay) el promedio del Registro de Operaciones. */
    val notaRegistro: String? = null,
    val historial: OpcionElegida? = null,
    val producto: OpcionElegida? = null,
    val canal: OpcionElegida? = null,
    val medioPago: OpcionElegida? = null,
    val departamentoResidencia: OpcionElegida? = null,
    val departamentoOperacion: OpcionElegida? = null,
    val pais: PaisElegido? = null,
    val bloqueosPrevios: List<Bloqueo> = emptyList()
)

data class Bloqueo(val campo: String, val mensaje: String)

data class ReglaActivada(val codigo: String, val titulo: String, val detalle: String)

data class FactorCalculado(val def: FactorDef, val dato: String?, val puntaje: Int?, val aporte: BigDecimal?)

data class GrupoCalculado(val def: GrupoDef, val promedio: BigDecimal?, val factores: List<FactorCalculado>)

data class AvisoRegla(val titulo: String, val detalle: String)

data class ResultadoCalculo(
    val grupos: List<GrupoCalculado>,
    /** Con dos decimales; null si falta algo. */
    val puntaje: BigDecimal?,
    val nivelPorPromedio: Nivel?,
    /** null cuando hay bloqueos (regla 6): sin nivel. */
    val nivel: Nivel?,
    val reglas: List<ReglaActivada>,
    val bloqueos: List<Bloqueo>,
    val formula: String,
    val montoNota: String?,
    val avisoRegla: AvisoRegla?
) {
    val completo: Boolean get() = bloqueos.isEmpty()
    val fijadoPorRegla: Boolean get() = nivel != null && reglas.isNotEmpty()
}

/** Campos de la peticion a los que apunta cada bloqueo (los mismos nombres del contrato con el front). */
object CampoScoring {
    const val CLIENTE = "cliente"
    const val OCUPACION = "ocupacionId"
    const val MONTO = "montoOperacion"
    const val PRODUCTO = "productoId"
    const val CANAL = "canalId"
    const val MEDIO_PAGO = "medioPagoId"
    const val DEPARTAMENTO_RESIDENCIA = "departamentoResidenciaId"
    const val DEPARTAMENTO_OPERACION = "departamentoOperacionId"
    const val PAIS = "paisFondosId"
}

/**
 * El calculo del scoring, sin base ni red: recibe la metodologia y los datos y devuelve factores, promedios,
 * puntaje, nivel, reglas activadas y bloqueos.
 */
object CalculoScoring {

    private const val SIN_PUNTAJE = "no tiene puntaje en la metodología: elige otra opción o consulta al oficial de cumplimiento."

    fun calcular(m: Metodologia, e: EntradaCalculo): ResultadoCalculo {
        val bloqueos = mutableListOf<Bloqueo>()
        bloqueos.addAll(e.bloqueosPrevios)
        fun bloquear(campo: String, mensaje: String) {
            if (bloqueos.none { it.campo == campo }) bloqueos.add(Bloqueo(campo, mensaje))
        }

        // ---- Puntaje y dato de cada factor
        val ocupacion = e.ocupacion
        when {
            ocupacion == null -> bloquear(CampoScoring.OCUPACION, "Falta la ocupación o actividad.")
            ocupacion.puntaje == null -> bloquear(CampoScoring.OCUPACION, "Esta ocupación $SIN_PUNTAJE")
        }

        if (e.condicion == null) bloquear(CampoScoring.CLIENTE, "Falta el cliente: búscalo por su documento o registra sus datos.")

        val monto = e.montoOperacion
        val baseMonto: BigDecimal? = when {
            monto == null -> { bloquear(CampoScoring.MONTO, "Falta el monto de la operación."); null }
            monto.signum() < 0 -> { bloquear(CampoScoring.MONTO, "El monto de la operación no puede ser negativo."); null }
            else -> listOfNotNull(monto, e.promedioMensual).max()
        }
        val puntajeMonto = baseMonto?.let { m.puntajeMonto(it) }

        val producto = e.producto
        when {
            producto == null -> bloquear(CampoScoring.PRODUCTO, "Falta el producto o servicio.")
            producto.puntaje == null -> bloquear(CampoScoring.PRODUCTO, "Este producto o servicio $SIN_PUNTAJE")
        }

        val canal = e.canal
        val medio = e.medioPago
        when {
            canal == null -> bloquear(CampoScoring.CANAL, "Falta el canal.")
            canal.puntaje == null -> bloquear(CampoScoring.CANAL, "Este canal $SIN_PUNTAJE")
        }
        when {
            medio == null -> bloquear(CampoScoring.MEDIO_PAGO, "Falta el medio de pago.")
            medio.puntaje == null -> bloquear(CampoScoring.MEDIO_PAGO, "Este medio de pago $SIN_PUNTAJE")
        }
        val puntajeCanalMedio = mayor(canal?.puntaje, medio?.puntaje)

        val depRes = e.departamentoResidencia
        val depOp = e.departamentoOperacion
        when {
            depRes == null -> bloquear(CampoScoring.DEPARTAMENTO_RESIDENCIA, "Falta el departamento de residencia.")
            depRes.puntaje == null -> bloquear(CampoScoring.DEPARTAMENTO_RESIDENCIA, "Este departamento $SIN_PUNTAJE")
        }
        when {
            depOp == null -> bloquear(CampoScoring.DEPARTAMENTO_OPERACION, "Falta el departamento de la operación.")
            depOp.puntaje == null -> bloquear(CampoScoring.DEPARTAMENTO_OPERACION, "Este departamento $SIN_PUNTAJE")
        }
        val puntajeDepartamento = mayor(depRes?.puntaje, depOp?.puntaje)

        val pais = e.pais
        when {
            pais == null -> bloquear(CampoScoring.PAIS, "Falta el país de origen o destino de los fondos.")
            pais.grupo == null -> bloquear(CampoScoring.PAIS, "Este país no tiene grupo en la metodología: elige otro o consulta al oficial de cumplimiento.")
        }

        val datos: Map<CodigoFactor, Pair<String?, Int?>> = mapOf(
            CodigoFactor.OCUPACION to (ocupacion?.nombre?.let(::capitalizar) to ocupacion?.puntaje),
            CodigoFactor.CONDICION to (e.condicion?.etiqueta to e.condicion?.puntaje),
            CodigoFactor.MONTO to (baseMonto?.let { "S/ ${soles(it)}" } to puntajeMonto),
            CodigoFactor.HISTORIAL to (e.historial?.nombre to e.historial?.puntaje),
            CodigoFactor.PRODUCTO to (producto?.nombre to producto?.puntaje),
            CodigoFactor.CANAL_MEDIO_PAGO to (listOfNotNull(canal?.nombre, medio?.nombre).joinToString(" + ").ifEmpty { null } to puntajeCanalMedio),
            CodigoFactor.DEPARTAMENTO to (datoDepartamento(depRes?.nombre, depOp?.nombre) to puntajeDepartamento),
            CodigoFactor.PAIS_FONDOS to (pais?.nombre to pais?.grupo)
        )

        // ---- Grupos y puntaje
        val grupos = m.grupos.map { g ->
            val factores = g.factores.map { f ->
                val (dato, puntaje) = datos.getValue(f.codigo)
                FactorCalculado(f, dato, puntaje, puntaje?.let { aporte(f.peso, it) })
            }
            val completo = factores.all { it.puntaje != null }
            val promedio = if (completo) {
                BigDecimal(factores.sumOf { it.def.peso * it.puntaje!! })
                    .divide(BigDecimal(g.peso), 2, RoundingMode.HALF_UP)
            } else null
            GrupoCalculado(g, promedio, factores)
        }

        val todos = grupos.flatMap { it.factores }
        // Puntaje = suma de (peso del grupo x promedio del grupo) = suma de (peso x puntaje) / 100, sin redondeos intermedios.
        val puntaje = if (todos.all { it.puntaje != null }) {
            BigDecimal(todos.sumOf { it.def.peso * it.puntaje!! }).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        } else null
        val nivelPorPromedio = puntaje?.let { m.nivelDe(it) }

        // ---- Reglas 1 a 5 (fijan Alto como minimo)
        val reglas = mutableListOf<ReglaActivada>()
        fun activar(codigo: String, detalle: String) = reglas.add(ReglaActivada(codigo, m.tituloRegla(codigo), detalle))

        if (e.condicion?.esPep == true) activar(CodigoRegla.PEP, "Nivel mínimo Alto")
        val sanciones = e.condicion?.coincidenciasSanciones ?: 0
        if (sanciones > 0) {
            activar(CodigoRegla.SANCIONES, if (sanciones == 1) "1 coincidencia: nivel mínimo Alto" else "$sanciones coincidencias: nivel mínimo Alto")
        }
        if (pais?.altoRiesgoGafi == true) activar(CodigoRegla.PAIS_GAFI, "${pais.nombre} está en la lista de alto riesgo del GAFI")
        val enMaximo = todos.filter { it.puntaje == m.puntajeMaximo }
        if (enMaximo.size >= m.minimoFactoresEnMaximo) {
            activar(CodigoRegla.DOS_FACTORES_EN_4, capitalizar(enumerar(enMaximo.map { it.def.corto })))
        }
        val puntajeOcupacion = ocupacion?.puntaje
        if (puntajeOcupacion != null && puntajeOcupacion in m.puntajesOcupacionBaja && monto != null && monto > m.montoAltoOcupacionBaja) {
            activar(
                CodigoRegla.MONTO_ALTO_OCUPACION_BAJA,
                "Más de S/ ${soles(m.montoAltoOcupacionBaja)} en la operación con una ocupación de puntaje $puntajeOcupacion"
            )
        }

        // ---- Nivel (regla 6: con bloqueos no hay nivel)
        val nivel = when {
            bloqueos.isNotEmpty() -> null
            reglas.isNotEmpty() -> Nivel.ALTO
            else -> nivelPorPromedio
        }

        val formula = grupos.joinToString(" + ") { "${it.def.peso}% × ${it.promedio?.toPlainString() ?: "—"}" }

        val aviso = if (nivel != null && reglas.isNotEmpty() && nivelPorPromedio != null && puntaje != null) {
            val primera = reglas.first().titulo.replaceFirstChar { it.lowercase() }
            val cuantas = when (reglas.size) {
                1 -> "una regla fija"
                else -> "${numeroEnLetras(reglas.size)} reglas fijan"
            }
            val detalle = if (nivelPorPromedio == Nivel.ALTO) {
                "El promedio ya da Alto (${puntaje.toPlainString()}). Aunque diera menos, $cuantas Alto como nivel mínimo."
            } else {
                "El promedio da ${nivelPorPromedio.etiqueta} (${puntaje.toPlainString()}), pero $cuantas Alto como nivel mínimo."
            }
            AvisoRegla("Nivel fijado por regla: $primera", detalle)
        } else null

        return ResultadoCalculo(
            grupos = grupos,
            puntaje = puntaje,
            nivelPorPromedio = nivelPorPromedio,
            nivel = nivel,
            reglas = reglas,
            bloqueos = bloqueos,
            formula = formula,
            montoNota = notaMonto(m, monto, e.promedioMensual, puntajeMonto, e.notaRegistro),
            avisoRegla = aviso
        )
    }

    private fun mayor(a: Int?, b: Int?): Int? = if (a == null || b == null) null else maxOf(a, b)

    private fun aporte(peso: Int, puntaje: Int): BigDecimal =
        BigDecimal(peso * puntaje).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

    private fun datoDepartamento(residencia: String?, operacion: String?): String? = when {
        residencia == null && operacion == null -> null
        residencia == null -> "$operacion (operación)"
        operacion == null -> "$residencia (residencia)"
        normalizar(residencia) == normalizar(operacion) -> residencia
        else -> "$residencia (residencia) / $operacion (operación)"
    }

    private fun notaMonto(m: Metodologia, monto: BigDecimal?, promedio: BigDecimal?, puntaje: Int?, notaRegistro: String?): String? {
        if (monto == null || puntaje == null) return notaRegistro
        val rango = textoRango(m, puntaje)
        val cual = when {
            promedio == null -> null
            promedio > monto -> "Se toma el mayor: el promedio mensual del Registro de Operaciones."
            else -> "Se toma el mayor: el monto de la operación."
        }
        return listOfNotNull("Puntaje $puntaje: $rango.", notaRegistro, cual).joinToString(" ")
    }

    /** "hasta S/ 20,000", "más de S/ 20,000 hasta S/ 100,000", "más de S/ 500,000". */
    fun textoRango(m: Metodologia, puntaje: Int): String {
        val i = m.rangosMonto.indexOfFirst { it.puntaje == puntaje }
        if (i < 0) return "puntaje $puntaje"
        val r = m.rangosMonto[i]
        val anterior = m.rangosMonto.getOrNull(i - 1)?.hasta
        return when {
            anterior == null && r.hasta != null -> "hasta S/ ${soles(r.hasta)}"
            r.hasta == null && anterior != null -> "más de S/ ${soles(anterior)}"
            anterior != null && r.hasta != null -> "más de S/ ${soles(anterior)} hasta S/ ${soles(r.hasta)}"
            else -> "cualquier monto"
        }
    }

    /** 5000000 -> "5,000,000"; 1234.5 -> "1,234.50". */
    fun soles(monto: BigDecimal): String {
        val simbolos = DecimalFormatSymbols(Locale.US)
        val formato = if (monto.stripTrailingZeros().scale() <= 0) DecimalFormat("#,##0", simbolos) else DecimalFormat("#,##0.00", simbolos)
        return formato.format(monto)
    }

    fun enumerar(partes: List<String>): String = when (partes.size) {
        0 -> ""
        1 -> partes[0]
        else -> partes.dropLast(1).joinToString(", ") + " y " + partes.last()
    }

    fun capitalizar(texto: String): String {
        val t = texto.trim()
        if (t.isEmpty()) return t
        val esMayusculas = t == t.uppercase() && t.any { it.isLetter() }
        val base = if (esMayusculas) t.lowercase() else t
        return base.replaceFirstChar { it.uppercase() }
    }

    private fun numeroEnLetras(n: Int): String = when (n) {
        2 -> "dos"; 3 -> "tres"; 4 -> "cuatro"; 5 -> "cinco"; else -> n.toString()
    }

    /** Clave de comparacion de nombres: sin tildes, en mayusculas y sin espacios. */
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.ROOT)
            .replace(Regex("\\s+"), "")
}
