package com.security.modules.denuncias.infrastructure.persistence.entity

import com.security.modules.denuncias.domain.enums.CausaDenuncia
import com.security.modules.denuncias.domain.enums.EstadoDenuncia
import com.security.modules.denuncias.domain.enums.RelacionEmpresa
import com.security.shared.datos.entities.Usuario
import com.security.modules.denuncias.domain.enums.ResultadoCierreDenuncia
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "denuncias")
class DenunciaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    // -------------------------
    // DATOS DE LA DENUNCIA
    // -------------------------

    // Texto y no ENUM de MySQL: asi agregar una situacion no exige cambiar la columna (ver EsquemaDenunciasInicializador).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(
        name = "causa",
        nullable = false,
        length = 50
    )
    var causa: CausaDenuncia,

    @Enumerated(EnumType.STRING)
    @Column(
        name = "relacion_empresa",
        nullable = false,
        length = 50
    )
    var relacionEmpresa: RelacionEmpresa,

    @Column(
        name = "descripcion",
        nullable = false,
        columnDefinition = "TEXT"
    )
    var descripcion: String,

    // -------------------------
    // SEGUIMIENTO
    // -------------------------

    /*
     * Código que podrá utilizar el denunciante
     * para consultar posteriormente su denuncia.
     *
     * Ejemplo:
     * DEN-K8P4X2
     *
     * Por ahora es nullable porque existen
     * denuncias anteriores a esta funcionalidad.
     */
    @Column(
        name = "codigo_seguimiento",
        length = 30,
        unique = true,
        updatable = false
    )
    var codigoSeguimiento: String? = null,

    /*
     * Nunca almacenamos el PIN original.
     *
     * Aquí solamente guardaremos el hash
     * generado con BCrypt.
     */
    @Column(
        name = "pin_hash",
        length = 100,
        updatable = false
    )
    var pinHash: String? = null,

    // -------------------------
    // RECEPTOR
    // -------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receptor_usuario_id")
    var receptorUsuario: Usuario? = null,

    @Column(
        name = "receptor_propuesto",
        length = 255
    )
    var receptorPropuesto: String? = null,

    // -------------------------
    // DATOS DEL DENUNCIANTE
    // -------------------------

    @Column(
        name = "anonima",
        nullable = false
    )
    var anonima: Boolean = false,

    @Column(
        name = "nombre_denunciante",
        length = 255
    )
    var nombreDenunciante: String? = null,

    @Column(
        name = "documento_denunciante",
        length = 50
    )
    var documentoDenunciante: String? = null,

    @Column(
        name = "telefono_denunciante",
        length = 50
    )
    var telefonoDenunciante: String? = null,

    @Column(
        name = "correo_denunciante",
        length = 255
    )
    var correoDenunciante: String? = null,

    @Column(
        name = "acepta_terminos",
        nullable = false
    )
    var aceptaTerminos: Boolean = false,

    // -------------------------
    // ESTADO
    // -------------------------

    @Enumerated(EnumType.STRING)
    @Column(
        name = "estado",
        nullable = false,
        length = 30
    )
    var estado: EstadoDenuncia = EstadoDenuncia.RECIBIDO,

    // -------------------------
    // USUARIO QUE REGISTRA
    // -------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_usuario_id")
    var creadoPorUsuario: Usuario? = null,

    // -------------------------
    // FECHAS
    // -------------------------

    @Column(
        name = "fecha_creacion",
        nullable = false
    )
    var fechaCreacion: LocalDateTime = LocalDateTime.now(),

    @Column(
        name = "fecha_actualizacion",
        nullable = false
    )
    var fechaActualizacion: LocalDateTime = LocalDateTime.now(),

    @Column(name = "fecha_cierre")
    var fechaCierre: LocalDateTime? = null,

    // -------------------------
    // RESULTADO DEL CIERRE (docs/DENUNCIAS-CIERRE.md); null en las cerradas antes de existir
    // -------------------------

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "resultado_cierre", length = 20)
    var resultadoCierre: ResultadoCierreDenuncia? = null,

    @Column(name = "motivo_cierre", length = 1000)
    var motivoCierre: String? = null,

    // -------------------------
    // DERIVACION AL OFICIAL DE CUMPLIMIENTO (reservada)
    // -------------------------

    @Column(name = "derivada_al_oficial", nullable = false)
    var derivadaAlOficial: Boolean = false,

    @Column(name = "fecha_derivacion")
    var fechaDerivacion: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "derivada_por_usuario_id")
    var derivadaPorUsuario: Usuario? = null,

    @Column(name = "motivo_derivacion", length = 500)
    var motivoDerivacion: String? = null
)