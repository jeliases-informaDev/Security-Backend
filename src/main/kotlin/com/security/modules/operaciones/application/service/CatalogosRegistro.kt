package com.security.modules.operaciones.application.service

import com.security.modules.operaciones.dto.MonedaOpcionResponse
import com.security.modules.operaciones.infraestructure.CatalogoExportacion
import com.security.modules.operaciones.infraestructure.CatalogoOperacionRepository
import com.security.modules.operaciones.infraestructure.CatalogoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Nombres de los catalogos del formulario nuevo (columna catalogo_operaciones.catalogo) y codigos con regla propia. */
object CatalogosOperacion {
    const val TIPO_DOCUMENTO = "TIPO_DOCUMENTO"
    const val ESTADO_CIVIL = "ESTADO_CIVIL"
    const val MODALIDAD_PAGO = "MODALIDAD_PAGO"
    const val ORIGEN_FONDOS = "ORIGEN_FONDOS"
    const val TIPO_OPERACION = "TIPO_OPERACION"
    const val SECTOR = "SECTOR"
    const val SENAL_ALERTA = "SENAL_ALERTA"
    const val FUENTE_SENAL = "FUENTE_SENAL"
    const val FORMATO_EXPORTACION = "FORMATO_EXPORTACION"
    const val NOTA_UMBRAL = "NOTA_UMBRAL"
    // Unico formato con estructura (formato interno de PLAFT, no es una plantilla oficial de la SBS)
    const val FORMATO_DISPOSICION = "DISPOSICION_UIF"
    const val SENAL_OTRA = "OTRA"

    const val SIN_DOCUMENTO = "SIN_DOCUMENTO"
    const val DOCUMENTO_INICIAL = "DNI"
    const val NATURAL = "NATURAL"
    const val JURIDICA = "JURIDICA"
    // Mientras la empresa no tenga sector configurado (entrega B) se usan los tipos de operacion de este sector.
    const val SECTOR_POR_DEFECTO = "INMOBILIARIO"
}

data class OpcionRegistro(
    val catalogo: String,
    val codigo: String,
    val nombre: String,
    val orden: Int,
    val pideTexto: Boolean,
    val sector: String?,
    val tipoBien: String? = null
)

/**
 * Foto de los catalogos para una peticion: valida codigos, da el nombre legible de un valor guardado y reconoce la
 * moneda de los registros viejos ("PEN — Soles", "PEN" o el id "1"). `tabla` resuelve los ids viejos de las tablas
 * de siempre (tipo de documento, pais, ocupacion, departamento, provincia); por defecto deja el valor como esta.
 */
class Catalogos(
    opciones: List<OpcionRegistro>,
    val monedas: List<MonedaOpcionResponse>,
    private val tabla: (CatalogoExportacion, String?) -> String? = { _, valor -> valor }
) {
    private val porCatalogo: Map<String, List<OpcionRegistro>> =
        opciones.groupBy { it.catalogo }.mapValues { (_, lista) -> lista.sortedBy { it.orden } }

    fun de(catalogo: String): List<OpcionRegistro> = porCatalogo[catalogo].orEmpty()

    fun opcion(catalogo: String, valor: String?): OpcionRegistro? {
        val v = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return de(catalogo).firstOrNull { it.codigo.equals(v, ignoreCase = true) }
    }

    /** Nombre para mostrar: el de la opcion si el valor es un codigo; si no (registro viejo), el valor tal cual. */
    fun nombre(catalogo: String, valor: String?): String? =
        opcion(catalogo, valor)?.nombre ?: valor?.trim()?.takeIf { it.isNotEmpty() }

    /** Tipo de documento: codigo nuevo -> su nombre; id viejo -> nombre de la tabla tipo_documento; si no, el valor. */
    fun nombreDocumento(valor: String?): String? =
        opcion(CatalogosOperacion.TIPO_DOCUMENTO, valor)?.nombre ?: resolverTabla(CatalogoExportacion.DOCUMENTO, valor)

    fun resolverTabla(catalogo: CatalogoExportacion, valor: String?): String? {
        val v = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return tabla(catalogo, v)?.takeIf { it.isNotBlank() } ?: v
    }

    fun moneda(valor: String?): MonedaOpcionResponse? {
        val v = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        monedas.firstOrNull { it.codigo.equals(v, ignoreCase = true) }?.let { return it }
        v.toLongOrNull()?.let { id -> return monedas.firstOrNull { it.id == id } }
        val primera = v.split(Regex("[\\s—–-]+")).firstOrNull { it.isNotEmpty() }
        monedas.firstOrNull { it.codigo.equals(primera, ignoreCase = true) }?.let { return it }
        return monedas.firstOrNull { it.nombre.equals(v, ignoreCase = true) }
    }
}

@Service
class CatalogosRegistro(
    private val repository: CatalogoOperacionRepository,
    private val catalogoRepository: CatalogoRepository
) {

    @Transactional(readOnly = true)
    fun cargar(): Catalogos {
        val opciones = repository.findAllByActivoTrueOrderByCatalogoAscOrdenAsc().map {
            OpcionRegistro(it.catalogo, it.codigo, it.nombre, it.orden, it.pideTexto, it.sector, it.tipoBien)
        }
        // Cache por peticion: un mismo id viejo no se consulta dos veces.
        val nombres = mutableMapOf<Pair<CatalogoExportacion, Long>, String?>()
        fun resolver(catalogo: CatalogoExportacion, valor: String?): String? {
            val id = valor?.trim()?.toLongOrNull() ?: return valor
            return nombres.getOrPut(catalogo to id) { catalogoRepository.nombreParaExportacion(catalogo, id) } ?: valor
        }
        return Catalogos(opciones, catalogoRepository.monedas(), ::resolver)
    }
}
