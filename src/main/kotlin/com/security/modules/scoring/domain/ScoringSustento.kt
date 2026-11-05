package com.security.modules.scoring.domain

import com.security.shared.datos.entities.Usuario
import jakarta.persistence.*
import java.time.LocalDateTime

/** Archivo de sustento del origen de los fondos, guardado con el mismo almacenamiento del canal de denuncias. */
@Entity
@Table(name = "scoring_sustento")
class ScoringSustento(

    @Id
    @Column(name = "id", length = 36)
    var id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    var usuario: Usuario,

    @Column(name = "ruta_archivo", nullable = false, length = 300)
    var rutaArchivo: String,

    // LOCAL / ORACLE
    @Column(name = "proveedor", length = 20)
    var proveedor: String,

    @Column(name = "bucket", length = 150)
    var bucket: String? = null,

    @Column(name = "tipo_contenido", length = 100)
    var tipoContenido: String,

    @Column(name = "tamano_bytes")
    var tamanoBytes: Long,

    @Column(name = "fecha_creacion")
    var fechaCreacion: LocalDateTime = LocalDateTime.now()
)
