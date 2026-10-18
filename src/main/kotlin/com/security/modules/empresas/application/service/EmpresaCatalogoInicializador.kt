package com.security.modules.empresas.application.service

import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import com.security.shared.security.siglaLibre
import com.security.shared.security.siglaSugerida
import java.util.UUID
import java.time.LocalDateTime

/**
 * Al arrancar, deja en la lista de empresas las que ya tienen escritas las cuentas existentes, para
 * que al activar la lista no se pierda ni quede huerfana ninguna. Si despues de eso la lista sigue
 * vacia (base nueva) agrega una empresa de demostracion, para poder crear cuentas desde el primer dia.
 *
 * Es idempotente y nunca toca las cuentas: solo agrega empresas que faltan. Cada alta se guarda por
 * separado y cualquier fallo se registra sin impedir que la API arranque (la lista tambien se puede
 * llenar desde la pantalla de administracion).
 */
@Component
@Order(2)
class EmpresaCatalogoInicializador(
    private val empresaRepository: EmpresaRepository,
    private val usuarioRepository: UsuarioRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            sincronizar()
        } catch (e: Exception) {
            log.warn("No se pudo preparar la lista de empresas al arrancar: {}", e.message)
        }
    }

    internal fun sincronizar() {
        var agregadas = 0

        val enUso = usuarioRepository.findEmpresasEnUso()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        for (nombre in enUso) {
            if (agregarSiFalta(nombre)) agregadas++
        }

        if (empresaRepository.count() == 0L && agregarSiFalta(EMPRESA_DEMO)) {
            agregadas++
        }

        if (agregadas > 0) {
            log.info("Lista de empresas: se agregaron {} (las que tenian las cuentas o la de demostracion).", agregadas)
        }
        completarUuidYSigla()
    }

    /** A las empresas creadas antes de existir uuid y sigla se les asignan al arrancar (una sola vez). */
    internal fun completarUuidYSigla() {
        val asignadas = mutableSetOf<String>()
        for (empresa in empresaRepository.findAll()) {
            if (empresa.uuid != null && empresa.sigla != null) continue
            try {
                if (empresa.uuid == null) empresa.uuid = UUID.randomUUID().toString()
                if (empresa.sigla == null) {
                    empresa.sigla = siglaLibre(siglaSugerida(empresa.nombre)) { it in asignadas || empresaRepository.existsBySiglaIgnoreCase(it) }
                }
                asignadas.add(empresa.sigla!!)
                empresaRepository.save(empresa)
                log.info("Empresa '{}': sigla {} asignada.", empresa.nombre, empresa.sigla)
            } catch (e: Exception) {
                log.warn("No se pudo completar uuid/sigla de la empresa '{}': {}", empresa.nombre, e.message)
            }
        }
    }

    private fun agregarSiFalta(nombre: String): Boolean {
        return try {
            if (empresaRepository.existsByNombreIgnoreCase(nombre)) {
                false
            } else {
                val ahora = LocalDateTime.now()
                val sigla = siglaLibre(siglaSugerida(nombre)) { empresaRepository.existsBySiglaIgnoreCase(it) }
                empresaRepository.save(Empresa(nombre = nombre, uuid = UUID.randomUUID().toString(), sigla = sigla,
                    fechaCreacion = ahora, fechaActualizacion = ahora))
                true
            }
        } catch (e: Exception) {
            log.warn("No se pudo agregar la empresa '{}' a la lista: {}", nombre, e.message)
            false
        }
    }

    companion object {
        const val EMPRESA_DEMO = "Empresa Demo"
    }
}
