package com.security.shared.security

import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Hasta donde ve una cuenta (regla general de la plataforma):
 *  - PROPIO:  solo lo que registro ella misma (cualquier cuenta comun).
 *  - EMPRESA: todo lo de su empresa (rol ADMINISTRADOR_EMPRESA: el administrador general de la empresa cliente).
 *  - TODO:    absolutamente todo, de todas las empresas (rol ADMINISTRADOR: el administrador universal de Sulotec).
 * Los clientes no deben saber unos de otros: nada de una empresa se muestra a otra.
 */
enum class Alcance { PROPIO, EMPRESA, TODO }

const val ROL_ADMINISTRADOR_EMPRESA = "ADMINISTRADOR_EMPRESA"

/** Nombre de empresa comparable (sin espacios sobrantes); null si no hay. */
fun limpiarEmpresa(valor: String?): String? = valor?.trim()?.takeIf { it.isNotEmpty() }

fun mismaEmpresa(a: String?, b: String?): Boolean {
    val x = limpiarEmpresa(a) ?: return false
    return x.equals(limpiarEmpresa(b), ignoreCase = true)
}

data class Vista(val usuario: Usuario, val alcance: Alcance, val empresa: String?) {
    /** Si esta cuenta puede ver algo que registro `duenio`. */
    fun puedeVer(duenio: Usuario?): Boolean = when (alcance) {
        Alcance.TODO -> true
        Alcance.EMPRESA -> mismaEmpresa(empresa, duenio?.empresa)
        Alcance.PROPIO -> duenio?.id != null && duenio.id == usuario.id
    }
}

@Service
class VisibilidadEmpresaService(private val usuarioRepository: UsuarioRepository) {

    @Transactional(readOnly = true)
    fun de(username: String): Vista {
        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException("Usuario no encontrado")
        return de(usuario)
    }

    fun de(usuario: Usuario): Vista {
        val roles = usuario.roles.filter { it.rol.activo }.map { it.rol.codigo.trim().uppercase() }
        val empresa = limpiarEmpresa(usuario.empresa)
        val alcance = when {
            ROL_ADMINISTRADOR in roles -> Alcance.TODO
            ROL_ADMINISTRADOR_EMPRESA in roles && empresa != null -> Alcance.EMPRESA
            else -> Alcance.PROPIO
        }
        return Vista(usuario, alcance, empresa)
    }
}
