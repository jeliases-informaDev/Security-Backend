package com.security.modules.listas.application.service

import com.security.modules.licencias.CobroAcciones
import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaService
import com.security.modules.licencias.dominio.TIPO_BUSQUEDA_LISTAS
import com.security.modules.licencias.dominio.TIPO_LISTA_PDF
import com.security.modules.listas.domain.Entidad
import com.security.modules.listas.domain.HistorialConsulta
import com.security.modules.listas.dto.HistorialConsultaResponse
import com.security.modules.listas.dto.ManchaResponse
import com.security.modules.listas.dto.ResultadoBusquedaResponse
import com.security.modules.listas.infrastructure.EntidadRepository
import com.security.modules.listas.infrastructure.HistorialConsultaRepository
import com.security.modules.listas.infrastructure.export.ListaNegativaPdfService
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
    private val visibilidad: VisibilidadEmpresaService,
    private val licenciaContexto: LicenciaContexto,
    private val licenciaService: LicenciaService,
    private val listaNegativaPdfService: ListaNegativaPdfService = ListaNegativaPdfService()
) {

    // Mismo licenciaContexto y licenciaService que la búsqueda: así las pruebas que arman el servicio a mano no cambian.
    private val cobro = CobroAcciones(licenciaContexto, licenciaService)

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
        val tamanoFinal = minOf(tamano, TAMANO_MAXIMO)

        val documentoLimpio = documento?.trim()?.ifBlank { null }
        val tipoDocumentoLimpio = tipoDocumento?.trim()?.ifBlank { null }
        val nombresLimpios = nombres?.trim()?.ifBlank { null }
        val paternoLimpio = apellidoPaterno?.trim()?.ifBlank { null }
        val maternoLimpio = apellidoMaterno?.trim()?.ifBlank { null }

        val tieneFiltro = listOfNotNull(
            documentoLimpio,
            tipoDocumentoLimpio,
            nombresLimpios,
            paternoLimpio,
            maternoLimpio
        ).any { it.length >= CARACTERES_MINIMOS }

        if (!tieneFiltro) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Indica al menos un criterio de búsqueda con $CARACTERES_MINIMOS o más caracteres"
            )
        }

        // Solo la primera página es una búsqueda nueva: pasar de página no consume. El token se pide antes de consultar.
        val claveConsumo = if (pagina == 0) {
            consumirBusqueda(listOf(documentoLimpio, tipoDocumentoLimpio, nombresLimpios, paternoLimpio, maternoLimpio))
        } else {
            null
        }

        // Si la consulta falla después de consumir, se devuelve el token.
        return try {
            consultarPagina(
                username, pagina, tamanoFinal,
                documentoLimpio, tipoDocumentoLimpio, nombresLimpios, paternoLimpio, maternoLimpio
            )
        } catch (e: Exception) {
            claveConsumo?.let { licenciaService.anular(it) }
            throw e
        }
    }

    /**
     * Pide 1 token a Sulotec para la búsqueda y devuelve la clave del consumo (null si no se consumió: licencias
     * apagadas, sin sesión o una cuenta sin empresa con UUID). La clave es la misma para la misma persona y los mismos
     * criterios durante dos minutos, así un doble clic o recargar la búsqueda no cobra dos veces.
     */
    private fun consumirBusqueda(criterios: List<String?>): String? {
        if (!licenciaService.activo) return null
        val contexto = licenciaContexto.actual() ?: return null
        val uuid = contexto.empresaUuid ?: return null
        val clave = licenciaService.claveBusqueda(uuid, contexto.usuario, criterios)
        licenciaService.consumirComo(contexto, TIPO_BUSQUEDA_LISTAS, clave, "busqueda en listas negativas")
        return clave
    }

    private fun consultarPagina(
        username: String,
        pagina: Int,
        tamanoFinal: Int,
        documentoLimpio: String?,
        tipoDocumentoLimpio: String?,
        nombresLimpios: String?,
        paternoLimpio: String?,
        maternoLimpio: String?
    ): Page<ResultadoBusquedaResponse> {
        val pageable = PageRequest.of(pagina, tamanoFinal)
        val paginaEntidades = entidadRepository.buscar(
            documento = documentoLimpio,
            tipoDocumento = tipoDocumentoLimpio,
            nombres = nombresLimpios,
            apellidoPaterno = paternoLimpio,
            apellidoMaterno = maternoLimpio,
            pageable = pageable
        )

        val ids = paginaEntidades.content.mapNotNull { it.id }
        if (ids.isNotEmpty()) {
            entidadRepository.cargarManchas(ids)
        }

        val usuario = usuarioRepository.findByUsuario(username)
        if (usuario != null && paginaEntidades.content.isNotEmpty()) {
            val ahora = java.time.LocalDateTime.now()
            val consultas = paginaEntidades.content.map { entidad ->
                HistorialConsulta(
                    usuario = usuario,
                    entidad = entidad,
                    fechaConsulta = ahora
                )
            }
            historialConsultaRepository.saveAll(consultas)
        }

        val respuestas = paginaEntidades.content.map { it.toResponse() }
        return PageImpl(respuestas, pageable, paginaEntidades.totalElements)
    }

    @Transactional(readOnly = true)
    fun obtenerHistorial(username: String, pageable: Pageable): Page<HistorialConsultaResponse> {
        val vista = visibilidad.de(username)
        val pagina = when (vista.alcance) {
            Alcance.TODO -> historialConsultaRepository.buscarTodas(pageable)
            Alcance.EMPRESA -> historialConsultaRepository.buscarPorEmpresa(requireNotNull(vista.empresa), pageable)
            Alcance.PROPIO -> historialConsultaRepository.buscarPorUsuario(vista.usuario.id!!, pageable)
        }

        return pagina.map { consulta ->
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

    /**
     * Exporta el reporte de la entidad consultada a formato PDF. Se avisa a Sulotec (lista_pdf) recién con la entidad
     * encontrada; la clave es la misma para la misma persona y la misma entidad durante dos minutos (un reintento de la
     * descarga no cobra dos veces). Si el PDF no se puede generar, el consumo se anula.
     */
    @Transactional
    fun exportarPdf(username: String, entidadId: Int): ByteArray {
        usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException("Usuario no encontrado")

        val entidad = entidadRepository.buscarPorIdConDetalle(entidadId)
            ?: throw ResourceNotFoundException("No se encontró el registro solicitado")

        return cobro.ejecutar(TIPO_LISTA_PDF, "PDF de la coincidencia $entidadId", criterios = listOf(entidadId.toString())) {
            listaNegativaPdfService.generar(entidad.toResponse(), username)
        }
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
