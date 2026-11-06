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

    // ---- Campos de la entrega A del diseño nuevo: todos opcionales (los registros anteriores no los tienen) ----

    @Column(name = "motivo_sin_documento", length = 300)
    var motivoSinDocumento: String? = null

    @Column(name = "estado_civil", length = 30)
    var estadoCivil: String? = null

    // Solo la persona representada: NATURAL o JURIDICA (null = registro anterior, se trata como NATURAL).
    @Column(name = "tipo_persona", length = 10)
    var tipoPersona: String? = null

    @Column(name = "razon_social", length = 255)
    var razonSocial: String? = null

    @Column(name = "representante_legal", length = 255)
    var representanteLegal: String? = null

    @Column(name = "representante_tipo_documento", length = 50)
    var representanteTipoDocumento: String? = null

    @Column(name = "representante_numero_documento", length = 50)
    var representanteNumeroDocumento: String? = null

    @Column(name = "accionistas_mayores_25", columnDefinition = "TEXT")
    var accionistasMayores25: String? = null

    @Column(name = "domicilio_fiscal", columnDefinition = "TEXT")
    var domicilioFiscal: String? = null
}
