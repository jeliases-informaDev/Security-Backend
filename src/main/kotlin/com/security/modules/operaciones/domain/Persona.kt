package com.security.modules.operaciones.domain
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "personas_operacion")
class Persona {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "tipo_documento", length = 50)
    var tipoDocumento: String? = null

    @Column(name = "numero_documento", length = 50)
    var numeroDocumento: String? = null

    @Column(name = "nombres", length = 150)
    var nombres: String? = null

    @Column(name = "apellidos", length = 150)
    var apellidos: String? = null

    @Column(name = "fecha_nacimiento")
    var fechaNacimiento: LocalDate? = null

    @Column(name = "nacionalidad", length = 100)
    var nacionalidad: String? = null

    @Column(name = "telefono", length = 50)
    var telefono: String? = null

    @Column(name = "profesion_ocupacion", length = 150)
    var profesionOcupacion: String? = null

    @Column(name = "domicilio", columnDefinition = "TEXT")
    var domicilio: String? = null

    @Column(name = "codigo_postal", length = 20)
    var codigoPostal: String? = null

    @Column(name = "departamento", length = 100)
    var departamento: String? = null

    @Column(name = "provincia", length = 100)
    var provincia: String? = null

    @Column(name = "distrito", length = 100)
    var distrito: String? = null
}
