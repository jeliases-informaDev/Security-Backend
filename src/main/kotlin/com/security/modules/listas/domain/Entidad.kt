package com.security.modules.listas.domain

import com.security.shared.datos.entities.Pais
import com.security.shared.datos.entities.TipoDocumento
import jakarta.persistence.*
import java.time.LocalDate

@Entity
// El indice por documento tiene el mismo nombre y columnas que el que crea deploy/cargar-listas.sh (Sulotec-infra):
// asi Hibernate, al actualizar el esquema, lo reconoce y no crea uno repetido.
@Table(
    name = "entidades",
    indexes = [Index(name = "idx_entidades_documento", columnList = "documento,id_tipo_documento")]
)
class Entidad(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_documento", nullable = false)
    var tipoDocumento: TipoDocumento,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pais")
    var pais: Pais? = null,

    @Column(name = "documento", nullable = false, length = 50)
    var documento: String,

    @Column(name = "tipo_entidad", nullable = false, length = 50)
    var tipoEntidad: String,

    @Column(name = "fecha_registro")
    var fechaRegistro: LocalDate? = null,

    @Column(name = "departamento")
    var departamento: String? = null,

    @Column(name = "provincia")
    var provincia: String? = null,

    @Column(name = "distrito")
    var distrito: String? = null,

    @Column(name = "direccion")
    var direccion: String? = null,

    @Column(name = "tipo")
    var tipo: String? = null,

    @Column(name = "rubro")
    var rubro: String? = null,

    @Column(name = "alias")
    var alias: String? = null,

    @OneToOne(mappedBy = "entidad", cascade = [CascadeType.ALL], orphanRemoval = true)
    var personaNatural: PersonaNatural? = null,

    @OneToOne(mappedBy = "entidad", cascade = [CascadeType.ALL], orphanRemoval = true)
    var personaJuridica: PersonaJuridica? = null,

    @OneToMany(mappedBy = "entidad", cascade = [CascadeType.ALL], orphanRemoval = true)
    var manchas: MutableList<HistorialMancha> = mutableListOf()
) {
    companion object {
        const val TIPO_NATURAL = "NATURAL"
        const val TIPO_JURIDICA = "JURIDICA"
    }
}
