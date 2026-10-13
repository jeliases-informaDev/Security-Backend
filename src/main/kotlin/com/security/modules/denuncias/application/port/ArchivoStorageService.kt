package com.security.modules.denuncias.application.port

import com.security.modules.denuncias.domain.enums.ProveedorAlmacenamientoArchivo

interface ArchivoStorageService {

    fun guardar(
        rutaArchivo: String,
        contenido: ByteArray,
        tipoContenido: String
    ): ArchivoGuardado

    fun obtener(
        rutaArchivo: String
    ): ArchivoDescargado

    fun eliminar(
        rutaArchivo: String
    )
}


data class ArchivoGuardado(

    val rutaArchivo: String,

    val proveedor:
    ProveedorAlmacenamientoArchivo,

    val bucket: String? = null
)


data class ArchivoDescargado(

    val contenido: ByteArray,

    val tipoContenido: String?
)