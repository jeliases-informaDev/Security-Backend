package com.security.modules.denuncias.application.service

import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.ResultadoCierreDenuncia
import com.security.modules.denuncias.infrastructure.persistence.entity.DenunciaEntity
import com.security.modules.oficial.aplicacion.ROL_ADMIN_EMPRESA
import com.security.modules.oficial.aplicacion.ROL_ADMIN_UNIVERSAL
import com.security.shared.datos.entities.Usuario
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/*
 * Cierre con resultado, derivacion reservada al oficial y plazo de atencion (docs/DENUNCIAS-CIERRE.md).
 */

/** Error del canal de denuncias con su codigo (el controlador lo devuelve como ApiErrorResponse.code). */
class DenunciaException(val status: HttpStatus, val code: String, message: String) : RuntimeException(message)

object CodigosDenuncia {
    const val RESULTADO_OBLIGATORIO = "DENUNCIA_RESULTADO_OBLIGATORIO"
    const val MOTIVO_OBLIGATORIO = "DENUNCIA_MOTIVO_OBLIGATORIO"
    const val MOTIVO_LARGO = "DENUNCIA_MOTIVO_LARGO"
    const val RESULTADO_SOLO_AL_CERRAR = "DENUNCIA_RESULTADO_SOLO_AL_CERRAR"
}

data class OpcionCatalogo(val codigo: String, val nombre: String)

data class ResultadoCatalogo(val codigo: String, val nombre: String, val pideMotivo: Boolean)

data class CatalogoDenunciasResponse(
    val situaciones: List<OpcionCatalogo>,
    val estados: List<OpcionCatalogo>,
    val resultadosCierre: List<ResultadoCatalogo>,
    val plazoAtencionDias: Long
)

/** Detalle reservado de la derivacion: solo para quien puedeVerReservado. */
data class DerivacionResponse(
    val fechaDerivacion: LocalDateTime?,
    val derivadaPorUsuarioId: Int?,
    val derivadaPor: String?,
    val motivo: String?
)

/**
 * Plazo de atencion de una denuncia (dias calendario desde la creacion). «Hoy» usa la misma zona con la que el modulo
 * guarda las fechas (LocalDateTime.now() del servidor); se puede fijar en las pruebas.
 */
@Service
class PlazoDenunciasService(
    // SUPUESTO A CONFIRMAR con el usuario: 30 dias calendario.
    @Value("\${plaft.denuncias.plazo-atencion-dias:30}") val dias: Long
) {
    var clock: Clock = Clock.systemDefaultZone()

    fun hoy(): LocalDate = LocalDate.now(clock)

    fun fechaLimite(d: DenunciaEntity): LocalDate = d.fechaCreacion.toLocalDate().plusDays(dias)

    /** null si ya esta cerrada; negativo = vencida hace N dias. */
    fun diasParaVencer(d: DenunciaEntity): Long? =
        if (d.estado == EstadoDenuncia.CERRADO) null else ChronoUnit.DAYS.between(hoy(), fechaLimite(d))

    fun vencida(d: DenunciaEntity): Boolean = d.estado != EstadoDenuncia.CERRADO && hoy().isAfter(fechaLimite(d))

    /** null si sigue abierta. */
    fun atendidaATiempo(d: DenunciaEntity): Boolean? =
        if (d.estado != EstadoDenuncia.CERRADO) null
        else d.fechaCierre?.let { !it.toLocalDate().isAfter(fechaLimite(d)) }

    fun catalogo() = CatalogoDenunciasResponse(
        situaciones = CausaDenuncia.entries.map { OpcionCatalogo(it.name, it.etiqueta) },
        estados = EstadoDenuncia.entries.map { OpcionCatalogo(it.name, it.etiqueta) },
        resultadosCierre = ResultadoCierreDenuncia.entries.map { ResultadoCatalogo(it.name, it.etiqueta, it.pideMotivo) },
        plazoAtencionDias = dias
    )
}

/**
 * Quien ve lo reservado de la derivacion (quien, cuando, motivo, y si una denuncia esta derivada): los mismos permisos de
 * escritura del modulo Oficial de Cumplimiento (AccesoOficialService: ADMINISTRADOR universal y ADMINISTRADOR_EMPRESA),
 * mas un rol OFICIAL_CUMPLIMIENTO si se crea. El supervisor (receptor) no lo ve.
 */
object ReservaOficial {
    const val ROL_OFICIAL_CUMPLIMIENTO = "OFICIAL_CUMPLIMIENTO"

    fun puedeVerReservado(u: Usuario): Boolean {
        val roles = u.roles.filter { it.rol.activo }.map { it.rol.codigo.trim().uppercase() }
        return ROL_ADMIN_UNIVERSAL in roles || ROL_ADMIN_EMPRESA in roles || ROL_OFICIAL_CUMPLIMIENTO in roles
    }
}

/**
 * Columna denuncias.causa: Hibernate 6+ la crea como ENUM de MySQL y `ddl-auto: update` no la cambia, asi que una base
 * vieja rechazaria FRAUDE, OPERACION_SOSPECHOSA y OTRAS. Al arrancar, si es ENUM, pasa a VARCHAR(50) conservando los
 * valores (MODIFY copia el texto). Idempotente; un fallo se registra y no impide arrancar.
 */
@Component
@Order(3)
class EsquemaDenunciasInicializador(private val jdbc: JdbcTemplate) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        for ((columna, largo) in listOf("causa" to 50, "resultado_cierre" to 20)) {
            try {
                aTexto("denuncias", columna, largo)
            } catch (e: Exception) {
                log.warn("Denuncias: no se pudo pasar {} a texto: {}", columna, e.message)
            }
        }
    }

    /** true si la cambio. */
    internal fun aTexto(tabla: String, columna: String, largo: Int): Boolean {
        val fila = jdbc.queryForList(
            """
            SELECT data_type AS tipo, is_nullable AS nulo FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """,
            tabla, columna
        ).firstOrNull() ?: return false
        val tipo = (fila["tipo"] ?: fila["TIPO"]).toString()
        if (!tipo.equals("enum", ignoreCase = true)) return false
        val nulo = (fila["nulo"] ?: fila["NULO"]).toString().equals("YES", ignoreCase = true)
        jdbc.execute("ALTER TABLE $tabla MODIFY $columna VARCHAR($largo) ${if (nulo) "NULL" else "NOT NULL"}")
        log.info("Denuncias: {}.{} paso de ENUM a VARCHAR({})", tabla, columna, largo)
        return true
    }
}
