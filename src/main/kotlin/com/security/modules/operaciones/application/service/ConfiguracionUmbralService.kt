package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.domain.ConfiguracionOperacionesEntity
import com.security.modules.operaciones.domain.Operacion
import com.security.modules.operaciones.domain.TipoCambioOperacionEntity
import com.security.modules.operaciones.domain.UmbralNormaEntity
import com.security.modules.operaciones.dto.*
import com.security.modules.operaciones.infraestructure.ConfiguracionOperacionesRepository
import com.security.modules.operaciones.infraestructure.TipoCambioOperacionRepository
import com.security.modules.operaciones.infraestructure.UmbralNormaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

/** Clave con la que se guardan los datos de una empresa: minusculas y sin espacios sobrantes. */
fun claveEmpresa(empresa: String): String = empresa.trim().lowercase()

/** Lo que vale para una empresa: su sector, el grupo de umbrales, los umbrales, el interno y sus oficinas. */
data class ConfiguracionEfectiva(
    val sector: String?,
    val sectorNombre: String?,
    val grupo: String?,
    val umbrales: List<UmbralNormaEntity>,
    val nota: String?,
    val umbralInternoUsd: BigDecimal?,
    val umbralInternoMotivo: String?,
    val oficinas: List<String>,
    val unRoPorActividad: Boolean,
    val entidad: ConfiguracionOperacionesEntity?
) {
    val porConfigurar: Boolean get() = umbrales.isEmpty()
}

/** Sector, umbral interno y oficinas de cada empresa (RO 16). Solo el administrador guarda. */
@Service
class ConfiguracionOperacionesService(
    private val repository: ConfiguracionOperacionesRepository,
    private val umbrales: UmbralNormaRepository,
    private val catalogos: CatalogosRegistro
) {

    @Transactional(readOnly = true)
    fun de(empresa: String, cat: Catalogos = catalogos.cargar()): ConfiguracionEfectiva {
        val entidad = repository.findByEmpresa(claveEmpresa(empresa))
        val sector = entidad?.sector?.let { cat.opcion(CatalogosOperacion.SECTOR, it) }
        val grupo = sector?.sector
        val lista = grupo?.let { umbrales.findAllByGrupoOrderByOrdenAsc(it) }.orEmpty()
        return ConfiguracionEfectiva(
            sector = sector?.codigo ?: entidad?.sector,
            sectorNombre = sector?.nombre,
            grupo = grupo,
            umbrales = lista,
            nota = grupo?.let { cat.opcion(CatalogosOperacion.NOTA_UMBRAL, it)?.nombre },
            umbralInternoUsd = entidad?.umbralInternoUsd,
            umbralInternoMotivo = entidad?.umbralInternoMotivo,
            oficinas = entidad?.oficinas?.toList().orEmpty(),
            unRoPorActividad = entidad?.unRoPorActividad ?: false,
            entidad = entidad
        )
    }

    fun respuesta(empresa: String, c: ConfiguracionEfectiva, puedeEditar: Boolean) = ConfiguracionResponse(
        empresa = empresa.trim(),
        sector = c.sector,
        sectorNombre = c.sectorNombre,
        porConfigurar = c.porConfigurar,
        unRoPorActividad = c.unRoPorActividad,
        umbralesNorma = c.umbrales.map { UmbralNormaResponse(it.tipoBien, it.nombre, it.umbralUsd) },
        notaUmbral = c.nota,
        umbralInterno = c.umbralInternoUsd?.let { UmbralInternoDto(it, c.umbralInternoMotivo) },
        oficinas = c.oficinas,
        actualizadoEn = c.entidad?.actualizadoEn,
        actualizadoPor = c.entidad?.actualizadoPor,
        puedeEditar = puedeEditar
    )

    @Transactional(readOnly = true)
    fun ver(empresa: String, puedeEditar: Boolean) = respuesta(empresa, de(empresa), puedeEditar)

    @Transactional
    fun guardar(empresa: String, request: ConfiguracionRequest, autor: AutorEvento?): ConfiguracionResponse {
        val cat = catalogos.cargar()
        val errores = linkedMapOf<String, String>()
        val sector = request.sector?.trim()?.takeIf { it.isNotEmpty() }?.let { codigo ->
            cat.opcion(CatalogosOperacion.SECTOR, codigo)?.codigo ?: run { errores["sector"] = "Elige un sector de la lista."; null }
        }
        val grupo = sector?.let { cat.opcion(CatalogosOperacion.SECTOR, it)?.sector }
        val maximoNorma = grupo?.let { umbrales.findAllByGrupoOrderByOrdenAsc(it) }.orEmpty().maxOfOrNull { it.umbralUsd }

        var internoMonto: BigDecimal? = null
        var internoMotivo: String? = null
        request.umbralInterno?.takeIf { it.montoUsd != null || !it.motivo.isNullOrBlank() }?.let { i ->
            val m = i.montoUsd
            when {
                m == null -> errores["umbralInterno.montoUsd"] = "Escribe el monto desde el que registras."
                m.scale() > 2 && m.stripTrailingZeros().scale() > 2 -> errores["umbralInterno.montoUsd"] = "El monto admite hasta dos decimales."
                m.signum() <= 0 -> errores["umbralInterno.montoUsd"] = "El monto debe ser mayor que cero."
                maximoNorma != null && m >= maximoNorma -> errores["umbralInterno.montoUsd"] = "Debe ser menor que el umbral de la norma."
                else -> internoMonto = m.setScale(2, RoundingMode.HALF_UP)
            }
            val motivo = i.motivo?.trim()?.takeIf { it.isNotEmpty() }
            when {
                motivo == null -> errores["umbralInterno.motivo"] = "Indica el motivo del umbral interno."
                motivo.length > 300 -> errores["umbralInterno.motivo"] = "Máximo 300 caracteres."
                else -> internoMotivo = motivo
            }
        }

        val oficinas = mutableListOf<String>()
        request.oficinas.orEmpty().forEachIndexed { i, o ->
            val t = o.trim()
            when {
                t.isEmpty() -> Unit
                t.length > 100 -> errores["oficinas[$i]"] = "Máximo 100 caracteres."
                oficinas.any { it.equals(t, ignoreCase = true) } -> errores["oficinas[$i]"] = "La oficina está repetida."
                else -> oficinas.add(t)
            }
        }
        if (oficinas.size > 50) errores["oficinas"] = "Hasta 50 oficinas."
        if (errores.isNotEmpty()) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.DATOS_INVALIDOS, errores.values.first(), errores.toMap())
        }

        val entidad = repository.findByEmpresa(claveEmpresa(empresa)) ?: ConfiguracionOperacionesEntity(empresa = claveEmpresa(empresa))
        entidad.sector = sector
        entidad.unRoPorActividad = request.unRoPorActividad ?: entidad.unRoPorActividad
        entidad.umbralInternoUsd = internoMonto
        entidad.umbralInternoMotivo = internoMotivo
        entidad.oficinas.clear()
        entidad.oficinas.addAll(oficinas)
        entidad.actualizadoEn = LocalDateTime.now(ZONA_LIMA).withNano(0)
        entidad.actualizadoPor = autor?.nombreCompleto
        repository.save(entidad)
        return respuesta(empresa, de(empresa, cat), true)
    }
}

/** Tipo de cambio mensual cargado a mano (DS 020-2017-JUS, art. 24.4 b). Nunca se inventa ni se completa. */
@Service
class TipoCambioService(
    private val repository: TipoCambioOperacionRepository,
    private val catalogos: CatalogosRegistro
) {

    /** El valor que vale para la empresa: el suyo; si no cargo uno, el de Sulotec. */
    @Transactional(readOnly = true)
    fun vigente(empresa: String, moneda: String, mes: YearMonth): TipoCambioOperacionEntity? =
        repository.findByEmpresaAndMonedaAndMes(claveEmpresa(empresa), moneda, mes.toString())
            ?: repository.findByEmpresaAndMonedaAndMes(TipoCambioOperacionEntity.SULOTEC, moneda, mes.toString())

    fun monedas(cat: Catalogos = catalogos.cargar()): List<String> = cat.monedas.map { it.codigo }.filter { it != "PEN" }

    @Transactional(readOnly = true)
    fun listar(empresa: String?, anio: Int, puedeEditar: Boolean): TiposCambioResponse {
        val ambitos = listOfNotNull(TipoCambioOperacionEntity.SULOTEC, empresa?.let { claveEmpresa(it) })
        val filas = repository.findAllByEmpresaInAndMesStartingWith(ambitos, "$anio-")
        val propios = filas.filter { it.empresa != TipoCambioOperacionEntity.SULOTEC }
        val vigentes = filas.groupBy { it.moneda to it.mes }.map { (_, grupo) ->
            grupo.firstOrNull { it in propios } ?: grupo.first()
        }.sortedWith(compareBy({ it.mes }, { it.moneda }))
        return TiposCambioResponse(anio, monedas(), puedeEditar, vigentes.map { it.respuesta() })
    }

    /** `empresa` null = el de Sulotec (solo el administrador universal llega asi). */
    @Transactional
    fun guardar(empresa: String?, request: TipoCambioRequest, autor: AutorEvento?): TipoCambioResponse {
        val errores = linkedMapOf<String, String>()
        val moneda = request.moneda?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (moneda == null || moneda !in monedas()) errores["moneda"] = "Elige una moneda de la lista."
        val mes = request.mes?.trim()?.let { try { YearMonth.parse(it) } catch (_: DateTimeParseException) { null } }
        if (mes == null) errores["mes"] = "El mes debe ser AAAA-MM."
        else if (mes.isAfter(YearMonth.now(ZONA_LIMA))) errores["mes"] = "El mes no puede ser futuro."
        val valor = request.valor
        when {
            valor == null -> errores["valor"] = "Escribe el tipo de cambio."
            valor.signum() <= 0 -> errores["valor"] = "El tipo de cambio debe ser mayor que cero."
            valor.stripTrailingZeros().scale() > 6 -> errores["valor"] = "Hasta 6 decimales."
            valor.setScale(6, RoundingMode.HALF_UP).precision() > 10 -> errores["valor"] = "Hasta 4 enteros."
        }
        val fuente = request.fuente?.trim()?.takeIf { it.isNotEmpty() }
        if (fuente != null && fuente.length > 200) errores["fuente"] = "Máximo 200 caracteres."
        if (errores.isNotEmpty()) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.DATOS_INVALIDOS, errores.values.first(), errores.toMap())
        }
        val ambito = empresa?.let { claveEmpresa(it) } ?: TipoCambioOperacionEntity.SULOTEC
        val fila = repository.findByEmpresaAndMonedaAndMes(ambito, moneda!!, mes!!.toString())
            ?: TipoCambioOperacionEntity(empresa = ambito, moneda = moneda, mes = mes.toString(), valor = valor!!)
        fila.valor = valor!!.setScale(6, RoundingMode.HALF_UP)
        fila.fuente = fuente
        fila.cargadoPor = autor?.nombreCompleto
        fila.cargadoEn = LocalDateTime.now(ZONA_LIMA).withNano(0)
        return repository.save(fila).respuesta()
    }

    private fun TipoCambioOperacionEntity.respuesta() = TipoCambioResponse(
        moneda, mes, valor, if (empresa == TipoCambioOperacionEntity.SULOTEC) "SULOTEC" else "EMPRESA", fuente, cargadoPor, cargadoEn
    )
}

/**
 * Verificacion de umbral (RO 2–5): monto en US$ con el tipo de cambio del mes anterior a la operacion y comparacion con el
 * umbral de la norma del tipo de bien (o el interno, si es menor). Informativa: nunca bloquea el registro.
 */
@Service
class UmbralService(
    private val configuracion: ConfiguracionOperacionesService,
    private val tiposCambio: TipoCambioService,
    private val catalogos: CatalogosRegistro,
    private val umbralRepositorio: UmbralNormaRepository
) {

    fun evaluar(empresa: String, tipoOperacion: String?, moneda: String?, monto: BigDecimal?, fechaOperacion: LocalDate?,
                cat: Catalogos = catalogos.cargar()): VerificacionUmbralResponse {
        val config = configuracion.de(empresa, cat)
        val tipo = cat.opcion(CatalogosOperacion.TIPO_OPERACION, tipoOperacion)
        val tipoBien = tipo?.tipoBien
        val norma = config.umbrales.firstOrNull { it.tipoBien == tipoBien }
        val mesTc = YearMonth.from(fechaOperacion ?: LocalDate.now(ZONA_LIMA)).minusMonths(1)
        val codigoMoneda = cat.moneda(moneda)?.codigo

        // Monto en US$ (se calcula aunque el sector no este configurado: sirve para el filtro de la lista)
        var montoUsd: BigDecimal? = null
        var tc: TipoCambioUsado? = null
        var nota: String? = null
        var faltaTc = false
        if (monto != null && monto.signum() > 0) {
            when (codigoMoneda) {
                "USD" -> { montoUsd = monto.setScale(2, RoundingMode.HALF_UP); nota = "No aplica (USD)" }
                null -> faltaTc = true
                else -> {
                    val usd = tiposCambio.vigente(empresa, "USD", mesTc)?.valor
                    val otra = if (codigoMoneda == "PEN") null else tiposCambio.vigente(empresa, codigoMoneda, mesTc)?.valor
                    if (usd == null || (codigoMoneda != "PEN" && otra == null)) faltaTc = true
                    else {
                        val soles = if (otra == null) monto else monto.multiply(otra)
                        montoUsd = soles.divide(usd, 2, RoundingMode.HALF_UP)
                        tc = TipoCambioUsado(mesTc.toString(), usd, otra)
                        nota = "Promedio de venta de ${nombreMes(mesTc)}"
                    }
                }
            }
        }
        val umbralAplicado = norma?.umbralUsd?.let { n -> config.umbralInternoUsd?.takeIf { it < n } ?: n }
        val interno = norma != null && config.umbralInternoUsd != null && config.umbralInternoUsd < norma.umbralUsd
        val (estado, mensaje) = when {
            monto == null || monto.signum() <= 0 -> SIN_MONTO to "Ingresa el monto para saber si debe registrarse en el RO."
            config.porConfigurar -> SIN_CONFIGURAR to "Define tu sector para saber qué operaciones deben ir al registro y desde qué monto."
            norma == null -> SIN_TIPO_DE_BIEN to "Este tipo de operación no tiene un umbral definido."
            montoUsd == null -> SIN_TIPO_DE_CAMBIO to
                (if (codigoMoneda == null) "No hay tipo de cambio para esta moneda." else "Falta el tipo de cambio de ${nombreMes(mesTc)}.")
            montoUsd >= umbralAplicado!! -> SI_SE_REGISTRA to "Igual o superior al umbral: debe registrarse en el RO."
            else -> NO_ALCANZA_UMBRAL to "Menor que el umbral: no es obligatorio registrarla en el RO."
        }
        if (faltaTc && nota == null && codigoMoneda != null) nota = "Falta el tipo de cambio de ${nombreMes(mesTc)}"
        return VerificacionUmbralResponse(
            estado = estado, mensaje = mensaje,
            sector = config.sector, sectorNombre = config.sectorNombre,
            tipoBien = tipoBien, tipoBienNombre = norma?.nombre,
            umbralUsd = umbralAplicado, umbralNormaUsd = norma?.umbralUsd, umbralInterno = if (norma == null) null else interno,
            montoUsd = montoUsd, tipoCambio = tc, tipoCambioNota = nota
        )
    }

    /** Guarda la verificacion en la operacion. Devuelve true si cambio el resultado guardado. */
    fun aplicar(o: Operacion, v: VerificacionUmbralResponse, cuando: LocalDateTime): Boolean {
        fun distinto(a: BigDecimal?, b: BigDecimal?) = if (a == null || b == null) a != b else a.compareTo(b) != 0
        val cambio = o.umbralEstado != v.estado || distinto(o.montoUsd, v.montoUsd) || distinto(o.umbralUsd, v.umbralUsd)
        o.umbralEstado = v.estado
        o.umbralUsd = v.umbralUsd
        o.umbralNormaUsd = v.umbralNormaUsd
        o.umbralInterno = v.umbralInterno
        o.tipoBien = v.tipoBien
        o.sector = v.sector
        o.montoUsd = v.montoUsd
        o.tcMes = v.tipoCambio?.mes
        o.tcUsd = v.tipoCambio?.usd
        o.tcMoneda = v.tipoCambio?.moneda
        o.umbralVerificadoEn = cuando
        return cambio
    }

    /** Lo guardado con el registro (null en los registros anteriores a la verificacion). */
    fun guardada(o: Operacion, cat: Catalogos): VerificacionUmbralResponse? {
        val estado = o.umbralEstado ?: return null
        val sector = o.sector?.let { cat.opcion(CatalogosOperacion.SECTOR, it) }
        val tipoBienNombre = sector?.sector?.let { grupo ->
            umbralRepositorio.findAllByGrupoOrderByOrdenAsc(grupo).firstOrNull { it.tipoBien == o.tipoBien }?.nombre
        }
        return VerificacionUmbralResponse(
            estado = estado, mensaje = mensajeGuardado(o),
            sector = o.sector, sectorNombre = sector?.nombre,
            tipoBien = o.tipoBien, tipoBienNombre = tipoBienNombre,
            umbralUsd = o.umbralUsd, umbralNormaUsd = o.umbralNormaUsd, umbralInterno = o.umbralInterno,
            montoUsd = o.montoUsd,
            tipoCambio = o.tcMes?.let { TipoCambioUsado(it, o.tcUsd, o.tcMoneda) },
            tipoCambioNota = when {
                o.tcMes != null -> "Promedio de venta de ${nombreMes(YearMonth.parse(o.tcMes))}"
                o.montoUsd != null -> "No aplica (USD)"
                else -> null
            }
        )
    }

    companion object {
        const val SIN_MONTO = "SIN_MONTO"
        const val SIN_CONFIGURAR = "SIN_CONFIGURAR"
        const val SIN_TIPO_DE_BIEN = "SIN_TIPO_DE_BIEN"
        const val SIN_TIPO_DE_CAMBIO = "SIN_TIPO_DE_CAMBIO"
        const val SI_SE_REGISTRA = "SI_SE_REGISTRA"
        const val NO_ALCANZA_UMBRAL = "NO_ALCANZA_UMBRAL"

        fun nombreMes(mes: YearMonth): String =
            mes.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")) + " de " + mes.year

        fun mensajeGuardado(o: Operacion): String = when (o.umbralEstado) {
            SIN_MONTO -> "Ingresa el monto para saber si debe registrarse en el RO."
            SIN_CONFIGURAR -> "Define tu sector para saber qué operaciones deben ir al registro y desde qué monto."
            SIN_TIPO_DE_BIEN -> "Este tipo de operación no tiene un umbral definido."
            SIN_TIPO_DE_CAMBIO -> "Faltaba el tipo de cambio al verificar."
            SI_SE_REGISTRA -> "Igual o superior al umbral: debe registrarse en el RO."
            else -> "Menor que el umbral: no es obligatorio registrarla en el RO."
        }

        /** Detalle del evento «Verificación de umbral» del historial. */
        fun detalleEvento(v: VerificacionUmbralResponse): String = when (v.estado) {
            SI_SE_REGISTRA -> "US$ ${FotoOperacion.monto(v.montoUsd)} es igual o superior a US$ ${FotoOperacion.monto(v.umbralUsd)}: debe registrarse en el RO."
            NO_ALCANZA_UMBRAL -> "US$ ${FotoOperacion.monto(v.montoUsd)} es menor que US$ ${FotoOperacion.monto(v.umbralUsd)}: no es obligatorio registrarla en el RO."
            else -> v.mensaje
        }
    }
}
