package com.security.modules.creditos.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Licencia de una empresa cliente: la bolsa de creditos que comparten todas sus cuentas, el cupo de
 * usuarios y el vencimiento. La define Sulotec (el ADMINISTRADOR universal); las empresas que aun no
 * tienen licencia estan en 0 creditos.
 *
 * Gastar: con saldo se ejecuta; sin saldo pero dentro de la tolerancia (un porcentaje extra de la bolsa)
 * se ejecuta y queda marcado como exceso; pasada la tolerancia se bloquea hasta ampliar.
 */
@Entity
@Table(name = "licencias_empresa")
class LicenciaEmpresa(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    // empresas.id (la empresa se resuelve por el nombre que lleva cada cuenta)
    @Column(name = "empresa_id", nullable = false, unique = true)
    var empresaId: Int,

    @Column(name = "creditos_totales", nullable = false)
    var creditosTotales: Long = 0,

    @Column(name = "creditos_consumidos", nullable = false)
    var creditosConsumidos: Long = 0,

    @Column(name = "tolerancia_porcentaje", nullable = false)
    var toleranciaPorcentaje: Int = TOLERANCIA_POR_DEFECTO,

    // null = sin limite de cuentas activas
    @Column(name = "cupo_usuarios")
    var cupoUsuarios: Int? = null,

    // null = no vence. La licencia vale hasta esta fecha inclusive.
    @Column(name = "fecha_vencimiento")
    var fechaVencimiento: LocalDate? = null,

    // Para avisar una sola vez al pasar el 80 % y al entrar en exceso (se rearman al ampliar la bolsa).
    @Column(name = "aviso_80_enviado", nullable = false)
    var aviso80Enviado: Boolean = false,

    @Column(name = "aviso_exceso_enviado", nullable = false)
    var avisoExcesoEnviado: Boolean = false,

    // Ultimo estado de vencimiento del que se aviso: VIGENTE, POR_VENCER, SOLO_LECTURA o SUSPENDIDA.
    @Column(name = "estado_notificado", nullable = false, length = 20)
    var estadoNotificado: String = ESTADO_VIGENTE,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime? = null,

    @Column(name = "fecha_actualizacion", nullable = false)
    var fechaActualizacion: LocalDateTime? = null
) {

    /** Creditos extra que se permiten pasada la bolsa. */
    fun toleranciaCreditos(): Long = creditosTotales * toleranciaPorcentaje / 100

    /** Lo que queda de la bolsa (sin contar la tolerancia). */
    fun disponibles(): Long = (creditosTotales - creditosConsumidos).coerceAtLeast(0)

    /** Creditos gastados por encima de la bolsa (dentro de la tolerancia). */
    fun exceso(): Long = (creditosConsumidos - creditosTotales).coerceAtLeast(0)

    fun enExceso(): Boolean = creditosConsumidos > creditosTotales

    /** Porcentaje de la bolsa gastado, de 0 a 100 (100 si se gasto algo con la bolsa en 0 o mas). */
    fun porcentajeConsumo(): Int = when {
        creditosTotales <= 0 -> if (creditosConsumidos > 0) 100 else 0
        else -> (creditosConsumidos * 100 / creditosTotales).coerceAtMost(100).toInt()
    }

    /** Si caben `costo` creditos mas contando la tolerancia. */
    fun admite(costo: Long): Boolean = creditosConsumidos + costo <= creditosTotales + toleranciaCreditos()

    fun estado(hoy: LocalDate): EstadoLicencia {
        val vence = fechaVencimiento ?: return EstadoLicencia.VIGENTE
        return when {
            !hoy.isAfter(vence) -> EstadoLicencia.VIGENTE
            !hoy.isAfter(vence.plusDays(DIAS_SOLO_LECTURA)) -> EstadoLicencia.SOLO_LECTURA
            else -> EstadoLicencia.SUSPENDIDA
        }
    }

    /** Dias que faltan para el vencimiento (negativo si ya vencio); null si no vence. */
    fun diasParaVencer(hoy: LocalDate): Long? =
        fechaVencimiento?.let { java.time.temporal.ChronoUnit.DAYS.between(hoy, it) }

    /** Tras cambiar la bolsa, vuelve a armar los avisos que ya no corresponden. */
    fun rearmarAvisos() {
        if (porcentajeConsumo() < PORCENTAJE_AVISO) aviso80Enviado = false
        if (!enExceso()) avisoExcesoEnviado = false
    }

    companion object {
        const val TOLERANCIA_POR_DEFECTO = 10
        const val PORCENTAJE_AVISO = 80
        const val DIAS_SOLO_LECTURA = 7L
        const val DIAS_AVISO_VENCIMIENTO = 7L
        const val ESTADO_VIGENTE = "VIGENTE"
        const val ESTADO_POR_VENCER = "POR_VENCER"
    }
}
