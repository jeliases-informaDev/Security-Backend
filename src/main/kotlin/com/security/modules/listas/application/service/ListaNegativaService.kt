package com.security.modules.listas.application.service

import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.domain.HistorialConsulta
import com.security.modules.listas.dto.HistorialConsultaResponse
import com.security.modules.listas.dto.ManchaResponse
import com.security.modules.listas.dto.ResultadoBusquedaResponse
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.listas.infrastructure.HistorialConsultaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.security.Alcance
import com.security.shared.security.VisibilidadEmpresaService
import com.security.shared.exceptions.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ListaNegativaService(
    private val entidadRepository: EntidadRepository,
    private val historialConsultaRepository: HistorialConsultaRepository,
    private val usuarioRepository: UsuarioRepository,
    private val visibilidad: VisibilidadEmpresaService
) {

    companion object {
        const val TAMANO_PAGINA = 20
        const val TAMANO_MAXIMO = 50
        const val CARACTERES_MINIMOS = 3
    }

    // Busca de a una pagina (la web pide 20; el tope es 50). El total sale de un conteo aparte, que Spring Data no ejecuta
    // cuando la primera pagina ya trae todo. Al historial solo van las personas que se muestran en esta pagina.
    @Transactional
    fun buscar(
        username: String,
        documento: String?,
        tipoDocumento: String?,
        nombres: String?,
        apellidoPaterno: String?,
        apellidoMaterno: String?,
        pagina: Int = 0,
        tamano: Int = TAMANO_PAGINA
    ): Page<ResultadoBusquedaResponse> {

        if (pagina < 0 || tamano < 1) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "La página debe ser 0 o mayor y el tamaño, al menos 1"
            )
        }

        val documentoLimpio = documento.limpio()
        val nombresLimpio = nombres.limpio()
        val paternoLimpio = apellidoPaterno.limpio()
        val maternoLimpio = apellidoMaterno.limpio()
        val textos = listOf(nombresLimpio, paternoLimpio, maternoLimpio)

        if (documentoLimpio == null && textos.all { it == null }) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Debe indicar al menos un criterio de búsqueda (documento, nombres o apellidos)"
            )
        }
        // Sin documento, un texto de 1 o 2 letras coincide con media base de datos: se pide al menos 3 en algun campo
        if (documentoLimpio == null && textos.none { it != null && it.count(Char::isLetterOrDigit) >= CARACTERES_MINIMOS }) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Escriba al menos $CARACTERES_MINIMOS letras en el nombre o en algún apellido, o indique el documento"
            )
        }

        val encontradas = entidadRepository.buscar(
            documento = documentoLimpio,
            tipoDocumento = tipoDocumento.limpio(),
            nombres = nombresLimpio,
            apellidoPaterno = paternoLimpio,
            apellidoMaterno = maternoLimpio,
            pageable = PageRequest.of(pagina, minOf(tamano, TAMANO_MAXIMO))
        )

        if (encontradas.isEmpty) {
            return PageImpl(emptyList(), encontradas.pageable, encontradas.totalElements)
        }

        // Las apariciones en listas de toda la pagina, en una sola consulta (antes era una por persona)
        val conManchas = entidadRepository.cargarManchas(encontradas.content.mapNotNull { it.id }).associateBy { it.id }

        usuarioRepository.findByUsuario(username)?.let { usuario ->
            historialConsultaRepository.saveAll(
                encontradas.content.map { entidad -> HistorialConsulta(usuario = usuario, entidad = entidad) }
            )
        }

        return encontradas.map { (conManchas[it.id] ?: it).toResponse() }
    }

    private fun String?.limpio(): String? = this?.trim()?.ifBlank { null }

    @Transactional(readOnly = true)
    fun obtenerHistorial(username: String, pageable: Pageable): Page<HistorialConsultaResponse> {

        // Cada cuenta ve sus consultas; el administrador de empresa, las de su empresa; el universal, todas.
        val vista = visibilidad.de(username)
        val pagina = when (vista.alcance) {
            Alcance.TODO -> historialConsultaRepository.buscarTodas(pageable)
            Alcance.EMPRESA -> historialConsultaRepository.buscarPorEmpresa(requireNotNull(vista.empresa), pageable)
            Alcance.PROPIO -> historialConsultaRepository.buscarPorUsuario(vista.usuario.id!!, pageable)
        }

        return pagina
            .map { consulta ->
                HistorialConsultaResponse(
                    id = consulta.id!!,
                    fechaConsulta = consulta.fechaConsulta,
                    resultado = consulta.entidad.toResponse()
                )
            }
    }

    @Transactional(readOnly = true)
    fun obtenerDetalle(entidadId: Int): ResultadoBusquedaResponse {
        val entidad = entidadRepository.buscarPorIdConDetalle(entidadId)
            ?: throw ResourceNotFoundException("No se encontró el registro solicitado")

        return entidad.toResponse()
    }

    private fun Entidad.toResponse(): ResultadoBusquedaResponse {
        val nombreCompleto = when {
            this.personaNatural != null -> listOfNotNull(
                this.personaNatural?.nombre,
                this.personaNatural?.segundoNombre,
                this.personaNatural?.apePat,
                this.personaNatural?.apeMat
            ).joinToString(" ")
            this.personaJuridica != null -> this.personaJuridica?.razonSocial ?: ""
            else -> ""
        }

        val manchas = this.manchas
            .sortedByDescending { it.fechaRegistro }
            .map { mancha ->
                ManchaResponse(
                    id = mancha.id!!,
                    tipoListaCodigo = mancha.tipoLista.codigo,
                    tipoListaNombre = mancha.tipoLista.nombre,
                    grupoNombre = mancha.tipoLista.grupo?.nombre,
                    grupoColor = mancha.tipoLista.grupo?.color,
                    esPep = mancha.tipoLista.esPep,
                    descripcion = mancha.descripcion,
                    link = mancha.link,
                    fechaRegistro = mancha.fechaRegistro,
                    fechaHasta = mancha.fechaHasta,
                    institucion = mancha.institucion,
                    cargo = mancha.cargo,
                    tipoPep = mancha.tipoPep,
                    periodoDesde = mancha.periodoDesde,
                    periodoHasta = mancha.periodoHasta
                )
            }

        return ResultadoBusquedaResponse(
            entidadId = this.id!!,
            tipoEntidad = this.tipoEntidad,
            documento = this.documento,
            tipoDocumento = this.tipoDocumento.nombre,
            nombreCompleto = nombreCompleto,
            nombres = listOfNotNull(
                this.personaNatural?.nombre,
                this.personaNatural?.segundoNombre
            ).joinToString(" ").ifBlank { null },
            apellidoPaterno = this.personaNatural?.apePat,
            apellidoMaterno = this.personaNatural?.apeMat,
            pasaporte = this.personaNatural?.pasaporte,
            alias = this.alias,
            fechaNacimientoRegistro = this.fechaRegistro,
            pais = this.pais?.nombre,
            manchas = manchas
        )
    }
}
