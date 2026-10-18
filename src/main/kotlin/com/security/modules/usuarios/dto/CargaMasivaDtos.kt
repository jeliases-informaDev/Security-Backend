package com.security.modules.usuarios.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

/** Una fila de la carga masiva (lo que trae el Excel o CSV). El usuario interno se arma solo. */
data class CuentaMasivaRequest(
    @field:NotBlank(message = "Escribe los nombres")
    val nombres: String,
    @field:NotBlank(message = "Escribe el apellido paterno")
    val apePat: String,
    val apeMat: String? = null,
    @field:NotBlank(message = "Escribe el correo")
    val correo: String,
    val cargo: String? = null,
    @field:NotBlank(message = "Elige el rol")
    val rolCodigo: String
)

data class CargaMasivaRequest(
    @field:NotBlank(message = "La empresa es obligatoria")
    val empresa: String,
    // true: solo revisa y devuelve el resultado fila por fila, sin crear nada
    val soloValidar: Boolean = false,
    @field:Valid
    @field:NotEmpty(message = "No hay cuentas para cargar")
    @field:Size(max = 500, message = "Hasta 500 cuentas por carga")
    val cuentas: List<CuentaMasivaRequest>
)

data class FilaCargaMasivaResponse(
    // 1-based, en el orden en que vinieron
    val fila: Int,
    val correo: String,
    val nombreCompleto: String,
    // "ok" (lista para crear), "creada", "error"
    val estado: String,
    val mensaje: String? = null,
    // usuario interno asignado (sigla.usuario)
    val usuario: String? = null,
    val invitacionEnviada: Boolean = false,
    // Solo cuando no se pudo enviar la invitacion por correo: clave temporal para entregarla a mano
    val claveTemporal: String? = null
)

data class CargaMasivaResponse(
    val empresa: String,
    val sigla: String,
    val soloValidar: Boolean,
    val total: Int,
    val listas: Int,
    val creadas: Int,
    val conError: Int,
    // Si el correo de salida esta configurado, las cuentas nuevas reciben un enlace para crear su clave
    val invitacionesPorCorreo: Boolean,
    val filas: List<FilaCargaMasivaResponse>
)

data class InvitacionResponse(
    val enviada: Boolean,
    val mensaje: String
)
