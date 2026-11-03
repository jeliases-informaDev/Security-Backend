package com.security.shared.exceptions

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
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

    // 403 - Autenticado pero sin el rol/permiso requerido (@PreAuthorize).
    // Se maneja acá y no solo en RestAccessDeniedHandler porque este @RestControllerAdvice
    // intercepta la excepción dentro del ciclo de DispatcherServlet, antes de que pueda
    // propagarse hasta el ExceptionTranslationFilter de la cadena de Spring Security.
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.FORBIDDEN,
            message = "No tiene permisos para acceder a este recurso",
            request = request
        )
    }

    // Respeta el status/mensaje de cualquier ResponseStatusException lanzada a mano en el
    // codigo (p.ej. validaciones de negocio simples). Sin este handler, el catch-all
    // Exception::class de abajo la interceptaria primero y siempre devolveria 500.
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.valueOf(ex.statusCode.value()),
            message = ex.reason ?: "Solicitud inválida",
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

    // 402 - Saldo insuficiente de creditos/tokens (P3)
    @ExceptionHandler(com.security.modules.creditos.domain.exceptions.SaldoInsuficienteException::class)
    fun handleSaldoInsuficiente(
        ex: com.security.modules.creditos.domain.exceptions.SaldoInsuficienteException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.PAYMENT_REQUIRED,
            message = ex.message ?: "Saldo insuficiente de créditos",
            request = request
        )
    }

    // 403 - Licencia en solo lectura o suspendida (P4)
    @ExceptionHandler(com.security.modules.creditos.domain.exceptions.LicenciaVencidaException::class)
    fun handleLicenciaVencida(
        ex: com.security.modules.creditos.domain.exceptions.LicenciaVencidaException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.FORBIDDEN,
            message = ex.message ?: "Licencia no vigente",
            request = request
        )
    }

    // 409 - Cupo maximo de usuarios alcanzado (P2)
    @ExceptionHandler(com.security.modules.creditos.domain.exceptions.CupoUsuariosExcedidoException::class)
    fun handleCupoUsuariosExcedido(
        ex: com.security.modules.creditos.domain.exceptions.CupoUsuariosExcedidoException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.CONFLICT,
            message = ex.message ?: "Cupo de usuarios excedido",
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