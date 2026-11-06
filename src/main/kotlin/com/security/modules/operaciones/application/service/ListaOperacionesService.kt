package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.dto.FiltrosOperaciones
import com.security.modules.operaciones.dto.IndicadoresOperacionesResponse
import com.security.modules.operaciones.dto.ListarOperacionesResponse
import com.security.modules.operaciones.dto.OperacionItemDto
import com.security.modules.operaciones.dto.UsuarioAsignableResponse
import com.security.modules.operaciones.infraestructure.ListaOperacionesConsulta
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/** Parametros de la lista tal como llegan (los valida ListaOperacionesService). */
data class ParametrosLista(
    val q: String? = null,
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
    val tipoOperacion: String? = null,
    val modalidadPago: String? = null,
    val moneda: String? = null,
    val responsableId: Int? = null,
    val sinResponsable: Boolean? = null,
    val estado: String? = null,
    val estados: List<String> = emptyList(),
    val oficina: String? = null,
    val montoMinUsd: java.math.BigDecimal? = null,
    val montoMaxUsd: java.math.BigDecimal? = null
)

/** Lista (RO 1, buscador y filtros de RO 11) e indicadores, siempre de una sola empresa (la decide el controlador). */
@Service
class ListaOperacionesService(
    private val consulta: ListaOperacionesConsulta,
    private val catalogos: CatalogosRegistro
) {

    fun listar(empresa: String, parametros: ParametrosLista, page: Int, size: Int): ListarOperacionesResponse {
        val nombreEmpresa = empresa.trim().ifEmpty {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.DATOS_INVALIDOS, "La empresa es obligatoria")
        }
        if (page < 0 || size < 1 || size > TAMANO_MAXIMO) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.PAGINA_INVALIDA,
                "page debe ser 0 o más y size entre 1 y $TAMANO_MAXIMO")
        }
        val cat = catalogos.cargar()
        val filtros = filtros(parametros, cat)
        val (filas, total) = consulta.listar(nombreEmpresa, filtros, page, size)
        val items = filas.map { f ->
            val moneda = cat.moneda(f.moneda)
            OperacionItemDto(
                id = f.id,
                numeroRegistro = f.numeroRegistro,
                personaFisica = f.personaFisica,
                personaNombre = f.personaNombre,
                oficina = f.oficina,
                fecha = f.fecha,
                responsable = f.responsableId?.let { UsuarioAsignableResponse(it, f.responsableNombre.orEmpty()) },
                creadoEn = f.creadoEn,
                fechaOperacion = f.fechaOperacion,
                tipoOperacion = f.tipoOperacion,
                tipoOperacionNombre = cat.nombre(CatalogosOperacion.TIPO_OPERACION, f.tipoOperacion),
                modalidadPago = f.modalidadPago,
                modalidadPagoNombre = cat.nombre(CatalogosOperacion.MODALIDAD_PAGO, f.modalidadPago),
                monto = f.monto,
                moneda = f.moneda,
                monedaCodigo = moneda?.codigo,
                monedaSimbolo = moneda?.simbolo,
                estado = estadoRegistro(f.estadoAnalisis, f.fechaEnvio),
                estadoEnvio = if (f.fechaEnvio != null) ESTADO_ENVIADO else ESTADO_PENDIENTE,
                inusualidad = f.estadoAnalisis ?: "SIN_ANALISIS",
                montoUsd = f.montoUsd,
                umbralEstado = f.umbralEstado
            )
        }
        val paginas = if (total == 0L) 0 else ((total + size - 1) / size).toInt()
        return ListarOperacionesResponse(items, paginas, total, page, size)
    }

    fun indicadores(empresa: String, periodo: String?): IndicadoresOperacionesResponse {
        val mes = periodo?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                YearMonth.parse(it)
            } catch (_: DateTimeParseException) {
                throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.PERIODO_INVALIDO, "El periodo debe ser AAAA-MM")
            }
        } ?: YearMonth.now(ZONA_LIMA)
        val c = consulta.conteos(empresa.trim(), mes.atDay(1), mes.atEndOfMonth())
        return IndicadoresOperacionesResponse(
            periodo = mes.toString(), registrosDelPeriodo = c.delPeriodo, pendientesDeEnvio = c.total - c.enviados,
            sinResponsable = c.sinResponsable, total = c.total, enviados = c.enviados,
            inusualesEnAnalisis = c.inusuales, sospechosas = c.sospechosas
        )
    }

    private fun filtros(p: ParametrosLista, cat: Catalogos): FiltrosOperaciones {
        fun invalido(mensaje: String): Nothing =
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.FILTRO_INVALIDO, mensaje)

        // estado puede repetirse o ir separado por comas: PENDIENTE (sin constancia), ENVIADO, INUSUAL (en analisis o sospechosa)
        val estados = (listOfNotNull(p.estado) + p.estados)
            .flatMap { it.split(",") }.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        estados.firstOrNull { it !in setOf(ESTADO_PENDIENTE, ESTADO_ENVIADO, ESTADO_INUSUAL) }?.let {
            invalido("El estado debe ser PENDIENTE, ENVIADO o INUSUAL")
        }
        if (p.montoMinUsd != null && p.montoMaxUsd != null && p.montoMinUsd > p.montoMaxUsd) invalido("El monto mínimo es mayor que el máximo")
        if (p.desde != null && p.hasta != null && p.desde.isAfter(p.hasta)) invalido("La fecha «desde» es posterior a «hasta»")

        // Un codigo encuentra tambien los registros viejos que guardaron el nombre escrito.
        fun opcion(catalogo: String, valor: String?, nombre: String): List<String> {
            val v = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
            val o = cat.opcion(catalogo, v) ?: invalido("$nombre no existe")
            return listOf(o.codigo, o.nombre)
        }
        val moneda = p.moneda?.trim()?.takeIf { it.isNotEmpty() }?.let { v ->
            cat.monedas.firstOrNull { it.codigo.equals(v, ignoreCase = true) } ?: invalido("La moneda no existe")
        }
        return FiltrosOperaciones(
            q = p.q?.trim()?.takeIf { it.isNotEmpty() }?.take(100),
            desde = p.desde,
            hasta = p.hasta,
            tipoOperacion = opcion(CatalogosOperacion.TIPO_OPERACION, p.tipoOperacion, "El tipo de operación"),
            modalidadPago = opcion(CatalogosOperacion.MODALIDAD_PAGO, p.modalidadPago, "La modalidad de pago"),
            moneda = moneda?.codigo,
            monedaId = moneda?.id,
            responsableId = p.responsableId,
            sinResponsable = p.sinResponsable == true,
            estados = estados,
            oficina = p.oficina?.trim()?.takeIf { it.isNotEmpty() },
            montoMinUsd = p.montoMinUsd,
            montoMaxUsd = p.montoMaxUsd
        )
    }

    companion object {
        const val TAMANO_MAXIMO = 100
    }
}
