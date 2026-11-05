package com.security.modules.listas.domain

import com.security.shared.datos.entities.Usuario
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Una consulta a Listas Negativas como unidad (ver docs/LISTAS-NEGATIVAS.md).
 *
 * Hay dos clases de filas:
 *  - estado BUSQUEDA: una busqueda por nombre (o un documento repetido) que devolvio candidatos. Guarda lo que se
 *    escribio y es la que consumio el token; no aparece en el historial. Elegir un candidato crea una consulta
 *    RESUELTA que apunta a ella (id_busqueda) y no vuelve a cobrar.
 *  - estado RESUELTA: la consulta de una persona (o de nadie: sin coincidencias), con el resultado guardado tal como se
 *    calculo (columna resultado, JSON). Es lo que muestra el historial y el PDF.
 *
 * La empresa se guarda al crearla (empresa y empresa_uuid): una consulta nunca cambia de empresa aunque la cuenta cambie.
 * id_entidad no es una clave foranea a propósito: la base de listas se recarga y la consulta no debe impedirlo.
 */
@Entity
@Table(
    name = "consultas_listas",
    indexes = [
        Index(name = "idx_consultas_listas_empresa_fecha", columnList = "empresa,fecha"),
        Index(name = "idx_consultas_listas_usuario_fecha", columnList = "id_usuario,fecha"),
        Index(name = "idx_consultas_listas_entidad", columnList = "id_entidad,homonimo")
    ]
)
class ConsultaLista(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    var usuario: Usuario,

    @Column(name = "empresa", length = 200)
    var empresa: String? = null,

    @Column(name = "empresa_uuid", length = 64)
    var empresaUuid: String? = null,

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDateTime = LocalDateTime.now(),

    // BUSQUEDA o RESUELTA
    @Column(name = "estado", nullable = false, length = 20)
    var estado: String = ESTADO_RESUELTA,

    // Busqueda de la que salio (cuando se eligio un candidato)
    @Column(name = "id_busqueda")
    var idBusqueda: Long? = null,

    // CLIENTE, PROVEEDOR o TRABAJADOR_DIRECTOR
    @Column(name = "tipo_consulta", nullable = false, length = 30)
    var tipoConsulta: String,

    // ---- Lo que se escribio
    @Column(name = "criterio_tipo_documento", length = 20)
    var criterioTipoDocumento: String? = null,

    @Column(name = "criterio_documento", length = 50)
    var criterioDocumento: String? = null,

    @Column(name = "criterio_nombres", length = 200)
    var criterioNombres: String? = null,

    @Column(name = "criterio_apellido_paterno", length = 120)
    var criterioApellidoPaterno: String? = null,

    @Column(name = "criterio_apellido_materno", length = 120)
    var criterioApellidoMaterno: String? = null,

    // Todo lo escrito en una linea (para buscar en el historial)
    @Column(name = "criterio_texto", length = 500)
    var criterioTexto: String? = null,

    // POR_DOCUMENTO o POR_NOMBRE
    @Column(name = "modo", nullable = false, length = 20)
    var modo: String,

    @Column(name = "total_candidatos")
    var totalCandidatos: Long? = null,

    // ---- La persona resultante (null en las consultas sin resultados)
    @Column(name = "id_entidad")
    var idEntidad: Int? = null,

    // EXACTA_DOCUMENTO, POR_NOMBRE o SIN_RESULTADOS
    @Column(name = "coincidencia", length = 30)
    var coincidencia: String? = null,

    @Column(name = "consultado_nombre", length = 300)
    var consultadoNombre: String? = null,

    @Column(name = "consultado_tipo_documento", length = 30)
    var consultadoTipoDocumento: String? = null,

    @Column(name = "consultado_documento", length = 50)
    var consultadoDocumento: String? = null,

    @Column(name = "tipo_persona", length = 20)
    var tipoPersona: String? = null,

    @Column(name = "consultado_pais", length = 120)
    var consultadoPais: String? = null,

    // ---- Resultado (copia de lo que hay en el JSON, para filtrar y contar en la base)
    @Column(name = "porcentaje")
    var porcentaje: Int? = null,

    @Column(name = "clasificacion", length = 30)
    var clasificacion: String? = null,

    @Column(name = "listas_consultadas")
    var listasConsultadas: Int? = null,

    @Column(name = "listas_con_coincidencia")
    var listasConCoincidencia: Int? = null,

    @Column(name = "registros_en_listas")
    var registrosEnListas: Int? = null,

    @Column(name = "noticias_en_medios")
    var noticiasEnMedios: Int? = null,

    @Column(name = "es_pep")
    var esPep: Boolean? = null,

    // Nombres de las filas con coincidencia, separados por " | "
    @Column(name = "listas_coincidencia_texto", columnDefinition = "TEXT")
    var listasCoincidenciaTexto: String? = null,

    // El resultado completo tal como se calculo (ResultadoListas en JSON)
    @Column(name = "resultado", columnDefinition = "LONGTEXT")
    var resultado: String? = null,

    // ---- Decision "Es homonimo" vigente (el registro completo esta en consultas_listas_decision)
    @Column(name = "homonimo", nullable = false)
    var homonimo: Boolean = false,

    @Column(name = "homonimo_comentario", columnDefinition = "TEXT")
    var homonimoComentario: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homonimo_por")
    var homonimoPor: Usuario? = null,

    @Column(name = "homonimo_fecha")
    var homonimoFecha: LocalDateTime? = null
) {
    companion object {
        const val ESTADO_BUSQUEDA = "BUSQUEDA"
        const val ESTADO_RESUELTA = "RESUELTA"

        const val MODO_DOCUMENTO = "POR_DOCUMENTO"
        const val MODO_NOMBRE = "POR_NOMBRE"

        const val COINCIDENCIA_DOCUMENTO = "EXACTA_DOCUMENTO"
        const val COINCIDENCIA_NOMBRE = "POR_NOMBRE"
        const val COINCIDENCIA_SIN_RESULTADOS = "SIN_RESULTADOS"
    }
}

/** Registro de cada vez que alguien marco o deshizo "Es homonimo" en una consulta. */
@Entity
@Table(name = "consultas_listas_decision", indexes = [Index(name = "idx_consultas_listas_decision_consulta", columnList = "id_consulta")])
class ConsultaListaDecision(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "id_consulta", nullable = false)
    var idConsulta: Long,

    // HOMONIMO o DESHACER_HOMONIMO
    @Column(name = "accion", nullable = false, length = 30)
    var accion: String,

    @Column(name = "comentario", columnDefinition = "TEXT")
    var comentario: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    var usuario: Usuario,

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        const val ACCION_HOMONIMO = "HOMONIMO"
        const val ACCION_DESHACER = "DESHACER_HOMONIMO"
    }
}
