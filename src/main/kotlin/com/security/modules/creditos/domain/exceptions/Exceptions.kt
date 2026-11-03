package com.security.modules.creditos.domain.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

open class CreditoException(message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
class SaldoInsuficienteException(
    val disponibles: Long,
    val requeridos: Int,
    val enExceso: Boolean = false,
    message: String = "Saldo de créditos insuficiente. Se requieren $requeridos créditos y la bolsa dispone de $disponibles."
) : CreditoException(message)

@ResponseStatus(HttpStatus.FORBIDDEN)
class LicenciaVencidaException(
    val estado: String,
    message: String = "La licencia de la empresa se encuentra en estado $estado."
) : CreditoException(message)

@ResponseStatus(HttpStatus.CONFLICT)
class CupoUsuariosExcedidoException(
    val cupoMaximo: Int,
    val usuariosActivos: Long,
    message: String = "Se ha alcanzado el límite de usuarios activos permitido ($usuariosActivos / $cupoMaximo)."
) : CreditoException(message)
