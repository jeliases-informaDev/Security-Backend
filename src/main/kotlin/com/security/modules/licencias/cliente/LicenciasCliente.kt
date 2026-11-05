package com.security.modules.licencias.cliente

import com.security.modules.licencias.dominio.ConsumoRemoto
import com.security.modules.licencias.dominio.CupoRemoto
import com.security.modules.licencias.dominio.LicenciaRemota

/**
 * Llamadas al servicio común de licencias de Sulotec. Solo arma las peticiones y lee las respuestas: quien lo usa
 * (LicenciaService) decide qué hacer si falla. El 404 de una empresa sin PLAFT es una respuesta, no un fallo;
 * todo lo demás que no sea una respuesta válida se lanza como LicenciasNoDisponibleException.
 */
interface LicenciasCliente {

    /** La licencia de la empresa; estado SIN_LICENCIA si el servicio responde 404 { error: SIN_LICENCIA }. */
    fun obtenerLicencia(uuid: String): LicenciaRemota

    /** ¿Puede crear o reactivar una cuenta? sinLicencia = true si el servicio responde 404. */
    fun consultarCupo(uuid: String): CupoRemoto

    /** Avisa cuántas cuentas activas tiene la empresa; responde igual que consultarCupo. */
    fun reportarActivos(uuid: String, activos: Long): CupoRemoto

    /** Pide permiso y descuenta; la misma clave repetida no descuenta dos veces. motivo SIN_LICENCIA y sinLicencia = true si 404. */
    fun consumir(
        uuid: String,
        clave: String,
        tipo: String,
        cantidad: Int,
        usuario: String,
        hechoPorSulotec: Boolean,
        detalle: String?
    ): ConsumoRemoto

    /** Devuelve los tokens de un consumo; si el servicio no lo conoce (404) se da por anulado. */
    fun anular(clave: String)
}

/**
 * El servicio de licencias no dio una respuesta válida: red, tiempo agotado, 5xx, 401 (token mal puesto),
 * otro 4xx inesperado o un cuerpo ilegible. status es el HTTP que respondió, o null si ni respondió.
 */
class LicenciasNoDisponibleException(
    val codigo: String,
    mensaje: String,
    val status: Int? = null
) : RuntimeException(mensaje)
