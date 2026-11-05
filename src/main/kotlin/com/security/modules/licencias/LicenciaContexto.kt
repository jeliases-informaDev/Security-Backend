package com.security.modules.licencias

import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.security.esAdministrador
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Quién actúa, para la licencia. usuario = la cuenta del token; empresa = el nombre que guarda la cuenta;
 * empresaUuid = el UUID de esa empresa, que es el que conoce Sulotec (null = no se consulta ni se bloquea nada);
 * esSulotec = el administrador de Sulotec (rol ADMINISTRADOR), que nunca se bloquea.
 */
data class ContextoLicencia(
    val usuario: String,
    val empresa: String?,
    val empresaUuid: String?,
    val esSulotec: Boolean
)

@Service
class LicenciaContexto(
    private val usuarioRepository: UsuarioRepository,
    private val empresaRepository: EmpresaRepository
) {

    /** null si no hay nadie autenticado. La empresa sale siempre de la cuenta en la base, nunca de lo que mande la web. */
    @Transactional(readOnly = true)
    fun de(autenticacion: Authentication?): ContextoLicencia? {
        if (!estaAutenticado(autenticacion)) return null
        val nombre = autenticacion!!.name
        val empresa = usuarioRepository.findByUsuario(nombre)?.empresa?.trim()?.takeIf { it.isNotEmpty() }
        return ContextoLicencia(
            usuario = nombre,
            empresa = empresa,
            empresaUuid = uuidDeEmpresa(empresa),
            esSulotec = autenticacion.esAdministrador()
        )
    }

    /** El de la petición en curso. */
    @Transactional(readOnly = true)
    fun actual(): ContextoLicencia? = de(SecurityContextHolder.getContext().authentication)

    /** UUID de la empresa por su nombre (ignorando mayúsculas y espacios), o null si no existe o no lo tiene. */
    @Transactional(readOnly = true)
    fun uuidDeEmpresa(nombreEmpresa: String?): String? {
        val nombre = nombreEmpresa?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return empresaRepository.findByNombreIgnoreCase(nombre)?.uuid?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        /** Spring Security pone un AnonymousAuthenticationToken en las rutas públicas: eso no es una cuenta. */
        fun estaAutenticado(autenticacion: Authentication?): Boolean =
            autenticacion != null && autenticacion.isAuthenticated && autenticacion !is AnonymousAuthenticationToken
    }
}
