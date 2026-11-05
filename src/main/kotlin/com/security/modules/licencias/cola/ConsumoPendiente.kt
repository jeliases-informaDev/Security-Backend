package com.security.modules.licencias.cola

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

enum class OperacionPendiente { CONSUMIR, ANULAR }

/**
 * Consumo (o anulación) que no se pudo avisar al servicio de licencias porque no respondió. La acción ya se
 * permitió; LicenciasReintento lo reenvía cada minuto con la MISMA clave, así el servicio nunca descuenta dos veces.
 */
@Entity
@Table(
    name = "consumos_licencia_pendientes",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_consumo_pendiente_clave", columnNames = ["clave_idempotente", "operacion"])
    ]
)
class ConsumoPendiente(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "operacion", nullable = false, length = 10)
    var operacion: OperacionPendiente,

    @Column(name = "clave_idempotente", nullable = false, length = 150)
    var claveIdempotente: String,

    // Solo los consumos la llevan: la anulación va por la clave.
    @Column(name = "empresa_uuid", length = 36)
    var empresaUuid: String? = null,

    @Column(name = "tipo", length = 40)
    var tipo: String? = null,

    @Column(name = "cantidad", nullable = false)
    var cantidad: Int = 1,

    @Column(name = "usuario", length = 150)
    var usuario: String? = null,

    @Column(name = "hecho_por_sulotec", nullable = false)
    var hechoPorSulotec: Boolean = false,

    @Column(name = "detalle", length = 500)
    var detalle: String? = null,

    @Column(name = "intentos", nullable = false)
    var intentos: Int = 0,

    @Column(name = "ultimo_error", length = 500)
    var ultimoError: String? = null,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime = LocalDateTime.now()
)

@Repository
interface ConsumoPendienteRepository : JpaRepository<ConsumoPendiente, Long> {

    // En orden de llegada: una anulación siempre va después del consumo que anula.
    fun findTop100ByOrderByIdAsc(): List<ConsumoPendiente>

    fun findByClaveIdempotenteAndOperacion(claveIdempotente: String, operacion: OperacionPendiente): ConsumoPendiente?

    fun existsByClaveIdempotenteAndOperacion(claveIdempotente: String, operacion: OperacionPendiente): Boolean
}
