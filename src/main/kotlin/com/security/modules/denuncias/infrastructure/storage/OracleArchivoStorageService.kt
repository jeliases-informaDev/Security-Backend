package com.security.modules.denuncias.infrastructure.storage

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.requests.GetObjectRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.security.modules.denuncias.application.port.ArchivoDescargado
import com.security.modules.denuncias.application.port.ArchivoGuardado
import com.security.modules.denuncias.application.port.ArchivoStorageService
import com.security.modules.denuncias.domain.enums.ProveedorAlmacenamientoArchivo
import com.security.modules.denuncias.infrastructure.config.ArchivoStorageProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream


@Service
@ConditionalOnProperty(
    prefix = "storage",
    name = ["provider"],
    havingValue = "oracle"
)
class OracleArchivoStorageService(

    private val properties:
    ArchivoStorageProperties

) : ArchivoStorageService {


    // =========================================================
    // CLIENTE OCI
    // =========================================================

    private val client:
            ObjectStorageClient by lazy {

        validarConfiguracion()

        val provider =
            crearAuthenticationProvider()

        val objectStorageClient =
            ObjectStorageClient
                .builder()
                .build(
                    provider
                )

        objectStorageClient
            .setRegion(
                properties.oracle.region
            )

        objectStorageClient
    }


    // =========================================================
    // GUARDAR
    // =========================================================

    override fun guardar(
        rutaArchivo: String,
        contenido: ByteArray,
        tipoContenido: String
    ): ArchivoGuardado {

        val objectName =
            normalizarObjectName(
                rutaArchivo
            )

        try {

            val request =
                PutObjectRequest
                    .builder()
                    .namespaceName(
                        properties.oracle.namespace
                    )
                    .bucketName(
                        properties.oracle.bucket
                    )
                    .objectName(
                        objectName
                    )
                    .contentType(
                        tipoContenido
                    )
                    .contentLength(
                        contenido.size.toLong()
                    )
                    .putObjectBody(
                        ByteArrayInputStream(
                            contenido
                        )
                    )
                    .build()

            client.putObject(
                request
            )

        } catch (exception: Exception) {

            throw IllegalStateException(
                "No fue posible almacenar el documento en Oracle Object Storage.",
                exception
            )
        }

        return ArchivoGuardado(

            rutaArchivo =
                objectName,

            proveedor =
                ProveedorAlmacenamientoArchivo.ORACLE,

            bucket =
                properties.oracle.bucket
        )
    }


    // =========================================================
    // OBTENER
    // =========================================================

    override fun obtener(
        rutaArchivo: String
    ): ArchivoDescargado {

        val objectName =
            normalizarObjectName(
                rutaArchivo
            )

        try {

            val request =
                GetObjectRequest
                    .builder()
                    .namespaceName(
                        properties.oracle.namespace
                    )
                    .bucketName(
                        properties.oracle.bucket
                    )
                    .objectName(
                        objectName
                    )
                    .build()

            val response =
                client.getObject(
                    request
                )

            val contenido =
                response
                    .inputStream
                    .use {
                        it.readBytes()
                    }

            return ArchivoDescargado(

                contenido =
                    contenido,

                tipoContenido =
                    response.contentType
            )

        } catch (exception: Exception) {

            throw IllegalStateException(
                "No fue posible recuperar el documento desde Oracle Object Storage.",
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

        val objectName =
            normalizarObjectName(
                rutaArchivo
            )

        try {

            val request =
                DeleteObjectRequest
                    .builder()
                    .namespaceName(
                        properties.oracle.namespace
                    )
                    .bucketName(
                        properties.oracle.bucket
                    )
                    .objectName(
                        objectName
                    )
                    .build()

            client.deleteObject(
                request
            )

        } catch (exception: Exception) {

            throw IllegalStateException(
                "No fue posible eliminar el documento de Oracle Object Storage.",
                exception
            )
        }
    }


    // =========================================================
    // AUTH PROVIDER
    // =========================================================

    private fun crearAuthenticationProvider():
            AbstractAuthenticationDetailsProvider {

        return when (
            properties.oracle.authMode
                .trim()
                .uppercase()
        ) {

            "CONFIG_FILE" ->
                crearConfigFileProvider()

            "INSTANCE_PRINCIPAL" ->
                InstancePrincipalsAuthenticationDetailsProvider
                    .builder()
                    .build()

            else ->
                throw IllegalStateException(
                    "OCI_AUTH_MODE no es válido. Usa CONFIG_FILE o INSTANCE_PRINCIPAL."
                )
        }
    }


    // =========================================================
    // CONFIG FILE
    // =========================================================

    private fun crearConfigFileProvider():
            AbstractAuthenticationDetailsProvider {

        val configFile =
            properties.oracle.configFile
                .trim()

        val profile =
            properties.oracle.profile
                .trim()
                .ifBlank {
                    "DEFAULT"
                }

        return if (
            configFile.isBlank()
        ) {

            /*
             * Usa la ubicación estándar:
             *
             * ~/.oci/config
             */
            ConfigFileAuthenticationDetailsProvider(
                profile
            )

        } else {

            ConfigFileAuthenticationDetailsProvider(
                configFile,
                profile
            )
        }
    }


    // =========================================================
    // VALIDAR CONFIGURACIÓN
    // =========================================================

    private fun validarConfiguracion() {

        if (
            properties.oracle.region
                .isBlank()
        ) {
            throw IllegalStateException(
                "OCI_REGION no está configurado."
            )
        }

        if (
            properties.oracle.namespace
                .isBlank()
        ) {
            throw IllegalStateException(
                "OCI_NAMESPACE no está configurado."
            )
        }

        if (
            properties.oracle.bucket
                .isBlank()
        ) {
            throw IllegalStateException(
                "OCI_BUCKET no está configurado."
            )
        }
    }


    // =========================================================
    // OBJECT NAME
    // =========================================================

    private fun normalizarObjectName(
        rutaArchivo: String
    ): String {

        val resultado =
            rutaArchivo
                .replace(
                    "\\",
                    "/"
                )
                .trim()
                .trimStart('/')

        if (
            resultado.isBlank()
        ) {
            throw IllegalArgumentException(
                "La ruta del documento no es válida."
            )
        }

        if (
            resultado.contains("../") ||
            resultado.contains("/..")
        ) {
            throw IllegalArgumentException(
                "La ruta del documento no es válida."
            )
        }

        return resultado
    }
}