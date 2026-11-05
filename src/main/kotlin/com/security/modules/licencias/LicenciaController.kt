package com.security.modules.licencias

import com.security.modules.auth.sso.CuentasSulotec
import com.security.modules.licencias.dominio.LicenciaRespuesta
import com.security.shared.security.esAdministrador
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * La licencia de PLAFT de la empresa de quien entra, para el aviso de la web (C1). Cualquier rol puede verla.
 * Nunca falla por el servicio de licencias: sin información responde licencia = null y la web no muestra nada.
 */
@RestController
@RequestMapping("/api/licencia")
class LicenciaController(
    private val contexto: LicenciaContexto,
    private val licencias: LicenciaService,
    private val cuentasSulotec: CuentasSulotec
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun obtener(
        authentication: Authentication,
        @RequestParam(name = "refrescar", required = false) refrescar: String?
    ): LicenciaRespuesta {
        val forzar = refrescar?.trim()?.lowercase() in setOf("1", "true", "si", "sí")
        var actor: ContextoLicencia? = null
        val licencia = try {
            actor = contexto.de(authentication)
            licencias.licenciaPublica(actor?.empresaUuid, forzar)
        } catch (e: Exception) {
            log.warn("No se pudo leer la licencia de {}: {}", authentication.name, e.message)
            null
        }
        // Modo Sulotec de la empresa de quien pregunta; el administrador de Sulotec (sin empresa) ve si está encendido.
        val cuentas = try {
            if (authentication.esAdministrador()) cuentasSulotec.encendido else cuentasSulotec.modoSulotec(actor?.empresaUuid)
        } catch (e: Exception) {
            log.warn("No se pudo saber si {} usa cuentas Sulotec: {}", authentication.name, e.message)
            false
        }
        return LicenciaRespuesta(
            exento = authentication.esAdministrador(),
            empresa = actor?.empresa,
            licencia = licencia,
            cuentasSulotec = cuentas
        )
    }
}

/**
 * Al arrancar, cada empresa le dice a Sulotec cuántas cuentas activas tiene. Corre después de EmpresaCatalogoInicializador
 * (que completa los UUID), en un hilo aparte y sin impedir nunca el arranque.
 */
@Component
@Order(4)
class LicenciasArranque(private val licencias: LicenciaService) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            licencias.alinearActivosEnSegundoPlano()
        } catch (e: Exception) {
            log.warn("No se pudo programar la alineación de cuentas activas con Sulotec: {}", e.message)
        }
    }
}
