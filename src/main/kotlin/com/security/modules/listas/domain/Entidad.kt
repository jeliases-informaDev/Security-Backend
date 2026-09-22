package com.security.modules.listas.domain

import com.security.shared.datos.entities.Pais
import com.security.shared.datos.entities.TipoDocumento
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "entidades")
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
