package com.security.modules.listas.application.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.security.modules.listas.domain.ListaGrupoPeso
import com.security.modules.listas.domain.ListaRegla
import com.security.modules.listas.domain.ListaReglaOtras
import com.security.modules.listas.domain.reglas.CalculoListas
import com.security.modules.listas.domain.reglas.FilaDef
import com.security.modules.listas.domain.reglas.GrupoPesoDef
import com.security.modules.listas.domain.reglas.ListaDisponible
import com.security.modules.listas.domain.reglas.OtrasDef
import com.security.modules.listas.domain.reglas.ReglasListas
import com.security.modules.listas.domain.reglas.TipoConteo
import com.security.modules.listas.infrastructure.ListaGrupoPesoRepository
import com.security.modules.listas.infrastructure.ListaReglaOtrasRepository
import com.security.modules.listas.infrastructure.ListaReglaRepository
import com.security.modules.listas.infrastructure.TipoListaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

// ---------------- Archivo de valores iniciales (src/main/resources/listas/reglas-listas.json) ----------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class SemillaGrupoPeso(val codigo: String = "", val nombre: String = "", val peso: Int = 0, val orden: Int = 0, val critica: Boolean = false)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SemillaFila(
    val codigo: String = "",
    val orden: Int = 0,
    val nombre: String = "",
    val subtitulo: String? = null,
    val fuente: String = "",
    val fuenteNombre: String = "",
    val grupoPeso: String = "",
    val estado: String = ListaRegla.ESTADO_CONSULTABLE,
    val motivoPronto: String? = null,
    val todasPep: Boolean = false,
    val listas: List<String> = emptyList(),
    val tipoConteo: String = "REGISTROS",
    val etiquetaAviso: String = "",
    val etiquetaSuma: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SemillaOtras(val ordenGrupo: Int = 0, val grupoPeso: String = "")

@JsonIgnoreProperties(ignoreUnknown = true)
data class TipoDocumentoConsulta(val codigo: String = "", val nombre: String = "", val patron: String? = null, val ayuda: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TipoConsultaDef(val codigo: String = "", val nombre: String = "", val tiposDocumento: List<String> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class SemillaReglas(
    val gruposPeso: List<SemillaGrupoPeso> = emptyList(),
    val filas: List<SemillaFila> = emptyList(),
    val otras: List<SemillaOtras> = emptyList(),
    val tiposDocumento: List<TipoDocumentoConsulta> = emptyList(),
    val tipoDocumentoInicial: String? = null,
    val tiposConsulta: List<TipoConsultaDef> = emptyList(),
    val tipoConsultaInicial: String? = null
) {
    fun reglas() = ReglasListas(
        gruposPeso = gruposPeso.map { GrupoPesoDef(it.codigo, it.nombre, it.peso, it.orden, it.critica) },
        filas = filas.map {
            FilaDef(
                codigo = it.codigo, nombre = it.nombre, subtitulo = it.subtitulo, orden = it.orden, fuente = it.fuente,
                fuenteNombre = it.fuenteNombre, grupoPeso = it.grupoPeso,
                consultable = it.estado.equals(ListaRegla.ESTADO_CONSULTABLE, ignoreCase = true),
                motivoPronto = it.motivoPronto, todasPep = it.todasPep, listas = it.listas,
                tipoConteo = TipoConteo.de(it.tipoConteo), etiquetaAviso = it.etiquetaAviso, etiquetaSuma = it.etiquetaSuma
            )
        },
        otras = otras.map { OtrasDef(it.ordenGrupo, it.grupoPeso) }
    )

    companion object {
        const val RECURSO = "listas/reglas-listas.json"

        fun leer(): SemillaReglas =
            jacksonObjectMapper().readValue<SemillaReglas>(ClassPathResource(RECURSO).inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
    }
}

/**
 * Las reglas de Listas Negativas tal como estan en la base (o, si las tablas aun estan vacias, las del archivo de
 * valores iniciales), las listas que existen en tipo_lista y el catalogo de la pantalla de consulta.
 */
@Service
class ReglasListasService(
    private val grupoPesoRepository: ListaGrupoPesoRepository,
    private val reglaRepository: ListaReglaRepository,
    private val otrasRepository: ListaReglaOtrasRepository,
    private val tipoListaRepository: TipoListaRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val semilla: SemillaReglas by lazy { SemillaReglas.leer() }

    @Transactional(readOnly = true)
    fun reglas(): ReglasListas {
        val grupos = grupoPesoRepository.findAll()
        val filas = reglaRepository.findAll()
        if (grupos.isEmpty() || filas.isEmpty()) return semilla.reglas()
        return ReglasListas(
            gruposPeso = grupos.map { GrupoPesoDef(it.codigo, it.nombre, it.peso, it.orden, it.critica) },
            filas = filas.map {
                FilaDef(
                    codigo = it.codigo, nombre = it.nombre, subtitulo = it.subtitulo, orden = it.orden, fuente = it.fuente,
                    fuenteNombre = it.fuenteNombre, grupoPeso = it.grupoPeso,
                    consultable = it.estado.trim().equals(ListaRegla.ESTADO_CONSULTABLE, ignoreCase = true),
                    motivoPronto = it.motivoPronto, todasPep = it.todasPep, listas = it.listas.toList(),
                    tipoConteo = TipoConteo.de(it.tipoConteo), etiquetaAviso = it.etiquetaAviso, etiquetaSuma = it.etiquetaSuma
                )
            },
            otras = otrasRepository.findAll().map { OtrasDef(it.ordenGrupo, it.grupoPeso) }
        )
    }

    @Transactional(readOnly = true)
    fun listasDisponibles(): List<ListaDisponible> =
        tipoListaRepository.findAll().filter { !it.nombre.isNullOrBlank() }
            .map { ListaDisponible(it.id, it.nombre!!, it.esPep, it.ultimaCarga) }

    /**
     * Crea lo que falta de los valores iniciales y nunca toca lo que ya existe (por codigo; las otras listas, por grupo).
     * Devuelve cuantas filas creo de cada tabla.
     */
    @Transactional
    fun sembrar(s: SemillaReglas = semilla): Triple<Int, Int, Int> {
        var grupos = 0
        s.gruposPeso.forEach { g ->
            if (grupoPesoRepository.findByCodigo(g.codigo) == null) {
                grupoPesoRepository.save(ListaGrupoPeso(codigo = g.codigo, nombre = g.nombre, peso = g.peso, orden = g.orden, critica = g.critica))
                grupos++
            }
        }
        var filas = 0
        s.filas.forEach { f ->
            if (reglaRepository.findByCodigo(f.codigo) == null) {
                reglaRepository.save(
                    ListaRegla(
                        codigo = f.codigo, nombre = f.nombre, subtitulo = f.subtitulo, orden = f.orden, fuente = f.fuente,
                        fuenteNombre = f.fuenteNombre, grupoPeso = f.grupoPeso, estado = f.estado.uppercase(),
                        motivoPronto = f.motivoPronto, todasPep = f.todasPep, tipoConteo = f.tipoConteo.uppercase(),
                        etiquetaAviso = f.etiquetaAviso, etiquetaSuma = f.etiquetaSuma, listas = f.listas.toMutableList()
                    )
                )
                filas++
            }
        }
        var otras = 0
        s.otras.forEach { o ->
            if (otrasRepository.findByOrdenGrupo(o.ordenGrupo) == null) {
                otrasRepository.save(ListaReglaOtras(ordenGrupo = o.ordenGrupo, grupoPeso = o.grupoPeso))
                otras++
            }
        }
        return Triple(grupos, filas, otras)
    }

    /** Avisa en el log las listas que nombran las reglas y que no existen en tipo_lista (esas filas quedan "Pronto"). */
    @Transactional(readOnly = true)
    fun avisarListasFaltantes(): Int {
        val faltan = CalculoListas.listasFaltantes(reglas(), listasDisponibles())
        faltan.forEach { (fila, lista) ->
            log.warn("Listas Negativas: la fila {} usa la lista '{}', que no existe en tipo_lista", fila, lista)
        }
        return faltan.size
    }
}

/**
 * Siembra las reglas de Listas Negativas al arrancar, SIEMPRE (tambien con APP_SEED_ENABLED apagado, como en
 * produccion). Es idempotente y no pisa lo cambiado a mano. Un fallo se registra en el log y no impide arrancar.
 */
@Component
@Order(3)
class ReglasListasInicializador(
    private val reglas: ReglasListasService,
    transactionManager: PlatformTransactionManager
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    override fun run(args: ApplicationArguments) {
        try {
            val (grupos, filas, otras) = tx.execute { reglas.sembrar() } ?: Triple(0, 0, 0)
            log.info("Listas Negativas: reglas nuevas: grupos de peso={}, filas={}, otras listas={}", grupos, filas, otras)
            val faltan = reglas.avisarListasFaltantes()
            if (faltan == 0) log.info("Listas Negativas: todas las listas de las reglas existen en tipo_lista")
        } catch (e: Exception) {
            log.warn("Listas Negativas: no se pudieron preparar las reglas: {}", e.message)
        }
    }
}
