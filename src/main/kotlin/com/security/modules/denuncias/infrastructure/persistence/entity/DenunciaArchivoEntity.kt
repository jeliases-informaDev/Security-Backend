package com.security.modules.denuncias.infrastructure.persistence.entity

import com.security.modules.denuncias.domain.enums.ProveedorAlmacenamientoArchivo
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "denuncia_archivos")
open class DenunciaArchivoEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    // =========================================================
    // DENUNCIA
    // =========================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "denuncia_id",
        nullable = false
    )
    var denuncia: DenunciaEntity,

    // =========================================================
    // ARCHIVO
    // =========================================================

    @Column(
        name = "nombre_original",
        nullable = false,
        length = 255
    )
    var nombreOriginal: String,

    /**
     * Nombre interno generado por el sistema.
     *
     * Ejemplo:
     * 72f3...-evidencia.pdf
     */
    @Column(
        name = "nombre_archivo",
        nullable = false,
        length = 255
    )
    var nombreArchivo: String,

    @Column(
        name = "tipo_contenido",
        length = 150
    )
    var tipoContenido: String? = null,

    /**
     * Para LOCAL:
     * denuncias/DEN-XXXX/documento.pdf
     *
     * Para ORACLE:
     * objectName / objectKey dentro del bucket.
     *
     * No se guarda una URL pública.
     */
    @Column(
        name = "ruta_archivo",
        nullable = false,
        length = 1024
    )
    var rutaArchivo: String,

    @Column(
        name = "tamano_bytes"
    )
    var tamanoBytes: Long? = null,

    /**
     * Permite saber dónde se encuentra físicamente
     * el documento.
     *
     * Nullable para mantener compatibilidad con
     * registros antiguos durante desarrollo.
     */
    @Enumerated(EnumType.STRING)
    @Column(
        name = "proveedor_almacenamiento",
        length = 20
    )
    var proveedorAlmacenamiento:
    ProveedorAlmacenamientoArchivo? = null,

    /**
     * Solo será utilizado por proveedores que
     * trabajen con buckets, como Oracle.
     */
    @Column(
        name = "bucket",
        length = 255
    )
    var bucket: String? = null,

    /**
     * Hash SHA-256 del contenido.
     *
     * Sirve para integridad y trazabilidad del
     * documento sin almacenar el documento en MySQL.
     */
    @Column(
        name = "sha256",
        length = 64
    )
    var sha256: String? = null,

    // =========================================================
    // FECHA
    // =========================================================

    @Column(
        name = "fecha_creacion",
        nullable = false
    )
    var fechaCreacion: LocalDateTime =
        LocalDateTime.now()
)