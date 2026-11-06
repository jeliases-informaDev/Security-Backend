package com.security.modules.oficial.aplicacion

import com.security.modules.oficial.dominio.CalculadoraPlazos
import com.security.modules.oficial.dominio.Plazo
import com.security.modules.oficial.dominio.UnidadPlazo
import com.security.modules.oficial.dominio.enumDe
import com.security.modules.oficial.persistencia.OcFeriado
import com.security.modules.oficial.persistencia.OcFeriadoRepository
import com.security.modules.oficial.persistencia.OcRegla
import com.security.modules.oficial.persistencia.OcReglaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** Valores por defecto de las reglas (DECISIONES §2.4 y la norma). En la base son datos: el valor se puede cambiar. */
object ReglasPorDefecto {
    data class Def(val codigo: String, val valor: Int, val unidad: UnidadPlazo, val articulo: String?, val descripcion: String)

    const val PLAZO_COMUNICAR_DESIGNACION = "PLAZO_COMUNICAR_DESIGNACION"
    const val PLAZO_COMUNICAR_CAMBIO = "PLAZO_COMUNICAR_CAMBIO"
    const val PLAZO_COMUNICAR_REMOCION = "PLAZO_COMUNICAR_REMOCION"
    const val VACANCIA_MAXIMA = "VACANCIA_MAXIMA"
    const val ALTERNO_MAXIMO = "ALTERNO_MAXIMO"
    const val AVISO_AUSENCIA = "AVISO_AUSENCIA"
    const val AVISO_PERDIDA_REQUISITO = "AVISO_PERDIDA_REQUISITO"
    const val REVISION_SUGERIDA = "REVISION_SUGERIDA"
    const val UMBRAL_POR_VENCER = "UMBRAL_POR_VENCER"

    val TODAS = listOf(
        Def(PLAZO_COMUNICAR_DESIGNACION, 15, UnidadPlazo.DIAS_HABILES, "Res. SBS 789-2018, art. 7.2", "Comunicar la designación a la UIF"),
        Def(PLAZO_COMUNICAR_CAMBIO, 5, UnidadPlazo.DIAS_HABILES, "Res. SBS 789-2018, art. 7.4", "Comunicar un cambio de datos del oficial"),
        Def(PLAZO_COMUNICAR_REMOCION, 5, UnidadPlazo.DIAS_HABILES, "Res. SBS 789-2018, art. 7.5, 7.6 y 6.2", "Comunicar la remoción o vacancia a la UIF"),
        Def(VACANCIA_MAXIMA, 30, UnidadPlazo.DIAS, "Res. SBS 789-2018, art. 7.6", "Máximo de vacancia del cargo"),
        Def(ALTERNO_MAXIMO, 4, UnidadPlazo.MESES, "Res. SBS 789-2018, art. 8.2 c", "Ausencia temporal cubierta por el alterno"),
        Def(AVISO_AUSENCIA, 5, UnidadPlazo.DIAS_HABILES, "Res. SBS 789-2018, art. 8.2 b", "Aviso previo de una ausencia temporal"),
        Def(AVISO_PERDIDA_REQUISITO, 5, UnidadPlazo.DIAS_HABILES, "Res. SBS 789-2018, art. 6.2", "Aviso del oficial que deja de cumplir un requisito"),
        Def(REVISION_SUGERIDA, 12, UnidadPlazo.MESES, null, "Revisión sugerida de requisitos (deducido del diseño, por validar)"),
        Def(UMBRAL_POR_VENCER, 3, UnidadPlazo.DIAS_HABILES, null, "Días restantes desde los que un plazo está «Por vencer»")
    )

    fun de(codigo: String) = TODAS.firstOrNull { it.codigo == codigo }
}

data class ReglaResponse(val codigo: String, val valor: Int, val unidad: String, val articulo: String?, val descripcion: String?)
data class FeriadoResponse(val fecha: LocalDate, val nombre: String, val fuente: String)
data class FeriadoRequest(val fecha: LocalDate? = null, val nombre: String? = null, val fuente: String? = null)
data class ReglaRequest(val valor: Int? = null)

/** Reglas de plazos y feriados: arma la calculadora y responde los plazos ya resueltos. */
@Service
class ReglasOficialService(
    private val reglaRepository: OcReglaRepository,
    private val feriadoRepository: OcFeriadoRepository,
    private val reloj: RelojOficial
) {

    /** Lo que se usa en un pedido: reglas y feriados leidos una vez. */
    inner class Motor(private val reglas: Map<String, OcRegla>, val calculadora: CalculadoraPlazos) {
        fun valor(codigo: String): Int = reglas[codigo]?.valor ?: ReglasPorDefecto.de(codigo)?.valor ?: 0
        fun unidad(codigo: String): UnidadPlazo =
            enumDe<UnidadPlazo>(reglas[codigo]?.unidad) ?: ReglasPorDefecto.de(codigo)?.unidad ?: UnidadPlazo.DIAS

        fun plazo(codigo: String, desde: LocalDate): Plazo =
            calculadora.plazo(codigo, unidad(codigo), valor(codigo), desde, reloj.hoy())

        fun vencimiento(codigo: String, desde: LocalDate): LocalDate =
            calculadora.vencimiento(unidad(codigo), desde, valor(codigo))
    }

    @Transactional(readOnly = true)
    fun motor(): Motor {
        val reglas = reglaRepository.findAll().associateBy { it.codigo }
        val feriados = feriadoRepository.findAll().map { it.fecha }.toSet()
        val umbral = reglas[ReglasPorDefecto.UMBRAL_POR_VENCER]?.valor
            ?: ReglasPorDefecto.de(ReglasPorDefecto.UMBRAL_POR_VENCER)!!.valor
        return Motor(reglas, CalculadoraPlazos(feriados, umbral))
    }

    @Transactional(readOnly = true)
    fun reglas(): List<ReglaResponse> {
        val guardadas = reglaRepository.findAll().associateBy { it.codigo }
        return ReglasPorDefecto.TODAS.map { def ->
            val r = guardadas[def.codigo]
            ReglaResponse(def.codigo, r?.valor ?: def.valor, r?.unidad ?: def.unidad.name, r?.articulo ?: def.articulo, r?.descripcion ?: def.descripcion)
        }
    }

    @Transactional(readOnly = true)
    fun plazo(regla: String?, desde: LocalDate?): Plazo {
        val v = Validador()
        val def = regla?.trim()?.uppercase()?.let { ReglasPorDefecto.de(it) }
        if (def == null || def.codigo == ReglasPorDefecto.UMBRAL_POR_VENCER) v.error("regla", "Esa regla de plazo no existe.")
        if (desde == null) v.error("desde", "Indica la fecha desde la que corre el plazo.")
        v.lanzarSiHay()
        return motor().plazo(def!!.codigo, desde!!)
    }

    @Transactional
    fun cambiarRegla(ctx: ContextoOc, codigo: String, pedido: ReglaRequest): ReglaResponse {
        ctx.exigirUniversal()
        val def = ReglasPorDefecto.de(codigo.trim().uppercase()) ?: throw Oc.noEncontrado("Esa regla no existe.")
        val valor = pedido.valor
        if (valor == null || valor < 1 || valor > 3650) throw Oc.dato("valor", "Indica un número entero entre 1 y 3650.")
        val regla = reglaRepository.findById(def.codigo).orElse(null) ?: nueva(def)
        regla.valor = valor
        regla.actualizadaEn = reloj.ahora()
        regla.actualizadaPor = ctx.actor.usuario
        val g = reglaRepository.save(regla)
        return ReglaResponse(g.codigo, g.valor, g.unidad, g.articulo, g.descripcion)
    }

    @Transactional(readOnly = true)
    fun feriados(anio: Int?): List<FeriadoResponse> {
        val lista = if (anio != null) {
            feriadoRepository.findByFechaBetweenOrderByFechaAsc(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31))
        } else feriadoRepository.findAllByOrderByFechaAsc()
        return lista.map { FeriadoResponse(it.fecha, it.nombre, it.fuente) }
    }

    @Transactional(readOnly = true)
    fun cantidadFeriados(): Long = feriadoRepository.count()

    @Transactional
    fun agregarFeriado(ctx: ContextoOc, pedido: FeriadoRequest): FeriadoResponse {
        ctx.exigirUniversal()
        val v = Validador()
        if (pedido.fecha == null) v.error("fecha", "Indica la fecha del feriado.")
        val nombre = v.texto("nombre", pedido.nombre, 150, true, "Escribe el nombre del feriado.")
        val fuente = v.texto("fuente", pedido.fuente, 255, true, "Indica la fuente oficial donde se confirmó.")
        v.lanzarSiHay()
        if (feriadoRepository.existsById(pedido.fecha!!)) {
            throw OficialException(HttpStatus.CONFLICT, Oc.FERIADO_DUPLICADO, "Ese feriado ya está registrado.")
        }
        val g = feriadoRepository.save(OcFeriado(pedido.fecha, nombre!!, fuente!!, reloj.ahora(), ctx.actor.usuario))
        return FeriadoResponse(g.fecha, g.nombre, g.fuente)
    }

    @Transactional
    fun quitarFeriado(ctx: ContextoOc, fecha: LocalDate) {
        ctx.exigirUniversal()
        if (!feriadoRepository.existsById(fecha)) throw Oc.noEncontrado("Ese feriado no está registrado.")
        feriadoRepository.deleteById(fecha)
    }

    /** Agrega las reglas que falten con su valor por defecto; nunca cambia un valor ya guardado. */
    @Transactional
    fun sembrarReglas(): Int {
        val existentes = reglaRepository.findAll().map { it.codigo }.toSet()
        val faltan = ReglasPorDefecto.TODAS.filter { it.codigo !in existentes }
        faltan.forEach { reglaRepository.save(nueva(it)) }
        return faltan.size
    }

    private fun nueva(def: ReglasPorDefecto.Def) =
        OcRegla(def.codigo, def.valor, def.unidad.name, def.articulo, def.descripcion)
}

/** Al arrancar deja las reglas sembradas. Los feriados NO se siembran (solo los confirmados, a mano). */
@Component
@Order(6)
class OficialInicializador(private val reglas: ReglasOficialService) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            val n = reglas.sembrarReglas()
            if (n > 0) log.info("Oficial de cumplimiento: se agregaron {} reglas de plazos.", n)
        } catch (e: Exception) {
            log.warn("No se pudieron preparar las reglas del oficial de cumplimiento: {}", e.message)
        }
    }
}
