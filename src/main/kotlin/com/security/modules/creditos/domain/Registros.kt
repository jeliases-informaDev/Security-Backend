package com.security.modules.creditos.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/** Historial de la bolsa de una empresa: cada gasto, recarga o cambio de licencia. */
@Entity
@Table(name = "movimientos_credito")
class MovimientoCredito(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "empresa_id", nullable = false)
    var empresaId: Int,

    // Cuenta que hizo la accion (null en recargas hechas por el sistema)
    @Column(name = "usuario_id")
    var usuarioId: Int? = null,

    @Column(name = "usuario", length = 100)
    var usuario: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    var tipo: TipoMovimiento,

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", length = 40)
    var accion: AccionCredito? = null,

    // Creditos gastados o recargados
    @Column(name = "cantidad", nullable = false)
    var cantidad: Long,

    // El gasto paso la bolsa y uso la tolerancia
    @Column(name = "exceso", nullable = false)
    var exceso: Boolean = false,

    // Dato para ubicar lo que se creo (p. ej. "scoring:15"); sin datos personales
    @Column(name = "referencia", length = 255)
    var referencia: String? = null,

    // Total gastado de la bolsa despues de este movimiento
    @Column(name = "consumidos_despues", nullable = false)
    var consumidosDespues: Long = 0,

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDateTime = LocalDateTime.now()
)

/** Costo de cada accion. Si falta una fila se usa AccionCredito.costoInicial. */
@Entity
@Table(name = "tarifas_credito")
class TarifaCredito(

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "accion", length = 40)
    var accion: AccionCredito,

    @Column(name = "costo", nullable = false)
    var costo: Int,

    @Column(name = "fecha_actualizacion", nullable = false)
    var fechaActualizacion: LocalDateTime = LocalDateTime.now()
)

/** Aviso dentro de la app para el administrador de la empresa (ademas del correo). */
@Entity
@Table(name = "avisos_licencia")
class AvisoLicencia(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "empresa_id", nullable = false)
    var empresaId: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    var tipo: TipoAviso,

    @Column(name = "asunto", nullable = false)
    var asunto: String,

    @Column(name = "mensaje", nullable = false, length = 1000)
    var mensaje: String,

    @Column(name = "leido", nullable = false)
    var leido: Boolean = false,

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDateTime = LocalDateTime.now()
)
