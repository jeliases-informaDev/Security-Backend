package com.security.modules.listas.domain

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "historial_manchas")
class HistorialMancha(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidades")
    var entidad: Entidad,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_lista")
    var tipoLista: TipoLista,

    @Column(name = "descripcion", columnDefinition = "TEXT")
    var descripcion: String? = null,

    @Column(name = "link", columnDefinition = "TEXT")
    var link: String? = null,

    // Fecha desde la que rige el registro (p.ej. fecha de inhabilitacion, o inicio del cargo PEP)
    @Column(name = "fecha_registro")
    var fechaRegistro: LocalDate? = null,

    // Fecha hasta la que rige el registro, cuando aplica (viene como "Fecha Hasta" en los Excel de origen)
    @Column(name = "fecha_hasta")
    var fechaHasta: LocalDate? = null,

    // Campos especificos de PEP (Personas Expuestas Politicamente); null para los demas tipos de lista
    @Column(name = "institucion")
    var institucion: String? = null,

    @Column(name = "cargo")
    var cargo: String? = null,

    @Column(name = "tipo_pep", length = 50)
    var tipoPep: String? = null,

    // Texto libre, no fecha estricta: el origen trae valores como "NO INDICA"
    @Column(name = "periodo_desde", length = 50)
    var periodoDesde: String? = null,

    @Column(name = "periodo_hasta", length = 50)
    var periodoHasta: String? = null
)
