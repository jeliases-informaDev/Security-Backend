package com.security.modules.operaciones.application.service

import com.security.modules.denuncias.application.port.ArchivoStorageService
import com.security.modules.denuncias.application.service.ValidadorDocumentoDenunciaService
import com.security.modules.operaciones.domain.AnalisisOperacionEntity
import com.security.modules.operaciones.domain.EventoOperacionEntity
import com.security.modules.operaciones.domain.ExportacionOperacionesEntity
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.dto.*
import com.security.modules.operaciones.infraestructure.*
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Exportar (RO 14) y constancia de envio (RO 15). El unico formato con estructura es el interno «RO a disposicion de la UIF»
 * (Excel y CSV, una fila por operacion): no es una plantilla oficial de la SBS, que no publica una para este sector. Los
 * formatos de la SBS quedan «Pronto». Periodo = mes de la fecha de la operacion; se exportan los pendientes de envio.
 * Todo es de una sola empresa (la decide el controlador).
 */
@Service
class ExportacionOperacionesService(
    private val consulta: ListaOperacionesConsulta,
    private val operaciones: OperacionRepository,
    private val exportaciones: ExportacionOperacionesRepository,
    private val analisis: AnalisisOperacionRepository,
    private val usuarios: UsuarioRepository,
    private val catalogos: CatalogosRegistro,
    private val catalogoRepository: CatalogoRepository,
    private val configuracion: ConfiguracionOperacionesService,
    private val historial: HistorialOperacionService,
    private val validadorDocumento: ValidadorDocumentoDenunciaService,
    private val almacenamiento: ArchivoStorageService
) {

    private fun periodo(valor: String?): YearMonth = try {
        YearMonth.parse(valor?.trim().orEmpty())
    } catch (_: DateTimeParseException) {
        throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.PERIODO_INVALIDO, "El periodo debe ser AAAA-MM")
    }

    private fun formato(codigo: String?, cat: Catalogos): OpcionRegistro =
        cat.opcion(CatalogosOperacion.FORMATO_EXPORTACION, codigo?.trim()?.takeIf { it.isNotEmpty() } ?: CatalogosOperacion.FORMATO_DISPOSICION)
            ?: throw CodigosOperaciones.invalido("formato", "Elige un formato de la lista.")

    private fun disponible(f: OpcionRegistro) = f.codigo == CatalogosOperacion.FORMATO_DISPOSICION

    /** Registros del periodo de la empresa (todos) y los pendientes de envio, ya cargados. */
    private fun delPeriodo(empresa: String, mes: YearMonth): Pair<Int, List<RegistroOperacionesEntity>> {
        val filas = consulta.delPeriodo(empresa, mes.atDay(1), mes.atEndOfMonth())
        val pendientes = filas.filter { it.second == null }.map { it.first }
        val entidades = operaciones.findAllById(pendientes).sortedWith(compareBy({ it.operacion.fechaOperacion }, { it.id }))
        return filas.size to entidades
    }

    @Transactional(readOnly = true)
    fun validacion(empresa: String, periodoTexto: String?, formatoCodigo: String?, puedeGenerar: Boolean): ValidacionExportacionResponse {
        val mes = periodo(periodoTexto)
        val cat = catalogos.cargar()
        val f = formato(formatoCodigo, cat)
        val (total, pendientes) = delPeriodo(empresa, mes)
        val observaciones = observaciones(pendientes, cat)
        val textos = observaciones.groupBy { it.texto }.keys
        val chequeos = listOf(
            ChequeoResponse("PERSONA_Y_BENEFICIARIO", "Todos los registros tienen persona que realiza y beneficiario",
                textos.none { it == FALTA_BENEFICIARIO || it == FALTA_DOCUMENTO }),
            ChequeoResponse("MONEDAS_Y_PAISES", "Monedas y países con código válido",
                textos.none { it == MONEDA_INVALIDA || it == PAIS_INVALIDO }),
            ChequeoResponse("MONTOS_NUMERICOS", "Todos los montos están en formato numérico", true)
        )
        return ValidacionExportacionResponse(
            periodo = mes.toString(), formato = f.codigo, formatoNombre = f.nombre, formatoDisponible = disponible(f),
            registros = total.toLong(), pendientes = pendientes.size.toLong(), enviados = (total - pendientes.size).toLong(),
            chequeos = chequeos, observaciones = observaciones,
            puedeGenerar = puedeGenerar && disponible(f) && pendientes.isNotEmpty()
        )
    }

    internal fun observaciones(registros: List<RegistroOperacionesEntity>, cat: Catalogos): List<ObservacionResponse> {
        val paises = catalogoRepository.paises()
        val idsPaises = paises.map { it.id.toString() }.toSet()
        val nombresPaises = paises.map { it.nombre.trim().lowercase() }.toSet()
        return registros.flatMap { r ->
            val o = r.operacion
            val faltas = mutableListOf<String>()
            if (o.origenFondos.isNullOrBlank()) faltas.add("Falta el origen de los fondos")
            if (o.numeroComprobante.isNullOrBlank()) faltas.add("Falta el N.º de comprobante")
            if (r.beneficiarios.isEmpty()) faltas.add(FALTA_BENEFICIARIO)
            if (r.personaFisica.numeroDocumento.isNullOrBlank() && r.personaFisica.motivoSinDocumento.isNullOrBlank()) faltas.add(FALTA_DOCUMENTO)
            val moneda = cat.moneda(o.moneda)?.codigo
            if (moneda == null) faltas.add(MONEDA_INVALIDA)
            else if (moneda != "USD" && o.montoUsd == null) {
                faltas.add("Falta el tipo de cambio de ${UmbralService.nombreMes(YearMonth.from(o.fechaOperacion).minusMonths(1))}")
            }
            val nac = r.personaFisica.nacionalidad?.trim()?.takeIf { it.isNotEmpty() }
            if (nac != null && nac !in idsPaises && nac.lowercase() !in nombresPaises) faltas.add(PAIS_INVALIDO)
            faltas.map { ObservacionResponse(requireNotNull(r.id), r.numeroRegistro.orEmpty(), it) }
        }
    }

    @Transactional
    fun generar(empresa: String, request: ExportacionRequest, autor: AutorEvento?): ExportacionResponse {
        val mes = periodo(request.periodo)
        val cat = catalogos.cargar()
        val f = formato(request.formato, cat)
        if (!disponible(f)) {
            throw CodigosOperaciones.conflicto(CodigosOperaciones.FORMATO_PRONTO, "Este formato todavía no está disponible.")
        }
        val (_, pendientes) = delPeriodo(empresa, mes)
        if (pendientes.isEmpty()) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.SIN_REGISTROS, "No hay registros pendientes de envío en ese periodo.")
        }
        val ahora = LocalDateTime.now(ZONA_LIMA).withNano(0)
        val filas = filas(pendientes, cat, configuracion.de(empresa, cat).sectorNombre)
        val base = "RO_${mes}_disposicion_UIF"
        val xlsx = ExportadorRo.xlsx(filas, ExportadorRo.leeme(mes, ahora, autor?.nombreCompleto, pendientes.size))
        val csv = ExportadorRo.csv(filas)
        val carpeta = "operaciones/exportaciones/${UUID.randomUUID()}"
        val rutaXlsx = almacenamiento.guardar("$carpeta/$base.xlsx", xlsx,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").rutaArchivo
        val rutaCsv = try {
            almacenamiento.guardar("$carpeta/$base.csv", csv, "text/csv").rutaArchivo
        } catch (e: Exception) {
            runCatching { almacenamiento.eliminar(rutaXlsx) }
            throw e
        }
        val exportacion = exportaciones.save(
            ExportacionOperacionesEntity(
                empresa = claveEmpresa(empresa), periodo = mes.toString(), formato = f.codigo, generadoEn = ahora,
                generadoPor = autor?.nombreCompleto, rutaXlsx = rutaXlsx, rutaCsv = rutaCsv, nombreBase = base,
                registros = pendientes.mapNotNull { it.id }
            )
        )
        return respuesta(exportacion, cat, true)
    }

    @Transactional(readOnly = true)
    fun listar(empresa: String, page: Int, size: Int, puedeRegistrar: Boolean): PaginaExportacionesResponse {
        if (page < 0 || size < 1 || size > 100) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.PAGINA_INVALIDA, "page debe ser 0 o más y size entre 1 y 100")
        }
        val cat = catalogos.cargar()
        val p = exportaciones.findAllByEmpresaOrderByGeneradoEnDescIdDesc(claveEmpresa(empresa), PageRequest.of(page, size))
        return PaginaExportacionesResponse(p.content.map { respuesta(it, cat, puedeRegistrar) }, p.totalElements, p.totalPages, page, size)
    }

    /** La exportacion, solo si es de la empresa; si no, el mismo 404 que si no existiera. */
    private fun propia(empresa: String, id: Long): ExportacionOperacionesEntity =
        exportaciones.findById(id).orElse(null)?.takeIf { it.empresa == claveEmpresa(empresa) }
            ?: throw ResourceNotFoundException("No existe la exportación $id", CodigosOperaciones.EXPORTACION_NO_ENCONTRADA)

    @Transactional(readOnly = true)
    fun ver(empresa: String, id: Long, puedeRegistrar: Boolean) = respuesta(propia(empresa, id), catalogos.cargar(), puedeRegistrar)

    @Transactional(readOnly = true)
    fun archivo(empresa: String, id: Long, tipo: String?): ArchivoOperacion {
        val e = propia(empresa, id)
        val esCsv = tipo?.trim().equals("csv", ignoreCase = true)
        if (!esCsv && !tipo.isNullOrBlank() && !tipo.trim().equals("xlsx", ignoreCase = true)) {
            throw CodigosOperaciones.invalido("tipo", "El tipo debe ser xlsx o csv.")
        }
        val ruta = (if (esCsv) e.rutaCsv else e.rutaXlsx)
            ?: throw ResourceNotFoundException("La exportación no tiene ese archivo", CodigosOperaciones.ADJUNTO_NO_ENCONTRADO)
        val archivo = almacenamiento.obtener(ruta)
        val nombre = "${e.nombreBase ?: "RO_${e.periodo}"}.${if (esCsv) "csv" else "xlsx"}"
        val tipoContenido = if (esCsv) "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        return ArchivoOperacion(nombre, tipoContenido, archivo.contenido)
    }

    @Transactional
    fun constancia(empresa: String, id: Long, fechaEnvio: LocalDate?, numeroAcuse: String?, notas: String?,
                   archivo: MultipartFile?, autor: AutorEvento?): ConstanciaRegistradaResponse {
        val e = propia(empresa, id)
        if (e.fechaEnvio != null) {
            throw CodigosOperaciones.conflicto(CodigosOperaciones.CONSTANCIA_YA_REGISTRADA, "La constancia de esta exportación ya está registrada.")
        }
        val errores = linkedMapOf<String, String>()
        val hoy = LocalDate.now(ZONA_LIMA)
        when {
            fechaEnvio == null -> errores["fechaEnvio"] = "Indica la fecha de envío."
            fechaEnvio.isAfter(hoy) -> errores["fechaEnvio"] = "La fecha de envío no puede ser futura."
        }
        val acuse = numeroAcuse?.trim()?.takeIf { it.isNotEmpty() }
        when {
            acuse == null -> errores["numeroAcuse"] = "Escribe el N.º de constancia o acuse."
            acuse.length > 100 -> errores["numeroAcuse"] = "Máximo 100 caracteres."
        }
        val nota = notas?.trim()?.takeIf { it.isNotEmpty() }
        if (nota != null && nota.length > 2000) errores["notas"] = "Máximo 2000 caracteres."
        if (errores.isNotEmpty()) {
            throw OperacionesException(HttpStatus.BAD_REQUEST, CodigosOperaciones.DATOS_INVALIDOS, errores.values.first(), errores.toMap())
        }
        val ahora = LocalDateTime.now(ZONA_LIMA).withNano(0)
        var rutaGuardada: String? = null
        try {
            if (archivo != null && !archivo.isEmpty) {
                val doc = validadorDocumento.validar(archivo)
                rutaGuardada = almacenamiento.guardar("operaciones/constancias/${UUID.randomUUID()}-${doc.nombreArchivo}",
                    doc.contenido, doc.tipoContenido).rutaArchivo
                e.constanciaNombre = doc.nombreOriginal
                e.constanciaRuta = rutaGuardada
                e.constanciaTipo = doc.tipoContenido
            }
            e.fechaEnvio = fechaEnvio
            e.numeroAcuse = acuse
            e.notas = nota
            e.constanciaRegistradaEn = ahora
            e.constanciaRegistradaPor = autor?.nombreCompleto
            exportaciones.save(e)
            var enviados = 0
            operaciones.findAllById(e.registros).forEach { r ->
                if (r.fechaEnvio == null) {
                    r.fechaEnvio = fechaEnvio
                    r.idExportacionEnvio = e.id
                    enviados++
                    historial.anotar(requireNotNull(r.id), EventoOperacionEntity.ENVIO, "Envío a la UIF registrado",
                        "Constancia N.º $acuse del ${FotoOperacion.fecha(fechaEnvio)} (archivo del periodo ${e.periodo}).", autor, ahora)
                }
            }
            operaciones.flush()
            return ConstanciaRegistradaResponse(requireNotNull(e.id), enviados, constanciaDe(e)!!)
        } catch (ex: Exception) {
            rutaGuardada?.let { ruta -> runCatching { almacenamiento.eliminar(ruta) } }
            throw ex
        }
    }

    @Transactional(readOnly = true)
    fun archivoConstancia(empresa: String, id: Long): ArchivoOperacion {
        val e = propia(empresa, id)
        val ruta = e.constanciaRuta
            ?: throw ResourceNotFoundException("La constancia no tiene archivo", CodigosOperaciones.ADJUNTO_NO_ENCONTRADO)
        val a = almacenamiento.obtener(ruta)
        return ArchivoOperacion(e.constanciaNombre ?: "constancia", e.constanciaTipo ?: a.tipoContenido, a.contenido)
    }

    private fun constanciaDe(e: ExportacionOperacionesEntity): ConstanciaResponse? = e.fechaEnvio?.let {
        ConstanciaResponse(it, e.numeroAcuse, e.notas, e.constanciaNombre, e.constanciaRegistradaEn, e.constanciaRegistradaPor)
    }

    private fun respuesta(e: ExportacionOperacionesEntity, cat: Catalogos, puedeRegistrar: Boolean) = ExportacionResponse(
        id = requireNotNull(e.id), periodo = e.periodo, formato = e.formato,
        formatoNombre = cat.opcion(CatalogosOperacion.FORMATO_EXPORTACION, e.formato)?.nombre,
        registros = e.registros.size, generadoEn = e.generadoEn, generadoPor = e.generadoPor,
        archivoXlsx = e.rutaXlsx?.let { "${e.nombreBase}.xlsx" }, archivoCsv = e.rutaCsv?.let { "${e.nombreBase}.csv" },
        estado = if (e.fechaEnvio != null) ESTADO_ENVIADO else "GENERADO",
        constancia = constanciaDe(e),
        puedeRegistrarConstancia = puedeRegistrar && e.fechaEnvio == null
    )

    /** Filas del archivo (mismo orden que ExportadorRo.COLUMNAS). */
    internal fun filas(registros: List<RegistroOperacionesEntity>, cat: Catalogos, sectorEmpresa: String?): List<List<String>> {
        val responsables = registros.mapNotNull { it.idUsuarioResponsable }.distinct()
            .let { ids -> usuarios.findAllById(ids).associate { requireNotNull(it.id) to nombreCompleto(it) } }
        val analisisPorRegistro = registros.mapNotNull { r -> r.id?.let { id -> analisis.findByRegistroId(id)?.let { id to it } } }.toMap()
        return registros.map { r -> ExportadorRo.fila(r, cat, sectorEmpresa, responsables, analisisPorRegistro[r.id]) }
    }

    companion object {
        const val FALTA_BENEFICIARIO = "Falta el beneficiario"
        const val FALTA_DOCUMENTO = "Falta el documento de la persona que realiza"
        const val MONEDA_INVALIDA = "La moneda no tiene código ISO válido"
        const val PAIS_INVALIDO = "La nacionalidad no está en el catálogo de países"
    }
}

/** Arma el archivo «RO a disposición de la UIF» (formato interno de PLAFT). */
object ExportadorRo {

    val COLUMNAS = listOf(
        "N.º de registro", "Fecha de registro", "Fecha de la operación", "Oficina", "Sector", "Tipo de operación", "Tipo de bien",
        "Lugar", "Descripción", "Monto", "Moneda (ISO 4217)", "Tipo de cambio aplicado (S/ por unidad)", "Mes del tipo de cambio",
        "Monto en US$", "Umbral aplicado (US$)", "Resultado del umbral", "Modalidad de pago", "Cuenta involucrada",
        "N.º de comprobante", "Origen de los fondos",
        "Realiza: tipo de documento", "Realiza: N.º de documento", "Realiza: nombres", "Realiza: apellidos", "Realiza: nacionalidad",
        "Realiza: ocupación", "Realiza: estado civil", "Realiza: domicilio", "Realiza: departamento", "Realiza: provincia",
        "Realiza: distrito", "Realiza: teléfono",
        "Representada: tipo de persona", "Representada: tipo de documento", "Representada: N.º de documento",
        "Representada: nombre o razón social", "Representada: representante legal", "Representada: documento del representante",
        "Representada: accionistas con más del 25 %", "Representada: domicilio fiscal",
        "Beneficiarios", "Responsable", "Calificación de inusualidad", "N.º de ROS"
    )

    private fun dec(v: java.math.BigDecimal?) = v?.toPlainString().orEmpty()

    fun fila(
        r: RegistroOperacionesEntity, cat: Catalogos, sectorEmpresa: String?, responsables: Map<Int, String>, a: AnalisisOperacionEntity?
    ): List<String> {
        val o = r.operacion
        val pf = r.personaFisica
        val pr = r.personaRepresentada
        val moneda = cat.moneda(o.moneda)
        val tc = when {
            o.tcMes == null -> ""
            moneda?.codigo == "PEN" -> dec(o.tcUsd)
            else -> dec(o.tcMoneda)
        }
        val tipoBien = o.tipoBien.orEmpty()
        fun t(v: String?) = v?.trim().orEmpty()
        return listOf(
            t(r.numeroRegistro), r.fechaRegistro.toString(), o.fechaOperacion.toString(), t(r.oficina),
            t(o.sector?.let { cat.nombre(CatalogosOperacion.SECTOR, it) } ?: sectorEmpresa),
            t(cat.nombre(CatalogosOperacion.TIPO_OPERACION, o.tipoOperacion)), tipoBien,
            t(o.lugar), t(o.descripcion), dec(o.monto), t(moneda?.codigo ?: o.moneda), tc, t(o.tcMes),
            dec(o.montoUsd), dec(o.umbralUsd), t(o.umbralEstado),
            t(listOfNotNull(cat.nombre(CatalogosOperacion.MODALIDAD_PAGO, o.modalidadPago), o.modalidadPagoTexto).joinToString(" · ")),
            t(o.cuenta1), t(o.numeroComprobante),
            t(listOfNotNull(cat.nombre(CatalogosOperacion.ORIGEN_FONDOS, o.origenFondos), o.origenFondosTexto).joinToString(" · ")),
            t(cat.nombreDocumento(pf.tipoDocumento)), t(pf.numeroDocumento ?: pf.motivoSinDocumento), t(pf.nombres), t(pf.apellidos),
            t(cat.resolverTabla(CatalogoExportacion.PAIS, pf.nacionalidad)),
            t(cat.resolverTabla(CatalogoExportacion.OCUPACION, pf.profesionOcupacion)),
            t(cat.nombre(CatalogosOperacion.ESTADO_CIVIL, pf.estadoCivil)), t(pf.domicilio),
            t(cat.resolverTabla(CatalogoExportacion.DEPARTAMENTO, pf.departamento)),
            t(cat.resolverTabla(CatalogoExportacion.PROVINCIA, pf.provincia)), t(pf.distrito), t(pf.telefono),
            t(pr?.let { if (it.tipoPersona == CatalogosOperacion.JURIDICA) "Jurídica" else "Natural" }),
            t(pr?.let { cat.nombreDocumento(it.tipoDocumento) }), t(pr?.numeroDocumento),
            t(pr?.let { it.razonSocial ?: listOfNotNull(it.nombres, it.apellidos).joinToString(" ") }),
            t(pr?.representanteLegal),
            t(pr?.let { listOfNotNull(cat.nombreDocumento(it.representanteTipoDocumento), it.representanteNumeroDocumento).joinToString(" ") }),
            t(pr?.accionistasMayores25), t(pr?.domicilioFiscal),
            r.beneficiarios.joinToString(" | ") { "${it.nombre} (${listOfNotNull(cat.nombreDocumento(it.tipoDocumento), it.numeroDocumento.takeIf { n -> n.isNotBlank() }).joinToString(" ")})" },
            t(r.idUsuarioResponsable?.let { responsables[it] }),
            t(a?.estado), t(a?.numeroRos)
        )
    }

    fun leeme(mes: YearMonth, generado: LocalDateTime, por: String?, registros: Int): List<String> = listOf(
        "RO a disposición de la UIF — formato interno de PLAFT",
        "No es una plantilla oficial de la SBS: la SBS no publica una estructura del RO para este sector.",
        "Periodo: $mes (mes de la fecha de la operación). Registros: $registros (los pendientes de envío al generar).",
        "Generado el ${FotoOperacion.fecha(generado.toLocalDate())} ${"%02d:%02d".format(generado.hour, generado.minute)}" +
            (por?.let { " por $it" } ?: "") + ".",
        "Fuente de cada columna: los datos del registro tal como se guardaron en PLAFT.",
        "Tipo de cambio: el valor cargado en PLAFT por la empresa o por Sulotec para el mes anterior a la operación " +
            "(tipo de cambio que establece el Reglamento de la Ley UIF, art. 24.4 b). Vacío = no aplica (USD) o no estaba cargado.",
        "Monto en US$, umbral y resultado: la verificación guardada con el registro (vacía en registros anteriores a ella)."
    )

    fun xlsx(filas: List<List<String>>, leeme: List<String>): ByteArray = XSSFWorkbook().use { libro ->
        val negrita = libro.createCellStyle().apply { setFont(libro.createFont().apply { bold = true }) }
        val hoja = libro.createSheet("RO")
        hoja.createRow(0).let { fila ->
            COLUMNAS.forEachIndexed { i, c -> fila.createCell(i).apply { setCellValue(c); setCellStyle(negrita) } }
        }
        filas.forEachIndexed { i, valores ->
            val fila = hoja.createRow(i + 1)
            valores.forEachIndexed { j, v -> fila.createCell(j).setCellValue(v) }
        }
        val notas = libro.createSheet("Léeme")
        leeme.forEachIndexed { i, l -> notas.createRow(i).createCell(0).setCellValue(l) }
        ByteArrayOutputStream().use { salida -> libro.write(salida); salida.toByteArray() }
    }

    /** CSV UTF-8 con BOM y separador «;» (lo abre bien Excel en español). */
    fun csv(filas: List<List<String>>): ByteArray {
        fun celda(v: String): String =
            if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        val texto = buildString {
            append('﻿')
            append(COLUMNAS.joinToString(";") { celda(it) }).append("\r\n")
            filas.forEach { append(it.joinToString(";") { v -> celda(v) }).append("\r\n") }
        }
        return texto.toByteArray(Charsets.UTF_8)
    }
}
