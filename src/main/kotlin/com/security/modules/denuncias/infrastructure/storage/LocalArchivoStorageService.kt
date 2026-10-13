package com.security.modules.denuncias.infrastructure.storage

import com.security.modules.denuncias.application.port.ArchivoDescargado
import com.security.modules.denuncias.application.port.ArchivoGuardado
import com.security.modules.denuncias.application.port.ArchivoStorageService
import com.security.modules.denuncias.domain.enums.ProveedorAlmacenamientoArchivo
import com.security.modules.denuncias.infrastructure.config.ArchivoStorageProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@Service
@ConditionalOnProperty(
    prefix = "storage",
    name = ["provider"],
    havingValue = "local",
    matchIfMissing = true
)
class LocalArchivoStorageService(
    private val properties:
    ArchivoStorageProperties
) : ArchivoStorageService {

    // =========================================================
    // GUARDAR
    // =========================================================

    override fun guardar(
        rutaArchivo: String,
        contenido: ByteArray,
        tipoContenido: String
    ): ArchivoGuardado {

        val destino =
            resolverRutaSegura(
                rutaArchivo
            )

        try {

            Files.createDirectories(
                destino.parent
            )

            Files.write(
                destino,
                contenido,
                StandardOpenOption.CREATE_NEW
            )

        } catch (exception: Exception) {

            throw IllegalStateException(
                "No fue posible almacenar el documento.",
                exception
            )
        }

        return ArchivoGuardado(
            rutaArchivo =
                normalizarRutaLogica(
                    rutaArchivo
                ),

            proveedor =
                ProveedorAlmacenamientoArchivo.LOCAL,

            bucket = null
        )
    }

    // =========================================================
    // OBTENER
    // =========================================================

    override fun obtener(
        rutaArchivo: String
    ): ArchivoDescargado {

        val archivo =
            resolverRutaSegura(
                rutaArchivo
            )

        if (
            !Files.exists(archivo) ||
            !Files.isRegularFile(archivo)
        ) {
            throw IllegalStateException(
                "El documento solicitado no existe."
            )
        }

        try {

            return ArchivoDescargado(
                contenido =
                    Files.readAllBytes(
                        archivo
                    ),

                tipoContenido =
                    Files.probeContentType(
                        archivo
                    )
            )

        } catch (exception: Exception) {

            throw IllegalStateException(
                "No fue posible recuperar el documento.",
                exception
            )
        }
    }

    // =========================================================
    // ELIMINAR
    // =========================================================

    override fun eliminar(
        rutaArchivo: String
    ) {

        val archivo =
            resolverRutaSegura(
                rutaArchivo
            )

        try {

            Files.deleteIfExists(
                archivo
            )

        } catch (exception: Exception) {

            throw IllegalStateException(
                "No fue posible eliminar el documento.",
                exception
            )
        }
    }

    // =========================================================
    // RUTA SEGURA
    // =========================================================

    private fun resolverRutaSegura(
        rutaArchivo: String
    ): Path {

        val basePath =
            Paths.get(
                properties.local.basePath
            )
                .toAbsolutePath()
                .normalize()

        val destino =
            basePath
                .resolve(
                    rutaArchivo
                )
                .normalize()

        /*
         * Evita rutas manipuladas como:
         *
         * ../../otro-archivo
         */
        if (
            !destino.startsWith(
                basePath
            )
        ) {
            throw IllegalArgumentException(
                "La ruta del documento no es válida."
            )
        }

        return destino
    }

    // =========================================================
    // NORMALIZAR OBJECT KEY
    // =========================================================

    private fun normalizarRutaLogica(
        rutaArchivo: String
    ): String {

        return rutaArchivo
            .replace(
                "\\",
                "/"
            )
    }
}