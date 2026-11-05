package com.security.modules.licencias.web

import com.security.modules.licencias.DecisionLicencia
import com.security.modules.licencias.LicenciaBloqueadaException
import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaReglas
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.config.LicenciasProperties
import com.security.shared.security.esAdministrador
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Guard de escrituras según el estado de la licencia (P5). Es un interceptor de Spring MVC y no un filtro: corre después
 * de la autenticación y lo que lanza pasa por GlobalExceptionHandler, con el mismo JSON de error de siempre. Ante un
 * error propio inesperado deja pasar: un fallo de la licencia nunca debe tumbar PLAFT.
 */
@Component
class LicenciaGuardInterceptor(
    private val properties: LicenciasProperties,
    private val contexto: LicenciaContexto,
    private val licencias: LicenciaService
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val decision = try {
            evaluar(request)
        } catch (e: Exception) {
            log.warn("No se pudo revisar la licencia en {} {}: se deja pasar ({})", request.method, request.requestURI, e.message)
            DecisionLicencia.PERMITIR
        }
        if (!decision.permitir) {
            throw LicenciaBloqueadaException(
                estado = decision.estado ?: "",
                code = decision.code ?: LicenciaReglas.CODE_CANCELADA,
                message = decision.mensaje ?: LicenciaReglas.mensajeDeEstado(decision.estado)
            )
        }
        return true
    }

    private fun evaluar(request: HttpServletRequest): DecisionLicencia {
        if (!properties.activo) return DecisionLicencia.PERMITIR
        val metodo = request.method ?: return DecisionLicencia.PERMITIR
        val ruta = request.requestURI.removePrefix(request.contextPath ?: "")
        val activo = request.getParameter("activo")
        val autenticacion = SecurityContextHolder.getContext().authentication
        val autenticado = LicenciaContexto.estaAutenticado(autenticacion)
        val esSulotec = autenticado && autenticacion!!.esAdministrador()
        // Lo exento se decide sin tocar la base: las lecturas y el administrador de Sulotec no cuestan nada.
        if (LicenciaReglas.esExenta(metodo, ruta, activo, esSulotec, autenticado)) return DecisionLicencia.PERMITIR
        val actor = contexto.de(autenticacion) ?: return DecisionLicencia.PERMITIR
        val estado = licencias.licenciaDe(actor.empresaUuid)?.estado
        return LicenciaReglas.decidir(metodo, ruta, activo, actor.esSulotec, autenticado, estado)
    }
}

@Configuration
class LicenciaWebConfig(private val guard: LicenciaGuardInterceptor) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(guard).addPathPatterns("/api/**")
    }
}
