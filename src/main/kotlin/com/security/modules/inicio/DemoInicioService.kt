package com.security.modules.inicio

import com.security.modules.denuncias.application.service.PlazoDenunciasService
import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.ResultadoCierreDenuncia
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import com.security.modules.denuncias.infrastructure.persistence.repository.DenunciaRepository
import com.security.modules.listas.application.service.ReglasListasService
import com.security.modules.listas.domain.ConsultaLista
import com.security.modules.listas.domain.ConsultaListaDecision
import com.security.modules.listas.domain.reglas.AparicionConsulta
import com.security.modules.listas.domain.reglas.CalculoListas
import com.security.modules.listas.domain.reglas.FilaDef
import com.security.modules.listas.domain.reglas.ListaDisponible
import com.security.modules.listas.domain.reglas.RegistroLista
import com.security.modules.listas.domain.reglas.ResultadoListas
import com.security.modules.listas.infrastructure.ConsultaListaDecisionRepository
import com.security.modules.listas.infrastructure.ConsultaListaRepository
import com.security.modules.oficial.dominio.TextosOc
import com.security.modules.oficial.persistencia.OcDesignacion
import com.security.modules.oficial.persistencia.OcDesignacionRepository
import com.security.modules.operaciones.domain.AnalisisOperacionEntity
import com.security.modules.operaciones.domain.BeneficiarioEntity
import com.security.modules.operaciones.domain.Operacion
import com.security.modules.operaciones.domain.Persona
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.infraestructure.AnalisisOperacionRepository
import com.security.modules.operaciones.infraestructure.OperacionRepository
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.security.ROL_ADMINISTRADOR
import com.security.shared.security.limpiarEmpresa
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

/**
 * Datos de demostracion del tablero de Inicio (POST/DELETE /api/inicio/demo), solo para el administrador universal y
 * siempre en la empresa de su cuenta. Todo lo creado lleva una marca que ningun dato real puede tener:
 *  - consultas de listas: criterio_tipo_documento = 'DEMO' (los reales solo aceptan los tipos del catalogo);
 *  - registros de operaciones: numero_registro 'RO-DEMO-NNNNNN' (los reales son RO-AAAA-NNNNNN y no mueven el contador);
 *  - denuncias: codigo_seguimiento 'DEMO-<empresa>-NNN' (los reales son DEN-…);
 *  - oficial de cumplimiento: designacion guardada por '__demo_inicio__' (solo si la empresa no tiene un titular activo).
 * Quitar borra SOLO lo que lleva la marca y es de esa empresa. Las cifras reproducen el ejemplo del contrato.
 */
@Service
class DemoInicioService(
    private val usuarios: UsuarioRepository,
    private val empresas: EmpresaRepository,
    private val consultas: ConsultaListaRepository,
    private val decisiones: ConsultaListaDecisionRepository,
    private val reglasListas: ReglasListasService,
    private val operaciones: OperacionRepository,
    private val analisis: AnalisisOperacionRepository,
    private val denuncias: DenunciaRepository,
    private val designaciones: OcDesignacionRepository,
    private val reloj: RelojInicio,
    private val plazoDenuncias: PlazoDenunciasService,
    private val em: EntityManager
) {
    private val json = jacksonObjectMapper()

    companion object {
        const val MARCA_CONSULTA = "DEMO"
        const val PREFIJO_RO = "RO-DEMO-"
        const val PREFIJO_DENUNCIA = "DEMO-"
        const val MARCA_OFICIAL = "__demo_inicio__"

        private val NOMBRES = listOf("Lucía", "Mateo", "Valeria", "Diego", "Camila", "Joaquín", "Renata", "Bruno", "Ximena", "Thiago", "Antonella", "Gael")
        private val APELLIDOS = listOf("Ficticio", "Ejemplar", "Prueba", "Muestra", "Modelo", "Ensayo", "Simulado", "Demostrado", "Patrón", "Boceto", "Borrador", "Maqueta")

        /** Nombre inventado (combinacion de listas de ejemplo): nunca una persona real. */
        fun nombreFicticio(i: Int): Triple<String, String, String> =
            Triple(NOMBRES[i % NOMBRES.size], APELLIDOS[(i / NOMBRES.size) % APELLIDOS.size], APELLIDOS[(i * 7 + 3) % APELLIDOS.size])
    }

    // ================================================================== estado

    @Transactional(readOnly = true)
    fun cargado(empresa: String): Boolean = conteos(empresa).values.any { it > 0 }

    private fun conteos(empresa: String): Map<String, Int> = mapOf(
        "consultas" to idsConsultas(empresa).size,
        "registros" to idsRegistros(empresa).size,
        "denuncias" to idsDenuncias(empresa).size,
        "oficial" to idsDesignaciones(empresa).size
    )

    // ================================================================== cargar

    @Transactional
    fun cargar(username: String): DemoCargaResponse {
        val (admin, empresa) = administrador(username)
        if (cargado(empresa)) return DemoCargaResponse(cargado = true, yaEstaba = true, creados = emptyMap())
        val creados = linkedMapOf(
            "consultas" to crearConsultas(admin, empresa),
            "registros" to crearRegistros(admin, empresa),
            "denuncias" to crearDenuncias(admin, empresa),
            "oficial" to crearOficial(empresa)
        )
        return DemoCargaResponse(cargado = true, yaEstaba = false, creados = creados)
    }

    // ================================================================== quitar

    @Transactional
    fun quitar(username: String): DemoBorradoResponse {
        val (_, empresa) = administrador(username)
        val consultasIds = idsConsultas(empresa)
        val registrosIds = idsRegistros(empresa)
        val denunciasIds = idsDenuncias(empresa)
        val designacionesIds = idsDesignaciones(empresa)

        if (consultasIds.isNotEmpty()) {
            nativo("DELETE FROM consultas_listas_decision WHERE id_consulta IN (:ids)", consultasIds)
            nativo("DELETE FROM consultas_listas WHERE id IN (:ids)", consultasIds)
        }
        if (registrosIds.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            val filas = em.createNativeQuery(
                "SELECT id_operacion, id_persona_fisica, id_persona_representada FROM registro_operaciones_v2 WHERE id IN (:ids)"
            ).setParameter("ids", registrosIds).resultList as List<Array<*>>
            val operacionesIds = filas.mapNotNull { (it[0] as Number?)?.toLong() }
            val personasIds = filas.flatMap { listOf(it[1], it[2]) }.mapNotNull { (it as Number?)?.toLong() }
            // Lo que alguien haya hecho sobre un registro de demostracion (analisis, historial, exportacion) tambien se va.
            nativo("DELETE FROM historial_operaciones_cambios WHERE id_evento IN (SELECT id FROM historial_operaciones WHERE registro_id IN (:ids))", registrosIds)
            nativo("DELETE FROM historial_operaciones WHERE registro_id IN (:ids)", registrosIds)
            nativo("DELETE FROM analisis_operaciones_adjuntos WHERE registro_id IN (:ids)", registrosIds)
            nativo("DELETE FROM analisis_operaciones WHERE registro_id IN (:ids)", registrosIds)
            nativo("DELETE FROM exportacion_operaciones_registros WHERE registro_id IN (:ids)", registrosIds)
            nativo("DELETE FROM beneficiarios_operacion_v2 WHERE registro_id IN (:ids)", registrosIds)
            nativo("DELETE FROM registro_operaciones_v2 WHERE id IN (:ids)", registrosIds)
            if (operacionesIds.isNotEmpty()) nativo("DELETE FROM operaciones_v2 WHERE id IN (:ids)", operacionesIds)
            if (personasIds.isNotEmpty()) nativo("DELETE FROM personas_operacion WHERE id IN (:ids)", personasIds)
        }
        if (denunciasIds.isNotEmpty()) {
            nativo("DELETE FROM denuncia_historial WHERE denuncia_id IN (:ids)", denunciasIds)
            nativo("DELETE FROM denuncia_involucrados WHERE denuncia_id IN (:ids)", denunciasIds)
            nativo("DELETE FROM denuncia_archivos WHERE denuncia_id IN (:ids)", denunciasIds)
            nativo("DELETE FROM denuncias WHERE id IN (:ids)", denunciasIds)
        }
        if (designacionesIds.isNotEmpty()) {
            nativo("DELETE FROM oc_evento_cambio WHERE evento_id IN (SELECT id FROM oc_evento WHERE designacion_id IN (:ids))", designacionesIds)
            nativo("DELETE FROM oc_evento WHERE designacion_id IN (:ids)", designacionesIds)
            nativo("DELETE FROM oc_pendiente WHERE designacion_id IN (:ids)", designacionesIds)
            nativo("DELETE FROM oc_remocion WHERE designacion_id IN (:ids)", designacionesIds)
            nativo("DELETE FROM oc_designacion WHERE id IN (:ids)", designacionesIds)
        }
        em.clear()
        return DemoBorradoResponse(
            cargado = false,
            eliminados = linkedMapOf(
                "consultas" to consultasIds.size, "registros" to registrosIds.size,
                "denuncias" to denunciasIds.size, "oficial" to designacionesIds.size
            )
        )
    }

    // ================================================================== marcas (siempre dentro de la empresa)

    private fun idsConsultas(empresa: String): List<Long> =
        em.createQuery(
            "SELECT c.id FROM ConsultaLista c WHERE c.criterioTipoDocumento = :marca AND LOWER(TRIM(c.empresa)) = LOWER(TRIM(:empresa))",
            Long::class.javaObjectType
        ).setParameter("marca", MARCA_CONSULTA).setParameter("empresa", empresa).resultList

    private fun idsRegistros(empresa: String): List<Long> =
        em.createQuery(
            "SELECT r.id FROM RegistroOperacionesEntity r WHERE r.numeroRegistro LIKE :prefijo AND LOWER(TRIM(r.empresa)) = LOWER(TRIM(:empresa))",
            Long::class.javaObjectType
        ).setParameter("prefijo", "$PREFIJO_RO%").setParameter("empresa", empresa).resultList

    private fun idsDenuncias(empresa: String): List<Int> =
        em.createQuery(
            """
            SELECT d.id FROM DenunciaEntity d LEFT JOIN d.receptorUsuario r LEFT JOIN d.creadoPorUsuario c
            WHERE d.codigoSeguimiento LIKE :prefijo AND LOWER(TRIM(COALESCE(r.empresa, c.empresa))) = LOWER(TRIM(:empresa))
            """,
            Int::class.javaObjectType
        ).setParameter("prefijo", prefijoDenuncia(empresa) + "%").setParameter("empresa", empresa).resultList

    private fun idsDesignaciones(empresa: String): List<Long> {
        val e = empresas.findByNombreIgnoreCase(empresa)?.id ?: return emptyList()
        return em.createQuery(
            "SELECT d.id FROM OcDesignacion d WHERE d.guardadaPorUsuario = :marca AND d.empresaId = :empresa",
            Long::class.javaObjectType
        ).setParameter("marca", MARCA_OFICIAL).setParameter("empresa", e).resultList
    }

    private fun prefijoDenuncia(empresa: String) =
        PREFIJO_DENUNCIA + Integer.toHexString(empresa.trim().lowercase().hashCode()).padStart(8, '0') + "-"

    private fun nativo(sql: String, ids: Collection<Number>) {
        em.createNativeQuery(sql).setParameter("ids", ids).executeUpdate()
    }

    private fun administrador(username: String): Pair<Usuario, String> {
        val u = usuarios.findByUsuario(username)
            ?: throw InicioException(HttpStatus.FORBIDDEN, "DEMO_SIN_PERMISO", "Usuario no disponible")
        val esUniversal = u.roles.any { it.rol.activo && it.rol.codigo.trim().equals(ROL_ADMINISTRADOR, ignoreCase = true) }
        if (!esUniversal || !u.activo) {
            throw InicioException(HttpStatus.FORBIDDEN, "DEMO_SIN_PERMISO", "Solo el administrador de Sulotec puede cargar o quitar los datos de demostración.")
        }
        val empresa = limpiarEmpresa(u.empresa)
            ?: throw InicioException(HttpStatus.BAD_REQUEST, "DEMO_SIN_EMPRESA", "Tu cuenta no tiene una empresa: los datos de demostración van a la empresa de tu cuenta.")
        return u to empresa
    }

    // ================================================================== fechas

    /** Momento i de n repartido entre el dia 1 del mes en curso y hoy, nunca en el futuro ni despues de `tope`. */
    private fun enMesActual(i: Int, n: Int, tope: LocalDateTime = reloj.ahora().minusMinutes(10)): LocalDateTime {
        val hoy = reloj.hoy()
        val dia = hoy.withDayOfMonth(1).plusDays((i.toLong() * hoy.dayOfMonth) / n)
        val momento = dia.atTime(8 + i % 10, (i * 7) % 60)
        return maxOf(dia.atStartOfDay(), minOf(momento, tope))
    }

    /** Momento i de n repartido en el mes anterior. */
    private fun enMesAnterior(i: Int, n: Int): LocalDateTime {
        val mes = YearMonth.from(reloj.hoy()).minusMonths(1)
        return mes.atDay(1).plusDays((i.toLong() * mes.lengthOfMonth()) / n).atTime(8 + i % 10, (i * 7) % 60)
    }

    private fun limitar(momento: LocalDateTime, desde: LocalDateTime): LocalDateTime =
        maxOf(desde, minOf(momento, reloj.ahora().minusMinutes(1)))

    // ================================================================== Listas negativas

    private enum class Tipo { CRITICA, ALTA, MEDIA, SIN }

    private data class PlanConsulta(val fila: String?, val tipo: Tipo, val homonimo: Boolean)

    private fun planMes(criticas: List<String>, pep: Int, asbancInv: Int, homonimoAlta: Int, noticias: Int, asbancNot: Int, sin: Int) =
        criticas.map { PlanConsulta(it, Tipo.CRITICA, true) } +
            List(pep) { PlanConsulta("PEP_PERU", Tipo.ALTA, false) } +
            List(asbancInv) { PlanConsulta("ASBANC_INVESTIGADOS", Tipo.ALTA, it < homonimoAlta) } +
            List(noticias) { PlanConsulta("NOTICIAS_MEDIOS", Tipo.MEDIA, false) } +
            List(asbancNot) { PlanConsulta("ASBANC_NOTICIAS", Tipo.MEDIA, false) } +
            List(sin) { PlanConsulta(null, Tipo.SIN, false) }

    // Intercala los tipos para que las series diarias no salgan en bloques.
    private fun intercalar(planes: List<PlanConsulta>): List<PlanConsulta> =
        planes.withIndex().sortedBy { (i, _) -> (i * 7919L) % planes.size }.map { it.value }

    private fun crearConsultas(admin: Usuario, empresa: String): Int {
        val reglas = reglasListas.reglas()
        val porCodigo = reglas.filas.associateBy { it.codigo }
        // Todas las filas consultables cuentan como disponibles: el resultado no depende de que listas esten cargadas hoy.
        val disponibles = reglasListas.listasDisponibles() +
            reglas.filas.flatMap { it.listas }.distinct().map { ListaDisponible(null, it, false, null) } +
            ListaDisponible(null, "PEP (demostración)", true, null)
        val resultados = mutableMapOf<String?, ResultadoListas>()
        fun resultado(fila: String?): ResultadoListas = resultados.getOrPut(fila) {
            val f: FilaDef? = fila?.let { porCodigo[it] }
            val apariciones = if (f == null) emptyList() else listOf(aparicion(f))
            CalculoListas.calcular(reglas, disponibles, apariciones)
        }
        val uuid = empresas.findByNombreIgnoreCase(empresa)?.uuid

        // Mes en curso: 312 consultas; 72 con coincidencia (12 criticas, 25 altas, 35 medias); 19 PEP; 11 homonimos.
        // Las 2 criticas sin decidir van aparte (ayer y hoy). Mes anterior: 297 consultas (todas sus criticas decididas).
        val actual = intercalar(planMes(List(5) { "OFAC" } + List(3) { "ONU_1267" } + List(2) { "UNION_EUROPEA" }, 19, 6, 1, 29, 6, 240))
        val anterior = intercalar(planMes(List(5) { "OFAC" } + List(4) { "ONU_1267" } + List(2) { "UNION_EUROPEA" }, 16, 6, 0, 26, 6, 232))

        val ahora = reloj.ahora()
        val guardadas = mutableListOf<ConsultaLista>()
        actual.forEachIndexed { i, p -> guardadas += consulta(admin, empresa, uuid, enMesActual(i, actual.size), p, resultado(p.fila), i) }
        anterior.forEachIndexed { i, p -> guardadas += consulta(admin, empresa, uuid, enMesAnterior(i, anterior.size), p, resultado(p.fila), 1000 + i) }
        // Criticas sin decidir: la mas antigua de ayer, la otra de hoy.
        guardadas += consulta(admin, empresa, uuid, ahora.minusDays(1), PlanConsulta("OFAC", Tipo.CRITICA, false), resultado("OFAC"), 2000)
        guardadas += consulta(admin, empresa, uuid, limitar(ahora.minusMinutes(30), reloj.hoy().atStartOfDay()),
            PlanConsulta("ONU_1267", Tipo.CRITICA, false), resultado("ONU_1267"), 2001)

        val creadas = consultas.saveAll(guardadas)
        decisiones.saveAll(creadas.filter { it.homonimo }.map {
            ConsultaListaDecision(idConsulta = it.id!!, accion = ConsultaListaDecision.ACCION_HOMONIMO,
                comentario = it.homonimoComentario, usuario = admin, fecha = it.homonimoFecha!!)
        })
        return creadas.size
    }

    private fun aparicion(f: FilaDef): AparicionConsulta {
        val lista = if (f.todasPep) "PEP (demostración)" else f.listas.firstOrNull() ?: f.nombre
        return AparicionConsulta(
            tipoListaId = null, nombreLista = lista, esPep = f.todasPep, ordenGrupo = null, nombreGrupo = null,
            registro = RegistroLista(
                lista = lista, esPep = f.todasPep, descripcion = "Registro de demostración (dato ficticio).",
                institucion = if (f.todasPep) "Entidad pública de ejemplo" else null,
                cargo = if (f.todasPep) "Cargo de ejemplo" else null,
                periodoDesde = if (f.todasPep) "2019" else null, periodoHasta = if (f.todasPep) "2022" else null
            )
        )
    }

    private fun consulta(
        admin: Usuario, empresa: String, uuid: String?, fecha: LocalDateTime, p: PlanConsulta, r: ResultadoListas, i: Int
    ): ConsultaLista {
        val (nombre, paterno, materno) = nombreFicticio(i)
        val completo = "$paterno $materno, $nombre"
        val homonimoFecha = if (p.homonimo) minOf(fecha.plusHours(1), reloj.ahora()) else null
        return ConsultaLista(
            usuario = admin, empresa = empresa, empresaUuid = uuid, fecha = fecha, estado = ConsultaLista.ESTADO_RESUELTA,
            tipoConsulta = "CLIENTE", criterioTipoDocumento = MARCA_CONSULTA, criterioNombres = nombre,
            criterioApellidoPaterno = paterno, criterioApellidoMaterno = materno,
            criterioTexto = "[DEMO] $nombre $paterno $materno", modo = ConsultaLista.MODO_NOMBRE, totalCandidatos = if (p.fila == null) 0 else 1,
            coincidencia = if (p.fila == null) ConsultaLista.COINCIDENCIA_SIN_RESULTADOS else ConsultaLista.COINCIDENCIA_NOMBRE,
            consultadoNombre = completo, tipoPersona = "NATURAL", consultadoPais = "Perú",
            porcentaje = r.porcentaje, clasificacion = r.clasificacion, listasConsultadas = r.listasConsultadas,
            listasConCoincidencia = r.listasConCoincidencia, registrosEnListas = r.registrosEnListas,
            noticiasEnMedios = r.noticiasEnMedios, esPep = r.esPep,
            listasCoincidenciaTexto = r.nombresConCoincidencia().joinToString(" | ").ifBlank { null },
            resultado = json.writeValueAsString(r),
            homonimo = p.homonimo,
            homonimoComentario = if (p.homonimo) "Demostración: la fecha de nacimiento y el documento no coinciden." else null,
            homonimoPor = if (p.homonimo) admin else null,
            homonimoFecha = homonimoFecha
        )
    }

    // ================================================================== Registro de operaciones

    private data class PlanRegistro(
        val tipo: String, val monto: BigDecimal, val moneda: String, val sobre: Boolean, val inusual: String? = null,
        val sinResponsable: Boolean = false, val diasAntes: Long = 0, val persona: Pair<String, String>? = null
    )

    private fun usd(v: Double) = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP)

    private fun crearRegistros(admin: Usuario, empresa: String): Int {
        // Mes en curso: 64 registros por unos US$ 2,4 M; 19 sobre el umbral, 3 inusuales, 58 el mismo dia (91 %).
        val actual = mutableListOf<PlanRegistro>()
        repeat(14) { k -> actual += PlanRegistro(if (k % 2 == 0) "COMPRA_LOTE" else "VENTA_LOTE", usd(35000.0 + 1000 * k), "USD", true) }
        actual += PlanRegistro("COMPRA_VIVIENDA", usd(150000.0), "USD", true, inusual = AnalisisOperacionEntity.EN_ANALISIS)
        actual += PlanRegistro("VENTA_LOCAL_COMERCIAL", usd(180000.0), "USD", true, inusual = AnalisisOperacionEntity.SOSPECHOSA)
        repeat(25) { k -> actual += PlanRegistro(if (k % 2 == 0) "VENTA_LOTE" else "COMPRA_LOTE", usd(18000.0 + 400 * k), "USD", false, diasAntes = if (k < 6) 1L + k % 3 else 0L) }
        val unidades = listOf("COMPRA_ESTACIONAMIENTO", "COMPRA_VIVIENDA", "VENTA_DEPARTAMENTO", "VENTA_ESTACIONAMIENTO")
        repeat(20) { k -> actual += PlanRegistro(unidades[k % unidades.size], usd(20000.0 + 1100 * k), "USD", false) }
        // Los tres ultimos (los que muestra la tarjeta), con los nombres del ejemplo del diseno (ficticios).
        actual += PlanRegistro("COMPRA_OFICINA", usd(520000.0), "PEN", true, inusual = AnalisisOperacionEntity.EN_ANALISIS, persona = "Salazar Cruz" to "Marco")
        actual += PlanRegistro("VENTA_LOTE", usd(42000.0), "USD", true, sinResponsable = true, persona = "Medina Paredes" to "Rosa")
        actual += PlanRegistro("VENTA_DEPARTAMENTO", usd(128500.0), "USD", true, sinResponsable = true, persona = "Rojas Silva" to "Pedro")

        val anterior = List(30) { k ->
            if (k % 3 == 0) PlanRegistro("COMPRA_VIVIENDA", usd(60000.0 + 1500 * k), "USD", false)
            else PlanRegistro("COMPRA_LOTE", usd(15000.0 + 300 * k), "USD", false)
        }

        val ahora = reloj.ahora()
        val inicioHoy = reloj.hoy().atStartOfDay()
        var correlativo = 0
        val momentos = anterior.indices.map { enMesAnterior(it, anterior.size) } +
            (0 until actual.size - 3).map { enMesActual(it, actual.size - 3, ahora.minusMinutes(10)) } +
            listOf(3L, 2L, 1L).map { limitar(ahora.minusMinutes(it), inicioHoy) }
        (anterior + actual).forEachIndexed { i, p ->
            correlativo++
            val creado = momentos[i]
            val (apellidos, nombres) = p.persona ?: nombreFicticio(i + 300).let { "${it.second} ${it.third}" to it.first }
            val lote = p.tipo.endsWith("_LOTE")
            val umbral = if (lote) BigDecimal("30000.00") else BigDecimal("100000.00")
            val tc = BigDecimal("3.750000")
            val montoUsd = if (p.moneda == "PEN") p.monto.divide(tc, 2, RoundingMode.HALF_UP) else p.monto
            val registro = operaciones.save(
                RegistroOperacionesEntity(
                    numeroRegistro = PREFIJO_RO + "%06d".format(correlativo),
                    idUsuario = admin.id!!, empresa = empresa, oficina = "Oficina principal",
                    fechaRegistro = creado.toLocalDate(), fechaCreacion = creado,
                    personaFisica = Persona().apply {
                        tipoDocumento = "DNI"; numeroDocumento = "00%06d".format(100000 + i); this.nombres = nombres
                        this.apellidos = apellidos; nacionalidad = "Peruana"; tipoPersona = "NATURAL"
                        domicilio = "Dirección de demostración"
                    },
                    personaRepresentada = null,
                    operacion = Operacion(
                        fechaOperacion = creado.toLocalDate().minusDays(p.diasAntes), lugar = "Lima",
                        modalidadPago = "TRANSFERENCIA_BANCARIA", moneda = p.moneda, monto = p.monto, tipoOperacion = p.tipo,
                        cuenta1 = null, cuenta2 = null, cuenta3 = null, origenFondos = "AHORROS",
                        descripcion = "Registro de demostración (datos ficticios).",
                        umbralEstado = if (p.sobre) "SI_SE_REGISTRA" else "NO_ALCANZA_UMBRAL",
                        umbralUsd = umbral, umbralNormaUsd = umbral, umbralInterno = false,
                        tipoBien = if (lote) "LOTE_TERRENO" else "UNIDAD_INMOBILIARIA", sector = "CONSTRUCCION_INMOBILIARIA",
                        montoUsd = montoUsd, tcMes = YearMonth.from(creado).minusMonths(1).toString(),
                        tcUsd = tc, tcMoneda = if (p.moneda == "PEN") BigDecimal("1.000000") else tc, umbralVerificadoEn = creado
                    ),
                    idUsuarioResponsable = if (p.sinResponsable) null else admin.id,
                    beneficiarios = listOf(
                        BeneficiarioEntity(nombre = "Inmobiliaria de Demostración S.A.C.", tipoDocumento = "RUC", numeroDocumento = "20000000001")
                    )
                )
            )
            p.inusual?.let { estado ->
                analisis.save(
                    AnalisisOperacionEntity(
                        registroId = registro.id!!, estado = estado, senal = "ORIGEN_INCONSISTENTE",
                        fuente = "ANALISTA", fechaDeteccion = creado.toLocalDate(),
                        descripcionSenal = "Señal de demostración (dato ficticio).",
                        decision = if (estado == AnalisisOperacionEntity.SOSPECHOSA) AnalisisOperacionEntity.SOSPECHOSA else null,
                        calificadaEn = if (estado == AnalisisOperacionEntity.SOSPECHOSA) creado else null,
                        calificadaPor = if (estado == AnalisisOperacionEntity.SOSPECHOSA) admin.usuario else null,
                        actualizadoEn = creado, actualizadoPor = admin.usuario
                    )
                )
            }
        }
        return correlativo
    }

    // ================================================================== Canal de denuncias

    private fun crearDenuncias(admin: Usuario, empresa: String): Int {
        val ahora = reloj.ahora()
        val hoy = reloj.hoy()
        val inicioMes = hoy.withDayOfMonth(1).atStartOfDay()
        val prefijo = prefijoDenuncia(empresa)
        // Situaciones del mes: 8/5/4/3/2/2 (como el diseno).
        val causas = List(8) { CausaDenuncia.CONFLICTO } + List(5) { CausaDenuncia.FRAUDE } + List(4) { CausaDenuncia.SOBORNO } +
            List(3) { CausaDenuncia.ACOSO } + List(2) { CausaDenuncia.OPERACION_SOSPECHOSA } + List(2) { CausaDenuncia.OTRAS }
        // Resultado de 15 de las cerradas del mes: 6 fundadas, 5 infundadas, 4 archivadas (40/33/27 %); el resto, sin dato.
        val resultados = List(6) { ResultadoCierreDenuncia.FUNDADA } + List(5) { ResultadoCierreDenuncia.INFUNDADA } +
            List(4) { ResultadoCierreDenuncia.ARCHIVADA }
        val relaciones = RelacionEmpresa.entries
        var n = 0
        val lista = mutableListOf<DenunciaEntity>()

        fun nueva(causa: CausaDenuncia, creada: LocalDateTime, estado: EstadoDenuncia, cerrada: LocalDateTime?): DenunciaEntity {
            n++
            return DenunciaEntity(
                causa = causa, relacionEmpresa = relaciones[n % relaciones.size],
                descripcion = "Denuncia de demostración (datos ficticios) para ver el tablero de Inicio.",
                codigoSeguimiento = prefijo + "%03d".format(n), receptorUsuario = admin, anonima = true, aceptaTerminos = true,
                estado = estado, creadoPorUsuario = admin, fechaCreacion = creada, fechaActualizacion = cerrada ?: creada,
                fechaCierre = cerrada
            )
        }

        // Mes en curso: 24 recibidas = 4 abiertas (2 sin abrir) + 20 cerradas el mismo dia; 2 derivadas al oficial.
        causas.forEachIndexed { i, causa ->
            val creada = enMesActual(i, causas.size, ahora.minusMinutes(5))
            val d = when (i) {
                0, 1 -> nueva(causa, creada, EstadoDenuncia.RECIBIDO, null)
                2, 3 -> nueva(causa, creada, EstadoDenuncia.EN_PROCESO, null)
                else -> nueva(causa, creada, EstadoDenuncia.CERRADO, limitar(creada.plusHours(2), creada)).also {
                    it.resultadoCierre = resultados.getOrNull(i - 4)
                    if (it.resultadoCierre == ResultadoCierreDenuncia.ARCHIVADA) it.motivoCierre = "Demostración: no hay elementos para continuar."
                }
            }
            if (i == 2 || i == 5) {
                d.derivadaAlOficial = true
                d.fechaDerivacion = limitar(creada.plusMinutes(30), creada)
                d.derivadaPorUsuario = admin
                d.motivoDerivacion = "Demostración: posible relación con una operación inusual."
            }
            lista += d
        }
        // Cerradas este mes pero recibidas antes: 3 a tiempo (5 a 7 dias) y 5 fuera de plazo; promedio de las 28: 9 dias.
        val cierre = limitar(inicioMes.plusHours(10), inicioMes)
        listOf(5L, 6L, 7L).forEach { lista += nueva(CausaDenuncia.CONFLICTO, cierre.minusDays(it), EstadoDenuncia.CERRADO, cierre) }
        listOf(46L, 47L, 47L, 47L, 47L).forEach { lista += nueva(CausaDenuncia.SOBORNO, cierre.minusDays(it), EstadoDenuncia.CERRADO, cierre) }
        // Una abierta con el plazo vencido.
        lista += nueva(CausaDenuncia.CONFLICTO, ahora.minusDays(plazoDenuncias.dias + 5), EstadoDenuncia.EN_PROCESO, null)
        denuncias.saveAll(lista)
        return lista.size
    }

    // ================================================================== Oficial de cumplimiento

    private fun crearOficial(empresa: String): Int {
        val e = empresas.findByNombreIgnoreCase(empresa) ?: return 0
        val empresaId = e.id ?: return 0
        // Nunca encima de un oficial real: si ya hay un titular activo, la demostracion no crea otro.
        if (designaciones.findFirstByEmpresaIdAndTipoCargoAndEstadoNotOrderByGuardadaEnDescIdDesc(empresaId, "TITULAR", "REMOVIDA") != null) return 0
        val guardada = LocalDate.of(2026, 3, 10).atTime(10, 0)
        designaciones.save(
            OcDesignacion(
                uuid = UUID.randomUUID().toString(), empresaId = empresaId, titularActivoEmpresa = empresaId, estado = "VIGENTE",
                tipoCargo = "TITULAR", dedicacion = "EXCLUSIVA", tipoEmpresa = "PERSONA_JURIDICA",
                nombres = "Mariana", apellidos = "Quispe Valdivia", nacionalidad = "PE", tipoDocumento = "DNI",
                numeroDocumento = "00000000", fechaNacimiento = LocalDate.of(1986, 4, 12), domicilio = "Dirección de demostración",
                correo = "oficial.demo@example.test", cargo = "Oficial de cumplimiento", fechaIngreso = LocalDate.of(2024, 1, 15),
                vinculo = "LABORAL", direccionOficina = "Oficina de demostración", designa = "DIRECTORIO",
                nombreOrgano = "Directorio (demostración)", documentoTipo = "ACTA_DIRECTORIO", documentoFecha = LocalDate.of(2026, 3, 9),
                fechaDesignacion = LocalDate.of(2026, 3, 10), djReqPuntos = TextosOc.LETRAS.joinToString(","),
                djReqFirmaNombre = "Mariana Quispe Valdivia", djReqFechaFirma = LocalDate.of(2026, 3, 10), djTextoVersion = TextosOc.VERSION,
                djDesAceptada = true, djDesFirmaNombre = "Directorio (demostración)", djDesFechaFirma = LocalDate.of(2026, 3, 10),
                comFecha = LocalDate.of(2026, 3, 20), comNumeroSolicitud = "DEMO-0001", comResultado = "PROCEDENTE",
                comFechaNotificacion = LocalDate.of(2026, 3, 27), comRegistradaEn = LocalDate.of(2026, 3, 27).atTime(9, 0),
                guardadaEn = guardada, guardadaPorUsuario = MARCA_OFICIAL, guardadaPorNombre = "Datos de demostración",
                actualizadaEn = LocalDate.of(2026, 3, 27).atTime(9, 0)
            )
        )
        return 1
    }
}
