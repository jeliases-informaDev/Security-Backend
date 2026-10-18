package com.security.shared.datos.entities

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Empresa cliente de la plataforma. Cada cuenta pertenece a una: usuarios.empresa guarda su nombre y
 * el modulo de operaciones solo muestra los registros de la empresa del usuario que entro.
 *
 * Esta tabla es la lista de empresas entre las que se elige al crear una cuenta, para que el nombre
 * quede escrito siempre igual. El nombre no se renombra: para corregir uno mal escrito se crea la
 * empresa correcta y se pasan las cuentas a ella.
 */
@Entity
@Table(name = "empresas")
class Empresa(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "nombre", nullable = false, unique = true)
    var nombre: String,

    @Column(name = "ruc", length = 11, unique = true)
    var ruc: String? = null,

    // Identificador publico, estable y no adivinable (se asigna al crear; a las empresas viejas, al arrancar).
    @Column(name = "uuid", length = 36, unique = true)
    var uuid: String? = null,

    // Sigla unica (2 a 8 letras o digitos): va delante del usuario de cada cuenta, p. ej. "hcyo.jperez".
    @Column(name = "sigla", length = 8, unique = true)
    var sigla: String? = null,

    // Una empresa inactiva ya no se ofrece al crear cuentas (las que ya tiene siguen funcionando).
    @Column(name = "activa", nullable = false)
    var activa: Boolean = true,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime? = null,

    @Column(name = "fecha_actualizacion", nullable = false)
    var fechaActualizacion: LocalDateTime? = null
)
