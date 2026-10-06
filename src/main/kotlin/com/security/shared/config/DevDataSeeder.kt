package com.security.shared.config

import com.security.shared.datos.entities.Pais
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.TipoDocumento
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.PaisRepository
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.TipoDocumentoRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Datos minimos para poder entrar al sistema en una base de datos nueva (los 3 roles, un
 * usuario administrador y los catalogos que otras pantallas necesitan).
 *
 * Solo corre si app.seed.enabled=true (variable APP_SEED_ENABLED). El docker-compose de desarrollo la
 * activa; NO la actives contra una base compartida o de produccion. Es idempotente: si el dato ya
 * existe, no lo toca (nunca pisa una clave ya cambiada).
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "app.seed", name = ["enabled"], havingValue = "true")
class DevDataSeeder(
    private val rolesRepository: RolesRepository,
    private val usuarioRepository: UsuarioRepository,
    private val usuarioRolRepository: UsuarioRolRepository,
    private val tipoDocumentoRepository: TipoDocumentoRepository,
    private val paisRepository: PaisRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.seed.admin-user:superadmin}") private val adminUser: String,
    @Value("\${app.seed.admin-password:Admin12345!}") private val adminPassword: String,
    @Value("\${app.seed.admin-email:admin@security.local}") private val adminEmail: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        sembrarCatalogos()
        sembrarAdministrador()
    }

    private fun sembrarCatalogos() {
        // Mismos catalogos que tienen las bases reales del equipo.
        listOf(
            "DNI" to "Documento Nacional de Identidad",
            "RUC" to "Registro Unico de Contribuyentes",
            "CE" to "Carne de Extranjeria",
            "PASAPORTE" to "Pasaporte"
        ).forEach { (nombre, descripcion) ->
            if (tipoDocumentoRepository.findByNombre(nombre) == null) {
                tipoDocumentoRepository.save(TipoDocumento(nombre = nombre, descripcion = descripcion))
            }
        }
        listOf("Peru", "Colombia", "Mexico", "Venezuela", "Estados Unidos").forEach { nombre ->
            if (paisRepository.findByNombre(nombre) == null) {
                paisRepository.save(Pais(nombre = nombre, continente = "America"))
            }
        }
    }

    private fun sembrarAdministrador() {
        val ahora = LocalDateTime.now()

        // Los 3 roles que existen en las bases reales; el administrador recibe el primero.
        val roles = listOf(
            Triple(ROL_ADMIN, "Administrador", "Acceso total (creado por el seed de desarrollo)"),
            Triple("SUPERVISOR", "Supervisor", "Rol de supervision (creado por el seed de desarrollo)"),
            Triple("USUARIO", "Usuario", "Rol basico (creado por el seed de desarrollo)")
        ).map { (codigo, nombre, descripcion) ->
            rolesRepository.findByCodigo(codigo) ?: rolesRepository.save(
                Roles(
                    codigo = codigo,
                    nombre = nombre,
                    descripcion = descripcion,
                    fechaCreacion = ahora,
                    fechaActualizacion = ahora
                )
            )
        }
        val rol = roles.first { it.codigo == ROL_ADMIN }

        if (usuarioRepository.existsByUsuario(adminUser)) {
            return
        }

        val usuario = usuarioRepository.save(
            Usuario(
                usuario = adminUser,
                clave = passwordEncoder.encode(adminPassword)!!,
                correo = adminEmail,
                nombres = "Administrador",
                apePat = "Desarrollo",
                activo = true,
                fechaCreacion = ahora,
                fechaActualizacion = ahora
            )
        )
        usuarioRolRepository.save(UsuarioRol(usuario = usuario, rol = rol))

        log.warn("Seed de desarrollo: usuario '{}' creado. Solo para entornos locales.", adminUser)
    }

    companion object {
        private const val ROL_ADMIN = "ADMINISTRADOR"
    }
}
