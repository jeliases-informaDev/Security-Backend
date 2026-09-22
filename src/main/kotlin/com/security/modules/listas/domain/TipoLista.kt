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
    var descripcion: String? = null
) {
    companion object {
        const val PEP = "PEP"
        const val ACTOS_ILICITOS = "ACTOS_ILICITOS"
        const val NOTICIAS = "NOTICIAS"
        const val INTERNACIONAL = "INTERNACIONAL"
    }
}
