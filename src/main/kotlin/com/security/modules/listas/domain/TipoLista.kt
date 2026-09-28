package com.security.modules.listas.domain

import jakarta.persistence.*

@Entity
@Table(name = "tipo_lista")
class TipoLista(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "codigo", unique = true, length = 50)
    var codigo: String? = null,

    @Column(name = "nombre")
    var nombre: String? = null,

    @Column(name = "descripcion", columnDefinition = "TEXT")
    var descripcion: String? = null,

    @Column(name = "fuente")
    var fuente: String? = null,

    // NACIONAL / INTERNACIONAL / NACIONAL E INTERNACIONAL, segun el alcance real de la lista
    @Column(name = "alcance", length = 40)
    var alcance: String? = null,

    @Column(name = "periodicidad", length = 30)
    var periodicidad: String? = null,

    // true para las listas que son especificamente de Personas Expuestas Politicamente
    // (reemplaza la comparacion por codigo == "PEP", que ya no aplica con el catalogo real)
    @Column(name = "es_pep")
    var esPep: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_grupo")
    var grupo: ListaGrupo? = null
)
