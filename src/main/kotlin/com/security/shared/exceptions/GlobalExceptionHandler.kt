package com.security.shared.exceptions

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    // 401 - Credenciales incorrectas
    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(
        ex: BadCredentialsException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.UNAUTHORIZED,
            message = "Credenciales inválidas",
            request = request
        )
    }

    // 401 - Token de un solo uso inválido, expirado o ya utilizado (recuperación de clave, etc.)
    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidToken(
        ex: InvalidTokenException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.UNAUTHORIZED,
            message = ex.message ?: "Token inválido",
            request = request
        )
    }

    // 401 - Usuario del token ya no existe
    @ExceptionHandler(UsernameNotFoundException::class)
    fun handleUsernameNotFound(
        ex: UsernameNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.UNAUTHORIZED,
            message = "Sesión inválida",
            request = request
        )
    }

    // 400 - Errores de validación
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        val firstError =
            ex.bindingResult.fieldErrors
                .firstOrNull()
                ?.defaultMessage
                ?: "Datos inválidos"

        return buildResponse(
            status = HttpStatus.BAD_REQUEST,
            message = firstError,
            request = request
        )
    }

    // 400 - Request mal formado, parámetro incorrecto o faltante
    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class
    )
    fun handleBadRequest(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        val message = when (ex) {

            is HttpMessageNotReadableException ->
                "El cuerpo de la solicitud es inválido o está mal formado"

            is MethodArgumentTypeMismatchException ->
                "El parámetro '${ex.name}' tiene un valor inválido"

            is MissingServletRequestParameterException ->
                "Falta el parámetro requerido '${ex.parameterName}'"

            else ->
                "Solicitud inválida"
        }

        return buildResponse(
            status = HttpStatus.BAD_REQUEST,
            message = message,
            request = request
        )
    }

    // 404 - Recurso no encontrado
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(
        ex: NoResourceFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.NOT_FOUND,
            message = "El recurso solicitado no fue encontrado",
            request = request
        )
    }

    // 404 - Recurso de negocio no encontrado (usuario, rol, etc.)
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(
        ex: ResourceNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.NOT_FOUND,
            message = ex.message ?: "Recurso no encontrado",
            request = request
        )
    }

    // 409 - Conflicto (dato duplicado, etc.)
    @ExceptionHandler(ConflictException::class)
    fun handleConflict(
        ex: ConflictException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.CONFLICT,
            message = ex.message ?: "Conflicto con el estado actual del recurso",
            request = request
        )
    }

    // 500 - Cualquier error no controlado
    @ExceptionHandler(Exception::class)
    fun handleInternalServerError(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            message = "Ocurrió un error interno en el servidor",
            request = request
        )
    }

    private fun buildResponse(
        status: HttpStatus,
        message: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        val response = ApiErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = request.requestURI
        )

        return ResponseEntity
            .status(status)
            .body(response)
    }
}