package com.security.modules.listas.domain

import jakarta.persistence.*

/*
 * Reglas del "Detalle por lista" como datos (ver docs/LISTAS-NEGATIVAS.md). Se siembran solas al arrancar desde
 * src/main/resources/listas/reglas-listas.json solo cuando faltan: lo que alguien cambie a mano en estas tablas se respeta.
 */

/** Grupo de peso: Criticas 15 %, Altas 10 %, Informativas 5 %, Sin peso 0 %. */
@Entity
@Table(name = "listas_grupo_peso")
class ListaGrupoPeso(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    var codigo: String,

    @Column(name = "nombre", nullable = false, length = 60)
    var nombre: String,

    @Column(name = "peso", nullable = false)
    var peso: Int = 0,

    @Column(name = "orden", nullable = false)
    var orden: Int = 0,

    // true = una coincidencia en una fila de este grupo hace la consulta "Coincidencia critica"
    @Column(name = "critica", nullable = false)
    var critica: Boolean = false
)

/** Una fila del "Detalle por lista": su grupo de peso, si se consulta o esta "Pronto" y las listas que la alimentan. */
@Entity
@Table(name = "listas_regla")
class ListaRegla(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "codigo", nullable = false, unique = true, length = 40)
    var codigo: String,

    @Column(name = "nombre", nullable = false, length = 120)
    var nombre: String,

    @Column(name = "subtitulo", length = 120)
    var subtitulo: String? = null,

    @Column(name = "orden", nullable = false)
    var orden: Int = 0,

    // Chip de "Fuentes consultadas" al que pertenece (PEP, SUNAT, ONU, OFAC, UE, GAFI, ASBANC, NOTICIAS)
    @Column(name = "fuente", nullable = false, length = 30)
    var fuente: String,

    @Column(name = "fuente_nombre", nullable = false, length = 60)
    var fuenteNombre: String,

    // Codigo de listas_grupo_peso (el peso sale de ahi)
    @Column(name = "grupo_peso", nullable = false, length = 30)
    var grupoPeso: String,

    // CONSULTABLE o PRONTO
    @Column(name = "estado", nullable = false, length = 20)
    var estado: String = ESTADO_CONSULTABLE,

    @Column(name = "motivo_pronto", columnDefinition = "TEXT")
    var motivoPronto: String? = null,

    // true = la alimentan todas las listas marcadas es_pep
    @Column(name = "todas_pep", nullable = false)
    var todasPep: Boolean = false,

    // REGISTROS, NOTAS o NOTICIAS (NOTICIAS = cuenta como "noticias en medios")
    @Column(name = "tipo_conteo", nullable = false, length = 20)
    var tipoConteo: String = "REGISTROS",

    @Column(name = "etiqueta_aviso", nullable = false, length = 80)
    var etiquetaAviso: String,

    @Column(name = "etiqueta_suma", nullable = false, length = 80)
    var etiquetaSuma: String,

    // Nombres de las listas (tipo_lista.nombre) que alimentan la fila; se comparan sin mayusculas ni tildes
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "listas_regla_origen", joinColumns = [JoinColumn(name = "id_regla")])
    @Column(name = "nombre_lista", nullable = false, length = 300)
    var listas: MutableList<String> = mutableListOf()
) {
    companion object {
        const val ESTADO_CONSULTABLE = "CONSULTABLE"
        const val ESTADO_PRONTO = "PRONTO"
    }
}

/** Otras listas: el grupo de colorimetria (lista_grupo.orden, 1 a 6) define su grupo de peso. */
@Entity
@Table(name = "listas_regla_otras")
class ListaReglaOtras(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,

    @Column(name = "orden_grupo", nullable = false, unique = true)
    var ordenGrupo: Int,

    @Column(name = "grupo_peso", nullable = false, length = 30)
    var grupoPeso: String
)
