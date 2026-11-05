package com.security.modules.auth.sso

import com.security.modules.auth.AuthService
import com.security.modules.auth.dto.LoginResponse
import com.security.modules.empresas.application.service.EmpresaService
import com.security.modules.empresas.dto.EmpresaCreateRequest
import com.security.modules.licencias.LicenciaService
import com.security.shared.datos.entities.Roles
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.entities.UsuarioRol
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.RolesRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.datos.repositories.UsuarioRolRepository
import com.security.shared.security.ROL_ADMINISTRADOR
import com.security.shared.security.ROL_ADMINISTRADOR_EMPRESA
import com.security.shared.security.usuarioConSigla
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

/**
 * Inicio único con la cuenta Sulotec. Canjea el código (fuera de la transacción: no se tiene la base tomada mientras
 * se habla con Keycloak), decide con SsoReglas quién entra y responde exactamente lo mismo que POST /api/auth/login.
 * Las cuentas, licencias y accesos los maneja Sulotec; PLAFT solo crea por su cuenta la del del administrador universal o
 * del titular de la licencia la primera vez que llegan.
 */
@Service
class SsoService(
    private val props: SsoProperties,
    private val proveedor: SsoProveedor,
    private val usuarioRepository: UsuarioRepository,
    private val rolesRepository: RolesRepository,
    private val usuarioRolRepository: UsuarioRolRepository,
    private val empresaRepository: EmpresaRepository,
    private val empresaService: EmpresaService,
    private val passwordEncoder: PasswordEncoder,
    private val licenciaService: LicenciaService,
    private val authService: AuthService,
    transactionManager: PlatformTransactionManager
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val transaccion = TransactionTemplate(transactionManager)
    private val azar = SecureRandom()

    fun config(): SsoConfigResponse =
        if (!props.activo) SsoConfigResponse(activo = false)
        else SsoConfigResponse(
            activo = true,
            issuer = props.issuerBase,
            clientId = props.clientId.trim(),
            redirectUri = props.redirectUri.trim()
        )

    fun entrar(request: SsoLoginRequest): LoginResponse {
        if (!props.activo) throw SsoReglas.inactivo()
        val code = request.code?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_DATO }
        val verificador = request.codeVerifier?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_DATO }
        if (code == null || verificador == null) throw SsoReglas.datos()

        val identidad = proveedor.canjear(code, verificador)
        val correo = SsoReglas.correoDe(identidad)

        return transaccion.execute {
            val local = usuarioRepository.findByCorreoIgnoreCase(correo)
            val cuenta = local?.let { CuentaLocalSso(it.usuario, it.activo, it.roles.firstOrNull()?.rol?.codigo) }
            val usuario = when (val decision = SsoReglas.decidir(identidad, cuenta, ::licenciaEnVivo)) {
                is DecisionSso.Entrar -> local!!
                is DecisionSso.NuevoAdministradorSulotec -> crearAdministradorSulotec(identidad, correo)
                is DecisionSso.NuevoTitular -> crearTitular(identidad, correo, decision)
            }
            // El mismo token y la misma respuesta que el login con usuario y contraseña.
            authService.respuestaDe(usuario)
        }!!
    }

    /** La licencia en vivo; si Sulotec no responde no se puede saber si es el titular: 424 con un mensaje claro. */
    private fun licenciaEnVivo(uuid: String) = try {
        licenciaService.licenciaEnVivo(uuid)
    } catch (e: Exception) {
        log.warn("No se pudo consultar la licencia de la empresa {} para el inicio único: {}", uuid, e.message)
        throw SsoReglas.emisor("No se pudo confirmar la licencia de tu empresa con Sulotec. Intenta de nuevo en unos minutos.")
    }

    /** Administrador universal de Sulotec: se guarda igual que el que ya existe (rol ADMINISTRADOR, sin empresa). */
    private fun crearAdministradorSulotec(identidad: SsoIdentidad, correo: String): Usuario {
        val rol = rol(ROL_ADMINISTRADOR)
        val usuario = guardar(
            nombreUsuario = usuarioLibre(SsoReglas.usuarioBase(correo)),
            correo = correo,
            identidad = identidad,
            empresa = null,
            rol = rol
        )
        log.info("Inicio único: se creó la cuenta {} de administrador de Sulotec para {}", usuario.usuario, correo)
        return usuario
    }

    /**
     * Titular de la licencia: si la empresa no existe se crea con el UUID de Sulotec; la cuenta nace como
     * ADMINISTRADOR_EMPRESA. No pasa por el control de cupo (el dueño de la licencia siempre puede entrar), pero
     * después se reportan las cuentas activas como en cualquier alta.
     */
    private fun crearTitular(identidad: SsoIdentidad, correo: String, decision: DecisionSso.NuevoTitular): Usuario {
        val existente = empresaRepository.findByUuid(decision.empresaUuid)
        val (nombreEmpresa, sigla) = if (existente != null) {
            existente.nombre to existente.sigla
        } else {
            val nombre = decision.empresaNombre
                ?: throw SsoReglas.datos("Sulotec no envió el nombre de tu empresa. Escribe a contacto@sulotec.com.")
            if (empresaRepository.findByNombreIgnoreCase(nombre) != null) throw SsoReglas.empresaEnConflicto(nombre)
            val creada = empresaService.crear(EmpresaCreateRequest(nombre = nombre, uuid = decision.empresaUuid))
            log.info("Inicio único: se creó la empresa {} ({}) para el titular de la licencia", creada.nombre, creada.uuid)
            creada.nombre to creada.sigla
        }
        val base = SsoReglas.usuarioBase(correo)
        val usuario = guardar(
            nombreUsuario = usuarioLibre(sigla?.let { usuarioConSigla(it, base) } ?: base),
            correo = correo,
            identidad = identidad,
            empresa = nombreEmpresa,
            rol = rol(ROL_ADMINISTRADOR_EMPRESA)
        )
        log.info("Inicio único: alta automática de {} como administrador de la empresa {} (titular de la licencia)", usuario.usuario, nombreEmpresa)
        licenciaService.reportarActivosLuego(nombreEmpresa)
        return usuario
    }

    private fun guardar(nombreUsuario: String, correo: String, identidad: SsoIdentidad, empresa: String?, rol: Roles): Usuario {
        val ahora = LocalDateTime.now()
        val usuario = usuarioRepository.save(
            Usuario(
                usuario = nombreUsuario,
                // Nadie la conoce: entra por Sulotec; si quiere clave local usa «olvidé mi contraseña».
                clave = passwordEncoder.encode(claveAleatoria())!!,
                correo = correo,
                nombres = identidad.nombres,
                apePat = identidad.apellidos,
                empresa = empresa,
                activo = true,
                fechaCreacion = ahora,
                fechaActualizacion = ahora
            )
        )
        val enlace = UsuarioRol(usuario = usuario, rol = rol)
        usuarioRolRepository.save(enlace)
        usuario.roles = mutableListOf(enlace)
        return usuario
    }

    private fun rol(codigo: String): Roles =
        rolesRepository.findByCodigo(codigo) ?: throw IllegalStateException("No existe el rol $codigo")

    /** El usuario propuesto, o con un número al final si ya está en uso. */
    private fun usuarioLibre(propuesto: String): String {
        if (!usuarioRepository.existsByUsuario(propuesto)) return propuesto
        for (n in 2..999) {
            val candidato = "$propuesto$n"
            if (!usuarioRepository.existsByUsuario(candidato)) return candidato
        }
        throw IllegalStateException("No se pudo generar un usuario libre para $propuesto")
    }

    // 32 bytes al azar (43 caracteres): por debajo del límite de 72 bytes de BCrypt.
    private fun claveAleatoria(): String {
        val bytes = ByteArray(32)
        azar.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private const val MAX_DATO = 4096
    }
}
