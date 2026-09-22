package com.security.shared.exceptions

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class
    )
    fun handleBadRequest(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        val response = ApiErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Solicitud inválida",
            path = request.requestURI
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response)
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(
        ex: NoResourceFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        val response = ApiErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = HttpStatus.NOT_FOUND.reasonPhrase,
            message = "El recurso solicitado no fue encontrado",
            path = request.requestURI
        )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleInternalServerError(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        val response = ApiErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            message = "Ocurrió un error interno en el servidor",
            path = request.requestURI
        )

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response)
    }
}