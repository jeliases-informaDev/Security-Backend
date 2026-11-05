package com.security.modules.licencias.cliente

import com.security.modules.licencias.config.LicenciasProperties
import com.security.modules.licencias.dominio.ActivosSolicitud
import com.security.modules.licencias.dominio.ConsumoRemoto
import com.security.modules.licencias.dominio.ConsumoSolicitud
import com.security.modules.licencias.dominio.CupoRemoto
import com.security.modules.licencias.dominio.ESTADO_SIN_LICENCIA
import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.modules.licencias.dominio.MOTIVO_SIN_LICENCIA
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration

/**
 * Cliente HTTP del servicio común de licencias (Authorization: Bearer LICENCIAS_TOKEN, todo en JSON).
 * No traduce los fallos a errores para la web: los lanza como LicenciasNoDisponibleException y LicenciaService
 * sigue con la última licencia conocida, porque a un cliente nunca se le bloquea por una caída de Sulotec.
 */
@Component
class LicenciasClienteHttp(private val properties: LicenciasProperties) : LicenciasCliente {

    // Se arma al primer uso: con las licencias apagadas (URL vacía) nunca se construye.
    private val cliente: RestClient by lazy { construir() }

    private fun construir(): RestClient {
        val espera = Duration.ofSeconds(properties.timeoutSegundos)
        val httpClient = HttpClient.newBuilder()
            // Sin intentar subir a HTTP/2 (Upgrade: h2c): algunos servidores Node cortan la conexión al ver ese encabezado.
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(espera)
            .build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(espera)
        }
        return RestClient.builder()
            .baseUrl(properties.urlBase)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.token.trim()}")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    override fun obtenerLicencia(uuid: String): LicenciaRemota {
        val licencia = llamar(
            operacion = "licencia",
            // Solo el 404 que dice SIN_LICENCIA es "no tiene PLAFT"; otro 404 (p. ej. una URL mal puesta) es un fallo.
            si404 = { cuerpo -> if (cuerpo.contains(ESTADO_SIN_LICENCIA)) LicenciaRemota.SIN_LICENCIA else null }
        ) {
            cliente.get()
                .uri("/v1/licencias/{uuid}", uuid)
                .retrieve()
                .body(LicenciaRemota::class.java)
        }
        if (licencia.estado.isNullOrBlank()) {
            throw LicenciasNoDisponibleException("RESPUESTA_INVALIDA", "La licencia llegó sin estado")
        }
        return licencia
    }

    override fun consultarCupo(uuid: String): CupoRemoto =
        llamar(operacion = "cupo", si404 = { CupoRemoto(estado = ESTADO_SIN_LICENCIA, puedeCrear = true, sinLicencia = true) }) {
            cliente.get()
                .uri("/v1/licencias/{uuid}/cupo", uuid)
                .retrieve()
                .body(CupoRemoto::class.java)
        }

    override fun reportarActivos(uuid: String, activos: Long): CupoRemoto =
        llamar(operacion = "usuarios-activos", si404 = { CupoRemoto(estado = ESTADO_SIN_LICENCIA, sinLicencia = true) }) {
            cliente.put()
                .uri("/v1/licencias/{uuid}/usuarios-activos", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ActivosSolicitud(activos))
                .retrieve()
                .body(CupoRemoto::class.java)
        }

    override fun consumir(
        uuid: String,
        clave: String,
        tipo: String,
        cantidad: Int,
        usuario: String,
        hechoPorSulotec: Boolean,
        detalle: String?
    ): ConsumoRemoto {
        val respuesta = llamar(
            operacion = "consumo",
            si404 = { ConsumoRemoto(permitido = false, motivo = MOTIVO_SIN_LICENCIA, sinLicencia = true) }
        ) {
            cliente.post()
                .uri("/v1/licencias/{uuid}/consumos", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ConsumoSolicitud(clave, tipo, cantidad, usuario, hechoPorSulotec, detalle))
                .retrieve()
                .body(ConsumoRemoto::class.java)
        }
        if (respuesta.permitido == null) {
            throw LicenciasNoDisponibleException("RESPUESTA_INVALIDA", "El consumo llegó sin «permitido»")
        }
        return respuesta
    }

    override fun anular(clave: String) {
        // Un 404 es que el servicio no tiene ese consumo: no hay nada que devolver.
        llamar(operacion = "anulación", si404 = { true }) {
            cliente.delete()
                .uri("/v1/consumos/{clave}", clave)
                .retrieve()
                .toBodilessEntity()
            true
        }
    }

    /**
     * Ejecuta una llamada y convierte todo lo que no sea una respuesta válida en LicenciasNoDisponibleException.
     * si404 recibe el cuerpo del 404 y devuelve la respuesta que corresponde, o null si ese 404 también es un fallo.
     */
    private fun <T : Any> llamar(operacion: String, si404: ((String) -> T?)? = null, bloque: () -> T?): T {
        try {
            return bloque()
                ?: throw LicenciasNoDisponibleException("RESPUESTA_VACIA", "El servicio de licencias respondió vacío en $operacion")
        } catch (e: LicenciasNoDisponibleException) {
            throw e
        } catch (e: RestClientResponseException) {
            val status = e.statusCode.value()
            if (status == 404 && si404 != null) {
                si404(e.responseBodyAsString)?.let { return it }
            }
            val codigo = if (status == 401) "TOKEN_INVALIDO" else "HTTP_$status"
            throw LicenciasNoDisponibleException(codigo, "El servicio de licencias respondió $status en $operacion", status)
        } catch (e: ResourceAccessException) {
            throw LicenciasNoDisponibleException("RED", "No se pudo conectar con el servicio de licencias en $operacion: ${e.message}")
        } catch (e: RestClientException) {
            // Respuesta 2xx que no se pudo leer (no es JSON o no tiene la forma esperada).
            throw LicenciasNoDisponibleException("RESPUESTA_INVALIDA", "Respuesta ilegible del servicio de licencias en $operacion: ${e.message}")
        } catch (e: RuntimeException) {
            throw LicenciasNoDisponibleException("ERROR", "Fallo inesperado al llamar al servicio de licencias en $operacion: ${e.message}")
        }
    }
}
