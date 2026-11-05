package com.security.modules.listas.application.service

import com.security.modules.listas.infrastructure.ConsultaListaRepository
import com.security.shared.datos.entities.Usuario
import com.security.shared.security.limpiarEmpresa
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** Una persona que la empresa descarto como homonimo en una consulta a Listas Negativas. */
data class HomonimoVigente(
    val consultaId: Long,
    val fecha: LocalDateTime?,
    val usuario: String,
    val comentario: String?
)

/**
 * Lo que el scoring pregunta a Listas Negativas: si la empresa de quien evalua descarto a esa persona como homonimo
 * (la marca vigente mas reciente). Una cuenta sin empresa solo cuenta sus propias consultas.
 */
@Service
class HomonimosListasService(private val consultaRepository: ConsultaListaRepository) {

    @Transactional(readOnly = true)
    fun vigente(usuario: Usuario?, entidadId: Int?): HomonimoVigente? {
        if (usuario == null || entidadId == null) return null
        val c = consultaRepository.homonimosDe(entidadId, limpiarEmpresa(usuario.empresa), usuario.id, PageRequest.of(0, 1))
            .firstOrNull() ?: return null
        return HomonimoVigente(c.id!!, c.homonimoFecha, nombreCorto(c.homonimoPor), c.homonimoComentario)
    }
}
