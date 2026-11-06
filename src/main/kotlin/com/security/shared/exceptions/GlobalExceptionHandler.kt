package com.security.shared.exceptions

import com.security.modules.auth.sso.SsoException
import com.security.modules.licencias.LicenciaBloqueadaException
import com.security.modules.licencias.LicenciaBolsaAgotadaException
import com.security.modules.licencias.LicenciaReglas
import com.security.modules.licencias.LicenciaSinCupoException
import com.security.modules.operaciones.application.service.OperacionesException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
            request = request,
            code = ex.code
        )
    }

    // Registro de Operaciones: error de negocio con su codigo y, en los datos de un formulario, el mensaje de cada campo.
    // Va antes que ResponseStatusException (de la que hereda) porque Spring elige el manejador mas especifico.
    @ExceptionHandler(OperacionesException::class)
    fun handleOperaciones(
        ex: OperacionesException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.valueOf(ex.statusCode.value()),
            message = ex.reason ?: "Solicitud inválida",
            request = request,
            code = ex.code,
            campos = ex.campos?.takeIf { it.isNotEmpty() }
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

    // Inicio único con la cuenta Sulotec: el status y el code los decide SsoReglas (nunca 502/504: Cloudflare los reemplaza)
    @ExceptionHandler(SsoException::class)
    fun handleSso(
        ex: SsoException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = ex.status,
            message = ex.message ?: "No se pudo entrar con la cuenta Sulotec",
            request = request,
            code = ex.code
        )
    }

    // 402 - La empresa usó todos los tokens del mes, incluida la tolerancia (servicio común de licencias)
    @ExceptionHandler(LicenciaBolsaAgotadaException::class)
    fun handleLicenciaBolsaAgotada(
        ex: LicenciaBolsaAgotadaException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.PAYMENT_REQUIRED,
            message = ex.message ?: LicenciaReglas.MENSAJE_BOLSA_AGOTADA,
            request = request,
            code = ex.code,
            enlace = ex.enlace
        )
    }

    // 403 - La licencia no permite registrar cambios (solo lectura, suspendida o cancelada)
    @ExceptionHandler(LicenciaBloqueadaException::class)
    fun handleLicenciaBloqueada(
        ex: LicenciaBloqueadaException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.FORBIDDEN,
            message = ex.message ?: LicenciaReglas.mensajeDeEstado(ex.estado),
            request = request,
            code = ex.code,
            enlace = ex.enlace,
            estado = ex.estado
        )
    }

    // 409 - El plan llegó al máximo de usuarios activos
    @ExceptionHandler(LicenciaSinCupoException::class)
    fun handleLicenciaSinCupo(
        ex: LicenciaSinCupoException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {

        return buildResponse(
            status = HttpStatus.CONFLICT,
            message = ex.message ?: LicenciaReglas.textoCupo(ex.activos, ex.usuariosMax),
            request = request,
            code = ex.code,
            enlace = ex.enlace,
            activos = ex.activos,
            usuariosMax = ex.usuariosMax
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
        request: HttpServletRequest,
        code: String? = null,
        enlace: String? = null,
        estado: String? = null,
        activos: Long? = null,
        usuariosMax: Long? = null,
        campos: Map<String, String>? = null
    ): ResponseEntity<ApiErrorResponse> {

        val response = ApiErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = request.requestURI,
            code = code,
            enlace = enlace,
            estado = estado,
            activos = activos,
            usuariosMax = usuariosMax,
            campos = campos
        )

        // JSON fijo: así el error también llega a quien pidió un PDF (Accept: application/pdf) en vez de un 406.
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response)
    }
}