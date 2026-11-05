package com.security.modules.scoring.domain.metodologia

/** Una aparicion del cliente en una lista de Listas Negativas, con lo justo para clasificarla. */
data class AparicionLista(val esPep: Boolean, val nombreLista: String?, val ordenGrupo: Int?)

/** Cuantas apariciones hay de cada tipo. Parientes y vinculados de un PEP no se pueden comprobar hoy (no hay ese dato). */
data class ConsultaListasResultado(
    val pep: Int,
    val sanciones: Int,
    val noticias: Int
)

object ClasificadorListas {

    /**
     * PEP: listas marcadas `esPep`. Noticias y medios: listas cuyo nombre contiene la palabra de la metodologia.
     * Sanciones o LA/FT: el resto de las listas de los grupos que la metodologia cuenta como tales (por su orden).
     * Las demas (afectacion financiera, informativas) no cuentan para ninguna regla.
     */
    fun clasificar(m: Metodologia, apariciones: List<AparicionLista>): ConsultaListasResultado {
        val palabra = CalculoScoring.normalizar(m.palabraListaNoticias)
        var pep = 0
        var sanciones = 0
        var noticias = 0
        apariciones.forEach { a ->
            when {
                a.esPep -> pep++
                a.nombreLista != null && CalculoScoring.normalizar(a.nombreLista).contains(palabra) -> noticias++
                a.ordenGrupo != null && a.ordenGrupo in m.gruposListaSanciones -> sanciones++
            }
        }
        return ConsultaListasResultado(pep, sanciones, noticias)
    }

    fun condicion(m: Metodologia, consulta: ConsultaListasResultado): CondicionCliente =
        if (consulta.pep > 0) {
            CondicionCliente(m.puntajeCondicionPep, "PEP", esPep = true, coincidenciasSanciones = consulta.sanciones)
        } else {
            CondicionCliente(m.puntajeCondicionSinCoincidencias, "Sin coincidencias en listas PEP", esPep = false, coincidenciasSanciones = consulta.sanciones)
        }
}
