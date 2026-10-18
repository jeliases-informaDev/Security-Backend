package com.security.shared.config

import com.security.shared.datos.entities.Roles
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.security.ROL_ADMINISTRADOR_EMPRESA
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Deja creado el rol ADMINISTRADOR_EMPRESA (el administrador general de una empresa cliente: ve todo lo de
 * su empresa, nada de otras). Corre siempre, es idempotente y si falla no impide el arranque.
 */
@Component
@Order(2)
class RolesInicializador(private val rolesRepository: RolesRepository) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            if (rolesRepository.findByCodigo(ROL_ADMINISTRADOR_EMPRESA) == null) {
                val ahora = LocalDateTime.now()
                rolesRepository.save(
                    Roles(
                        codigo = ROL_ADMINISTRADOR_EMPRESA,
                        nombre = "Administrador de empresa",
                        descripcion = "Ve y gestiona todo lo de su propia empresa",
                        activo = true,
                        fechaCreacion = ahora,
                        fechaActualizacion = ahora
                    )
                )
                log.info("Rol {} creado.", ROL_ADMINISTRADOR_EMPRESA)
            }
        } catch (e: Exception) {
            log.warn("No se pudo comprobar el rol {}: {}", ROL_ADMINISTRADOR_EMPRESA, e.message)
        }
    }
}
