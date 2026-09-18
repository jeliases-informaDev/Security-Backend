package com.security.shared.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)  // 401, no 403
            .body(mapOf("error" to ex.message))

    @ExceptionHandler(Exception::class)
    fun handleGenericError(ex: Exception): ResponseEntity<Map<String, String?>> {
        // loguea el error real para ti, pero no expongas detalles internos al cliente
        // logger.error("Error inesperado", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "Ocurrió un error inesperado"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String?>> {
        val primerError = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
        return ResponseEntity.badRequest().body(mapOf("error" to (primerError ?: "Datos inválidos")))
    }
}