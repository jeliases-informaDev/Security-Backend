package com.security.modules.licencias.dominio

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// Estados que responde el servicio común. SIN_LICENCIA no lo manda como estado: es el 404 { error: SIN_LICENCIA }
// (la empresa no tiene PLAFT contratado) y aquí se representa con ese mismo nombre para no tratarlo aparte.
const val ESTADO_ACTIVA = "ACTIVA"
const val ESTADO_SOLO_LECTURA = "SOLO_LECTURA"
const val ESTADO_SUSPENDIDA = "SUSPENDIDA"
const val ESTADO_CANCELADA = "CANCELADA"
const val ESTADO_SIN_LICENCIA = "SIN_LICENCIA"

// Motivos de un consumo rechazado (permitido = false).
const val MOTIVO_BOLSA_AGOTADA = "BOLSA_AGOTADA"
const val MOTIVO_SOLO_LECTURA = "SOLO_LECTURA"
const val MOTIVO_SUSPENDIDA = "SUSPENDIDA"
const val MOTIVO_SIN_LICENCIA = "SIN_LICENCIA"

// Nivel de la bolsa de tokens: con "bloqueado" ya no queda ni la tolerancia.
const val NIVEL_BLOQUEADO = "bloqueado"

// Las acciones de PLAFT que se avisan a Sulotec. El costo de cada una NO se decide aquí: lo pone la tarifa de Sulotec
// (sulotec.com/licencias); PLAFT manda cantidad = 1 solo por compatibilidad y el servicio la ignora.
const val TIPO_BUSQUEDA_LISTAS = "busqueda_listas"
const val TIPO_LISTA_PDF = "lista_pdf"
const val TIPO_SCORING = "scoring"
const val TIPO_MATRIZ_REGISTRAR = "matriz_registrar"
const val TIPO_MATRIZ_PDF = "matriz_pdf"
const val TIPO_DENUNCIA_CREAR = "denuncia_crear"
const val TIPO_DENUNCIA_DOCUMENTOS = "denuncia_documentos"
const val TIPO_OPERACION_REGISTRAR = "operacion_registrar"
const val TIPO_OPERACION_PDF = "operacion_pdf"
const val TIPO_MENSAJE_INDIRA = "mensaje_indira"

/** Bolsa de tokens del periodo, tal como la responde el servicio (snake_case). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TokensRemotos(
    val bolsa: Long? = null,
    val extra: Long? = null,
    val capacidad: Long? = null,
    val usados: Long? = null,
    val disponibles: Long? = null,
    val porcentaje: Double? = null,
    val nivel: String? = null,
    val tolerancia: Long? = null,
    @JsonProperty("tolerancia_max") val toleranciaMax: Long? = null
)

/** Tarifa de Sulotec para una acción: cuántos tokens cuesta y si se bloquea cuando no queda saldo. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TarifaRemota(
    val tipo: String? = null,
    val nombre: String? = null,
    val tokens: Long? = null,
    val bloquea: Boolean? = null
)

/** Titular de la licencia (el dueño: entra a PLAFT con su cuenta Sulotec sin que nadie le cree el usuario). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TitularRemoto(
    val email: String? = null,
    val nombre: String? = null
)

/**
 * GET /v1/licencias/{uuid}. Con estado SIN_LICENCIA el resto llega vacío. titular, empresa_nombre y tarifas son
 * nuevos: un servicio viejo no los manda y llegan null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LicenciaRemota(
    val estado: String? = null,
    val plan: String? = null,
    @JsonProperty("usuarios_max") val usuariosMax: Long? = null,
    @JsonProperty("usuarios_activos") val usuariosActivos: Long? = null,
    @JsonProperty("puede_crear_usuarios") val puedeCrearUsuarios: Boolean? = null,
    val periodo: String? = null,
    val tokens: TokensRemotos? = null,
    val vencimiento: String? = null,
    @JsonProperty("dias_para_vencer") val diasParaVencer: Long? = null,
    val titular: TitularRemoto? = null,
    @JsonProperty("empresa_nombre") val empresaNombre: String? = null,
    val tarifas: List<TarifaRemota>? = null
) {
    fun esSinLicencia(): Boolean = estado == ESTADO_SIN_LICENCIA

    companion object {
        val SIN_LICENCIA = LicenciaRemota(estado = ESTADO_SIN_LICENCIA)
    }
}

/** GET /cupo y PUT /usuarios-activos. sinLicencia = true cuando el servicio respondió 404 (no hay cupo que exigir). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CupoRemoto(
    @JsonProperty("puede_crear") val puedeCrear: Boolean? = null,
    val activos: Long? = null,
    @JsonProperty("usuarios_max") val usuariosMax: Long? = null,
    val estado: String? = null,
    @JsonProperty("sin_licencia") val sinLicencia: Boolean = false
)

/**
 * POST /consumos. sinLicencia = true cuando respondió 404 (la empresa no tiene PLAFT: no se cobra ni se bloquea).
 * cobrado = tokens que descontó Sulotec según su tarifa (puede ser 0); sin_descuento = se permitió sin descontar.
 * Ambos son nuevos y llegan null con un servicio viejo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ConsumoRemoto(
    val permitido: Boolean? = null,
    val motivo: String? = null,
    val nivel: String? = null,
    val disponibles: Long? = null,
    val porcentaje: Double? = null,
    val capacidad: Long? = null,
    val usados: Long? = null,
    val periodo: String? = null,
    val repetido: Boolean? = null,
    val cobrado: Long? = null,
    @JsonProperty("sin_descuento") val sinDescuento: Boolean? = null,
    @JsonProperty("sin_licencia") val sinLicencia: Boolean = false
)

/** Cuerpo de POST /v1/licencias/{uuid}/consumos. */
data class ConsumoSolicitud(
    @JsonProperty("clave_idempotente") val claveIdempotente: String,
    val tipo: String,
    val cantidad: Int,
    val usuario: String,
    @JsonProperty("hecho_por_sulotec") val hechoPorSulotec: Boolean,
    val detalle: String?
)

/** Cuerpo de POST /v1/licencias/{uuid}/cuentas: que la persona tenga cuenta Sulotec en esa empresa (y su invitación). */
data class CuentaSolicitud(
    val correo: String,
    val nombre: String?,
    val reinvitar: Boolean
)

/**
 * Respuesta de POST /v1/licencias/{uuid}/cuentas. estado: creada, existente, otra_empresa, sin_empresa, sin_keycloak
 * o error; invitacion: correo, clave_temporal o ninguna. sinLicencia = true cuando respondió 404 (sin licencia de PLAFT).
 * clave_temporal solo se devuelve a quien creó la cuenta: nunca se guarda ni se escribe en el log (por eso toString la oculta).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CuentaRemota(
    val estado: String? = null,
    val invitacion: String? = null,
    @JsonProperty("clave_temporal") val claveTemporal: String? = null,
    val mensaje: String? = null,
    @JsonProperty("sin_licencia") val sinLicencia: Boolean = false
) {
    override fun toString(): String = "CuentaRemota(estado=$estado, invitacion=$invitacion, sinLicencia=$sinLicencia)"
}

/** Cuerpo de PUT /v1/licencias/{uuid}/usuarios-activos. */
data class ActivosSolicitud(val activos: Long)

/** Lo que ve la web en GET /api/licencia (C1). Las claves van en snake_case tal cual. */
data class LicenciaPublica(
    val estado: String,
    val plan: String?,
    @JsonProperty("usuarios_max") val usuariosMax: Long?,
    @JsonProperty("usuarios_activos") val usuariosActivos: Long?,
    @JsonProperty("puede_crear_usuarios") val puedeCrearUsuarios: Boolean?,
    val vencimiento: String?,
    @JsonProperty("dias_para_vencer") val diasParaVencer: Long?,
    val periodo: String?,
    val tokens: TokensRemotos?,
    val mensaje: String,
    val enlace: String,
    // Lo que cuesta cada acción según Sulotec, tal cual lo responde el servicio (vacía si no lo manda).
    val tarifas: List<TarifaRemota> = emptyList()
)

/** Respuesta de GET /api/licencia. licencia = null: licencias apagadas o sin información (la web no muestra nada). */
data class LicenciaRespuesta(
    val exento: Boolean,
    val empresa: String?,
    val licencia: LicenciaPublica?,
    // La empresa de quien pregunta está en modo Sulotec (sus cuentas se crean en Sulotec y entran con la cuenta Sulotec).
    val cuentasSulotec: Boolean = false
)
