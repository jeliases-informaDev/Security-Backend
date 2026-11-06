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
import com.security.modules.scoring.application.service.ScoringService
import com.security.modules.scoring.domain.ScoringOpcion
import com.security.modules.scoring.domain.metodologia.CalculoScoring
import com.security.modules.scoring.dto.ClienteNuevoRequest
import com.security.modules.scoring.dto.DatosScoringRequest
import com.security.modules.scoring.infrastructure.ScoringDepartamentoRepository
import com.security.modules.scoring.infrastructure.ScoringOcupacionRepository
import com.security.modules.scoring.infrastructure.ScoringOpcionRepository
import com.security.modules.scoring.infrastructure.ScoringPaisGrupoRepository
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
 * siempre en la empresa de su cuenta. Se ven como datos reales (nombres, documentos y codigos con el formato de siempre);
 * la marca vive APARTE, en la tabla inicio_demo_marca (DemoMarca): tipo + id de cada fila creada.
 *  - registros: RO-AAAA-9NNNNN (rango reservado; NumeradorRegistros no los cuenta, el correlativo real no se mueve);
 *  - denuncias: DEN-AAAA-BBBNNN (los reales son DEN- + 8 caracteres al azar: no chocan);
 *  - oficial: solo si la empresa no tiene un titular activo.
 * Quitar borra SOLO lo marcado de esa empresa (y, por compatibilidad, lo que dejaron versiones anteriores con su marca
 * vieja: tipo de documento DEMO, RO-DEMO-, DEMO-<hash>-, documento DEMO- y '__demo_inicio__').
 * Las cifras reproducen el ejemplo del contrato.
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
    private val scoringService: ScoringService,
    private val ocupacionRepository: ScoringOcupacionRepository,
    private val opcionRepository: ScoringOpcionRepository,
    private val departamentoRepository: ScoringDepartamentoRepository,
    private val paisGrupoRepository: ScoringPaisGrupoRepository,
    private val marcas: DemoMarcaRepository,
    private val em: EntityManager
) {
    private val json = jacksonObjectMapper()

    companion object {
        const val MARCA_CONSULTA = "DEMO"
        const val PREFIJO_RO = "RO-DEMO-"
        const val PREFIJO_DENUNCIA = "DEMO-"
        const val MARCA_OFICIAL = "__demo_inicio__"
        const val PREFIJO_SCORING = "DEMO-"
        // Tipos de la tabla de marcas
        const val LISTA_PEP = "Funcionarios públicos (PEP)"
        const val M_CONSULTA = "CONSULTA"
        const val M_REGISTRO = "REGISTRO"
        const val M_DENUNCIA = "DENUNCIA"
        const val M_SCORING = "SCORING"
        const val M_OFICIAL = "OFICIAL"
        /** Rango reservado de los registros de la demo: RO-AAAA-900001 en adelante. */
        const val BASE_REGISTRO = 900000

        /** Departamento y provincia (texto, como los guarda el formulario) de quien realiza la operacion. */
        private val UBICACIONES = listOf(
            "Lima" to "Lima", "Arequipa" to "Arequipa", "Cusco" to "Cusco", "Piura" to "Piura", "La Libertad" to "Trujillo",
            "Junín" to "Huancayo", "Lambayeque" to "Chiclayo", "Callao" to "Callao"
        )

        private val NOMBRES = listOf(
            "Luis Alberto", "María Elena", "José Carlos", "Rosa María", "Jorge Luis", "Carmen Rosa", "Juan Carlos", "Ana Lucía",
            "Miguel Ángel", "Patricia", "César Augusto", "Gladys", "Víctor Hugo", "Silvia", "Raúl", "Elizabeth", "Wilber",
            "Yolanda", "Fernando", "Milagros", "Edwin", "Karina", "Hugo", "Liliana", "Óscar", "Verónica", "Javier", "Sonia",
            "Ricardo", "Maribel"
        )
        private val APELLIDOS = listOf(
            "Quispe", "Mamani", "Huamán", "Flores", "Rojas", "Vargas", "Torres", "Mendoza", "Castillo", "Chávez", "Ramos",
            "Gutiérrez", "Salazar", "Medina", "Paredes", "Cruz", "Silva", "Condori", "Ríos", "Vega", "Díaz", "Espinoza",
            "Sánchez", "Ramírez", "Huanca", "Ccahuana", "Palomino", "Zevallos", "Aguilar", "Cárdenas"
        )
        private val EMPRESAS = listOf(
            "Inmobiliaria Los Andes S.A.C.", "Constructora Pacífico S.A.C.", "Desarrollos Valle Verde S.A.C.",
            "Proyectos Urbanos Horizonte S.A.C.", "Edificaciones Costa Norte S.A.C.", "Inmobiliaria Altiplano S.A.C."
        )
        private val DIRECCIONES = listOf(
            "Av. Los Próceres 845", "Jr. Huallaga 312", "Calle Las Begonias 150", "Av. Ejército 1020", "Av. Grau 455",
            "Jr. Ayacucho 278", "Calle Los Pinos 96", "Av. Bolognesi 610"
        )

        /** Nombre de apariencia peruana armado con listas de nombres y apellidos comunes: (nombres, paterno, materno). */
        fun nombreFicticio(i: Int): Triple<String, String, String> {
            val paterno = APELLIDOS[(i * 7 + 1) % APELLIDOS.size]
            var materno = APELLIDOS[(i * 11 + 5) % APELLIDOS.size]
            if (materno == paterno) materno = APELLIDOS[(i * 11 + 6) % APELLIDOS.size]
            return Triple(NOMBRES[(i * 13 + i / NOMBRES.size) % NOMBRES.size], paterno, materno)
        }

        /** DNI de 8 digitos que empieza con 9 (rango aun no asignado). */
        fun dni(i: Int): String = "9" + "%07d".format((i.toLong() * 7919 + 1234567) % 10_000_000)

        /** RUC de 11 digitos con prefijo 20 y numero inventado. */
        fun ruc(i: Int): String = "20" + "%09d".format((i.toLong() * 104729 + 605000000) % 1_000_000_000)

        private val DESCRIPCIONES = mapOf(
            CausaDenuncia.CONFLICTO to "Un trabajador del área comercial habría aprobado descuentos a una empresa vinculada a un familiar directo. No informó el vínculo al comité. Se adjuntan correos internos como referencia.",
            CausaDenuncia.FRAUDE to "Se detectaron comprobantes de pago con montos alterados en la rendición de gastos de un proyecto. La diferencia se habría repetido durante tres meses.",
            CausaDenuncia.SOBORNO to "Un proveedor habría ofrecido un pago en efectivo para agilizar la conformidad de una obra. El ofrecimiento se hizo fuera de las oficinas.",
            CausaDenuncia.ACOSO to "Una colaboradora reporta comentarios ofensivos reiterados de un supervisor durante las reuniones de equipo. Solicita reserva de su identidad.",
            CausaDenuncia.OPERACION_SOSPECHOSA to "Un cliente pidió fraccionar el pago de un departamento en varios depósitos en efectivo de distintas personas. No explicó el origen de los fondos.",
            CausaDenuncia.OTRAS to "Se reporta el uso de un vehículo de la empresa para fines personales durante los fines de semana. El hecho se habría repetido varias veces."
        )
    }

    // ================================================================== estado

    @Transactional(readOnly = true)
    fun cargado(empresa: String): Boolean = conteos(empresa).values.any { it > 0 }

    private fun conteos(empresa: String): Map<String, Int> = mapOf(
        "consultas" to idsConsultas(empresa).size,
        "registros" to idsRegistros(empresa).size,
        "denuncias" to idsDenuncias(empresa).size,
        "oficial" to idsDesignaciones(empresa).size,
        "scoring" to idsScoring(empresa).size
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
            "oficial" to crearOficial(admin, empresa),
            "scoring" to crearScoring(admin, empresa)
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
        val scoringIds = idsScoring(empresa)
        if (scoringIds.isNotEmpty()) nativo("DELETE FROM scoring_riesgo WHERE id IN (:ids)", scoringIds)
        if (designacionesIds.isNotEmpty()) {
            nativo("DELETE FROM oc_evento_cambio WHERE evento_id IN (SELECT id FROM oc_evento WHERE designacion_id IN (:ids))", designacionesIds)
            nativo("DELETE FROM oc_evento WHERE designacion_id IN (:ids)", designacionesIds)
            nativo("DELETE FROM oc_pendiente WHERE designacion_id IN (:ids)", designacionesIds)
            nativo("DELETE FROM oc_remocion WHERE designacion_id IN (:ids)", designacionesIds)
            nativo("DELETE FROM oc_designacion WHERE id IN (:ids)", designacionesIds)
        }
        marcas.borrarDe(clave(empresa))
        em.clear()
        return DemoBorradoResponse(
            cargado = false,
            eliminados = linkedMapOf(
                "consultas" to consultasIds.size, "registros" to registrosIds.size,
                "denuncias" to denunciasIds.size, "oficial" to designacionesIds.size,
                "scoring" to scoringIds.size
            )
        )
    }

    // ================================================================== marcas (siempre dentro de la empresa)

    private fun clave(empresa: String) = empresa.trim().lowercase()

    private fun marcados(empresa: String, tipo: String): List<Long> = marcas.idsDe(clave(empresa), tipo)

    private fun marcar(empresa: String, tipo: String, ids: Collection<Number>) {
        marcas.saveAll(ids.map { DemoMarca(empresa = clave(empresa), tipo = tipo, idFila = it.toLong()) })
    }

    private fun idsConsultas(empresa: String): List<Long> = (marcados(empresa, M_CONSULTA) + idsConsultasViejas(empresa)).distinct()

    private fun idsRegistros(empresa: String): List<Long> = (marcados(empresa, M_REGISTRO) + idsRegistrosViejos(empresa)).distinct()

    private fun idsDenuncias(empresa: String): List<Int> =
        (marcados(empresa, M_DENUNCIA).map { it.toInt() } + idsDenunciasViejas(empresa)).distinct()

    private fun idsDesignaciones(empresa: String): List<Long> = (marcados(empresa, M_OFICIAL) + idsDesignacionesViejas(empresa)).distinct()

    private fun idsScoring(empresa: String): List<Int> =
        (marcados(empresa, M_SCORING).map { it.toInt() } + idsScoringViejos(empresa)).distinct()

    // ---- Marcas de versiones anteriores (solo para poder quitarlas)

    private fun idsConsultasViejas(empresa: String): List<Long> =
        em.createQuery(
            "SELECT c.id FROM ConsultaLista c WHERE c.criterioTipoDocumento = :marca AND LOWER(TRIM(c.empresa)) = LOWER(TRIM(:empresa))",
            Long::class.javaObjectType
        ).setParameter("marca", MARCA_CONSULTA).setParameter("empresa", empresa).resultList

    private fun idsRegistrosViejos(empresa: String): List<Long> =
        em.createQuery(
            "SELECT r.id FROM RegistroOperacionesEntity r WHERE r.numeroRegistro LIKE :prefijo AND LOWER(TRIM(r.empresa)) = LOWER(TRIM(:empresa))",
            Long::class.javaObjectType
        ).setParameter("prefijo", "$PREFIJO_RO%").setParameter("empresa", empresa).resultList

    private fun idsDenunciasViejas(empresa: String): List<Int> =
        em.createQuery(
            """
            SELECT d.id FROM DenunciaEntity d LEFT JOIN d.receptorUsuario r LEFT JOIN d.creadoPorUsuario c
            WHERE d.codigoSeguimiento LIKE :prefijo AND LOWER(TRIM(COALESCE(r.empresa, c.empresa))) = LOWER(TRIM(:empresa))
            """,
            Int::class.javaObjectType
        ).setParameter("prefijo", prefijoDenuncia(empresa) + "%").setParameter("empresa", empresa).resultList

    private fun idsDesignacionesViejas(empresa: String): List<Long> {
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
            ListaDisponible(null, LISTA_PEP, true, null)
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
        marcar(empresa, M_CONSULTA, creadas.map { it.id!! })
        decisiones.saveAll(creadas.filter { it.homonimo }.map {
            ConsultaListaDecision(idConsulta = it.id!!, accion = ConsultaListaDecision.ACCION_HOMONIMO,
                comentario = it.homonimoComentario, usuario = admin, fecha = it.homonimoFecha!!)
        })
        return creadas.size
    }

    private fun aparicion(f: FilaDef): AparicionConsulta {
        val lista = if (f.todasPep) LISTA_PEP else f.listas.firstOrNull() ?: f.nombre
        val descripcion = when {
            f.todasPep -> "Funcionario con cargo de confianza en una entidad pública."
            f.codigo.startsWith("OFAC") || f.codigo.startsWith("ONU") || f.codigo.startsWith("UNION") ->
                "Persona incluida en una lista de sanciones vigente."
            f.codigo.contains("INVESTIGADOS") -> "Investigación fiscal en curso por presunto delito financiero."
            else -> "Nota periodística sobre una investigación fiscal en curso."
        }
        return AparicionConsulta(
            tipoListaId = null, nombreLista = lista, esPep = f.todasPep, ordenGrupo = null, nombreGrupo = null,
            registro = RegistroLista(
                lista = lista, esPep = f.todasPep, descripcion = descripcion,
                institucion = if (f.todasPep) "Municipalidad provincial" else null,
                cargo = if (f.todasPep) "Gerente municipal" else null,
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
            tipoConsulta = "CLIENTE", criterioTipoDocumento = null, criterioNombres = nombre,
            criterioApellidoPaterno = paterno, criterioApellidoMaterno = materno,
            criterioTexto = "$nombre $paterno $materno", modo = ConsultaLista.MODO_NOMBRE, totalCandidatos = if (p.fila == null) 0 else 1,
            coincidencia = if (p.fila == null) ConsultaLista.COINCIDENCIA_SIN_RESULTADOS else ConsultaLista.COINCIDENCIA_NOMBRE,
            consultadoNombre = completo, tipoPersona = "NATURAL", consultadoPais = "Perú",
            porcentaje = r.porcentaje, clasificacion = r.clasificacion, listasConsultadas = r.listasConsultadas,
            listasConCoincidencia = r.listasConCoincidencia, registrosEnListas = r.registrosEnListas,
            noticiasEnMedios = r.noticiasEnMedios, esPep = r.esPep,
            listasCoincidenciaTexto = r.nombresConCoincidencia().joinToString(" | ").ifBlank { null },
            resultado = json.writeValueAsString(r),
            homonimo = p.homonimo,
            homonimoComentario = if (p.homonimo) "La fecha de nacimiento y el número de documento no coinciden con el registro de la lista." else null,
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
        // Algunos en soles (monto en S/; su equivalente en US$ es el mismo de antes, asi los totales no cambian).
        repeat(20) { k ->
            val dolares = 20000.0 + 1100 * k
            actual += if (k % 5 == 2) PlanRegistro(unidades[k % unidades.size], usd(dolares * 3.75), "PEN", false)
            else PlanRegistro(unidades[k % unidades.size], usd(dolares), "USD", false)
        }
        // Los tres ultimos (los que muestra la tarjeta), con los nombres del ejemplo del diseno (ficticios).
        actual += PlanRegistro("COMPRA_OFICINA", usd(520000.0), "PEN", true, inusual = AnalisisOperacionEntity.EN_ANALISIS, persona = "Salazar Cruz" to "Marco")
        actual += PlanRegistro("VENTA_LOTE", usd(42000.0), "USD", true, sinResponsable = true, persona = "Medina Paredes" to "Rosa")
        actual += PlanRegistro("VENTA_DEPARTAMENTO", usd(128500.0), "USD", true, sinResponsable = true, persona = "Rojas Silva" to "Pedro")

        val anterior = List(30) { k ->
            if (k % 3 == 0) PlanRegistro("COMPRA_VIVIENDA", usd(60000.0 + 1500 * k), "USD", false)
            else PlanRegistro("COMPRA_LOTE", usd(18500.0 + 300 * k), "USD", false)
        }

        val ahora = reloj.ahora()
        val inicioHoy = reloj.hoy().atStartOfDay()
        var correlativo = 0
        val creados = mutableListOf<Long>()
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
                    numeroRegistro = "RO-%04d-%06d".format(creado.year, BASE_REGISTRO + correlativo),
                    idUsuario = admin.id!!, empresa = empresa, oficina = "Oficina principal",
                    fechaRegistro = creado.toLocalDate(), fechaCreacion = creado,
                    personaFisica = Persona().apply {
                        tipoDocumento = "DNI"; numeroDocumento = dni(i + 100); this.nombres = nombres
                        this.apellidos = apellidos; nacionalidad = "Peruana"; tipoPersona = "NATURAL"
                        val lugar = UBICACIONES[if (i % 3 == 0) 0 else i % UBICACIONES.size]
                        domicilio = DIRECCIONES[i % DIRECCIONES.size] + ", " + lugar.second
                        departamento = lugar.first; provincia = lugar.second
                    },
                    personaRepresentada = null,
                    operacion = Operacion(
                        fechaOperacion = creado.toLocalDate().minusDays(p.diasAntes),
                        lugar = UBICACIONES[if (i % 3 == 0) 0 else i % UBICACIONES.size].second,
                        modalidadPago = "TRANSFERENCIA_BANCARIA", moneda = p.moneda, monto = p.monto, tipoOperacion = p.tipo,
                        cuenta1 = null, cuenta2 = null, cuenta3 = null, origenFondos = "AHORROS",
                        descripcion = "Compraventa con minuta firmada; escritura pública en trámite.",
                        umbralEstado = if (p.sobre) "SI_SE_REGISTRA" else "NO_ALCANZA_UMBRAL",
                        umbralUsd = umbral, umbralNormaUsd = umbral, umbralInterno = false,
                        tipoBien = if (lote) "LOTE_TERRENO" else "UNIDAD_INMOBILIARIA", sector = "CONSTRUCCION_INMOBILIARIA",
                        montoUsd = montoUsd, tcMes = YearMonth.from(creado).minusMonths(1).toString(),
                        tcUsd = tc, tcMoneda = if (p.moneda == "PEN") BigDecimal("1.000000") else tc, umbralVerificadoEn = creado
                    ),
                    idUsuarioResponsable = if (p.sinResponsable) null else admin.id,
                    beneficiarios = listOf(
                        BeneficiarioEntity(nombre = EMPRESAS[i % EMPRESAS.size], tipoDocumento = "RUC", numeroDocumento = ruc(i % EMPRESAS.size))
                    )
                )
            )
            creados += registro.id!!
            p.inusual?.let { estado ->
                analisis.save(
                    AnalisisOperacionEntity(
                        registroId = registro.id!!, estado = estado, senal = "ORIGEN_INCONSISTENTE",
                        fuente = "ANALISTA", fechaDeteccion = creado.toLocalDate(),
                        descripcionSenal = "El cliente no acreditó el origen de los fondos con documentos de respaldo.",
                        decision = if (estado == AnalisisOperacionEntity.SOSPECHOSA) AnalisisOperacionEntity.SOSPECHOSA else null,
                        calificadaEn = if (estado == AnalisisOperacionEntity.SOSPECHOSA) creado else null,
                        calificadaPor = if (estado == AnalisisOperacionEntity.SOSPECHOSA) admin.usuario else null,
                        actualizadoEn = creado, actualizadoPor = admin.usuario
                    )
                )
            }
        }
        marcar(empresa, M_REGISTRO, creados)
        return correlativo
    }

    // ================================================================== Canal de denuncias

    private fun crearDenuncias(admin: Usuario, empresa: String): Int {
        val ahora = reloj.ahora()
        val hoy = reloj.hoy()
        val inicioMes = hoy.withDayOfMonth(1).atStartOfDay()
        // DEN-AAAA-BBBNNN: BBB sale de la empresa (y se corre si ya lo usa otra empresa), NNN es el correlativo.
        var bloque = Math.floorMod(clave(empresa).hashCode(), 1000)
        while (em.createQuery("SELECT COUNT(d) FROM DenunciaEntity d WHERE d.codigoSeguimiento LIKE :p", Long::class.javaObjectType)
                .setParameter("p", "DEN-%04d-%03d%%".format(hoy.year, bloque)).singleResult > 0L) {
            bloque = (bloque + 1) % 1000
        }
        val prefijo = "DEN-%04d-%03d".format(hoy.year, bloque)
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
                descripcion = DESCRIPCIONES.getValue(causa),
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
                    if (it.resultadoCierre == ResultadoCierreDenuncia.ARCHIVADA) it.motivoCierre = "No se encontraron elementos suficientes para continuar con la investigación."
                }
            }
            if (i == 2 || i == 5) {
                d.derivadaAlOficial = true
                d.fechaDerivacion = limitar(creada.plusMinutes(30), creada)
                d.derivadaPorUsuario = admin
                d.motivoDerivacion = "Posible relación con una operación registrada como inusual."
            }
            lista += d
        }
        // Cerradas este mes pero recibidas antes: 3 a tiempo (5 a 7 dias) y 5 fuera de plazo; promedio de las 28: 9 dias.
        val cierre = limitar(inicioMes.plusHours(10), inicioMes)
        listOf(5L, 6L, 7L).forEach { lista += nueva(CausaDenuncia.CONFLICTO, cierre.minusDays(it), EstadoDenuncia.CERRADO, cierre) }
        listOf(46L, 47L, 47L, 47L, 47L).forEach { lista += nueva(CausaDenuncia.SOBORNO, cierre.minusDays(it), EstadoDenuncia.CERRADO, cierre) }
        // Una abierta con el plazo vencido.
        lista += nueva(CausaDenuncia.CONFLICTO, ahora.minusDays(plazoDenuncias.dias + 5), EstadoDenuncia.EN_PROCESO, null)
        marcar(empresa, M_DENUNCIA, denuncias.saveAll(lista).map { it.id!! })
        return lista.size
    }

    // ================================================================== Scoring (cabina)

    private fun idsScoringViejos(empresa: String): List<Int> =
        em.createQuery(
            """
            SELECT s.id FROM ScoringRiesgo s JOIN s.usuario u
            WHERE s.clienteDocumento LIKE :prefijo AND LOWER(TRIM(u.empresa)) = LOWER(TRIM(:empresa))
            """,
            Int::class.javaObjectType
        ).setParameter("prefijo", "$PREFIJO_SCORING%").setParameter("empresa", empresa).resultList

    /**
     * 44 evaluaciones de la version 2 en 10 departamentos, repartidas en los ultimos 30 dias, calculadas y guardadas con
     * el MISMO servicio de scoring (sin consumir tokens), asi su detalle y su PDF abren igual que una real.
     */
    private fun crearScoring(admin: Usuario, empresa: String): Int {
        fun <T> cercano(lista: List<T>, objetivo: Int, puntaje: (T) -> Int?): T? =
            lista.filter { puntaje(it) != null }.minByOrNull { kotlin.math.abs(puntaje(it)!! - objetivo) }
        val ocupaciones = ocupacionRepository.findAll()
        val opciones = opcionRepository.findAll()
        fun opcion(tipo: String, objetivo: Int) = cercano(opciones.filter { it.tipo == tipo }, objetivo) { it.puntaje }
        val pais = paisGrupoRepository.todosConPais().filter { it.grupo != null && it.altoRiesgoGafi != true }.minByOrNull { it.grupo!! }
            ?: return 0
        val porNombre = departamentoRepository.findAll().filter { it.puntajeV2 != null }
            .associateBy { CalculoScoring.normalizar(it.nombre) }
        val deptos = listOf("LIMA", "AREQUIPA", "CUSCO", "PIURA", "LA LIBERTAD", "JUNIN", "LAMBAYEQUE", "ICA", "PUNO", "CALLAO")
            .mapNotNull { porNombre[it] }.ifEmpty { return 0 }
        val ahora = reloj.ahora()
        val ids = mutableListOf<Int>()
        for (k in 0 until 44) {
            // Nivel buscado: Alto (ocupacion y monto en 4: regla de dos factores en 4), Medio o Bajo.
            val objetivo = when {
                k % 6 == 0 -> 4
                k % 3 == 1 -> 3
                else -> 1
            }
            val depto = deptos[(k * 7) % deptos.size]
            val (nombre, paterno, materno) = nombreFicticio(k + 500)
            val request = DatosScoringRequest(
                cliente = ClienteNuevoRequest("NATURAL", "DNI", dni(k + 700), "$paterno $materno, $nombre"),
                ocupacionId = cercano(ocupaciones, objetivo) { it.puntajeV2 }?.id,
                montoOperacion = BigDecimal(
                    when (objetivo) {
                        4 -> 800000
                        3 -> 200000
                        else -> 9500 + 350 * k
                    }
                ),
                productoId = opcion(ScoringOpcion.PRODUCTO, if (objetivo == 1) 1 else 3)?.id,
                canalId = opcion(ScoringOpcion.CANAL, if (objetivo == 1) 1 else 2)?.id,
                medioPagoId = opcion(ScoringOpcion.MEDIO_PAGO, if (objetivo == 1) 1 else 2)?.id,
                departamentoResidenciaId = depto.id,
                departamentoOperacionId = depto.id,
                paisFondosId = pais.pais.id
            )
            // Repartidas en los ultimos 30 dias, nunca en el futuro.
            val fecha = limitar(ahora.minusDays((k * 29L) / 43).withHour(9 + k % 9).withMinute((k * 13) % 60), ahora.minusDays(30))
            ids += scoringService.evaluarDemostracion(admin, request, fecha).id
        }
        marcar(empresa, M_SCORING, ids)
        return ids.size
    }

    // ================================================================== Oficial de cumplimiento

    private fun crearOficial(admin: Usuario, empresa: String): Int {
        val e = empresas.findByNombreIgnoreCase(empresa) ?: return 0
        val empresaId = e.id ?: return 0
        // Nunca encima de un oficial real: si ya hay un titular activo, la demostracion no crea otro.
        if (designaciones.findFirstByEmpresaIdAndTipoCargoAndEstadoNotOrderByGuardadaEnDescIdDesc(empresaId, "TITULAR", "REMOVIDA") != null) return 0
        val guardada = LocalDate.of(2026, 3, 10).atTime(10, 0)
        val d = designaciones.save(
            OcDesignacion(
                uuid = UUID.randomUUID().toString(), empresaId = empresaId, titularActivoEmpresa = empresaId, estado = "VIGENTE",
                tipoCargo = "TITULAR", dedicacion = "EXCLUSIVA", tipoEmpresa = "PERSONA_JURIDICA",
                nombres = "Mariana", apellidos = "Quispe Valdivia", nacionalidad = "PE", tipoDocumento = "DNI",
                numeroDocumento = dni(900), fechaNacimiento = LocalDate.of(1986, 4, 12), domicilio = "Calle Los Laureles 215, Lima",
                correo = "m.quispe@example.com", cargo = "Oficial de cumplimiento", fechaIngreso = LocalDate.of(2024, 1, 15),
                vinculo = "LABORAL", direccionOficina = "Av. Principal 1450, oficina 502, Lima", designa = "DIRECTORIO",
                nombreOrgano = "Directorio de la empresa", documentoTipo = "ACTA_DIRECTORIO", documentoFecha = LocalDate.of(2026, 3, 9),
                fechaDesignacion = LocalDate.of(2026, 3, 10), djReqPuntos = TextosOc.LETRAS.joinToString(","),
                djReqFirmaNombre = "Mariana Quispe Valdivia", djReqFechaFirma = LocalDate.of(2026, 3, 10), djTextoVersion = TextosOc.VERSION,
                djDesAceptada = true, djDesFirmaNombre = "Presidencia del Directorio", djDesFechaFirma = LocalDate.of(2026, 3, 10),
                comFecha = LocalDate.of(2026, 3, 20), comNumeroSolicitud = "2026-000184", comResultado = "PROCEDENTE",
                comFechaNotificacion = LocalDate.of(2026, 3, 27), comRegistradaEn = LocalDate.of(2026, 3, 27).atTime(9, 0),
                guardadaEn = guardada, guardadaPorUsuario = admin.usuario, guardadaPorId = admin.id,
                guardadaPorNombre = listOfNotNull(admin.nombres, admin.apePat, admin.apeMat).joinToString(" ").ifBlank { admin.usuario },
                actualizadaEn = LocalDate.of(2026, 3, 27).atTime(9, 0)
            )
        )
        marcar(empresa, M_OFICIAL, listOf(d.id!!))
        return 1
    }
}
