package com.security.modules.licencias

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Avisa a Sulotec las acciones de PLAFT que tienen tarifa, con el mismo mecanismo de siempre: se pide permiso ANTES de
 * hacer la acción, si la acción falla después se anula el consumo, si Sulotec no responde queda en la cola, y si
 * Sulotec dice que no se responde 402/403. El costo lo decide Sulotec con su tarifa del tipo; aquí no se escribe ninguno.
 *
 * Sin licencias, sin sesión o con una cuenta sin empresa con UUID no se avisa nada (como en la búsqueda y el scoring).
 * El administrador de Sulotec va marcado hecho_por_sulotec y nunca se bloquea (lo resuelve LicenciaService).
 */
@Service
class CobroAcciones(
    private val contexto: LicenciaContexto,
    private val licencias: LicenciaService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Pide permiso para la acción y devuelve la clave del consumo, o null si no se avisó nada.
     *
     * criterios != null: clave determinista (misma persona, misma acción y mismos datos en 2 minutos = misma clave), para
     * lo que un reintento del navegador repetiría (descargas de PDF). criterios == null: una clave nueva por petición.
     * nuncaBloquea = true: si Sulotec rechaza se deja pasar igual (canal de denuncias: nunca se frena).
     */
    fun pedir(tipo: String, detalle: String?, criterios: List<String?>? = null, nuncaBloquea: Boolean = false): String? {
        if (!licencias.activo) return null
        val actor = contexto.actual() ?: return null
        val uuid = actor.empresaUuid ?: return null
        val clave = if (criterios != null) {
            licencias.claveDeterminista(uuid, actor.usuario, tipo, criterios)
        } else {
            LicenciaReglas.claveAleatoria(uuid, tipo)
        }
        try {
            licencias.consumirComo(actor, tipo, clave, detalle)
        } catch (e: LicenciaBolsaAgotadaException) {
            if (!nuncaBloquea) throw e
            log.info("Sulotec rechazó {} de la empresa {} (bolsa agotada), pero esta acción nunca se frena", tipo, uuid)
        } catch (e: LicenciaBloqueadaException) {
            if (!nuncaBloquea) throw e
            log.info("Sulotec rechazó {} de la empresa {} ({}), pero esta acción nunca se frena", tipo, uuid, e.estado)
        }
        return clave
    }

    /** Pide permiso, ejecuta la acción y, si la acción falla, devuelve el consumo. */
    fun <T> ejecutar(
        tipo: String,
        detalle: String?,
        criterios: List<String?>? = null,
        nuncaBloquea: Boolean = false,
        accion: () -> T
    ): T {
        val clave = pedir(tipo, detalle, criterios, nuncaBloquea)
        return try {
            accion()
        } catch (e: Exception) {
            clave?.let { licencias.anular(it) }
            throw e
        }
    }
}

/** Igual que CobroAcciones.ejecutar; sin cobro (pruebas que arman el servicio a mano) solo ejecuta la acción. */
fun <T> CobroAcciones?.conCobro(
    tipo: String,
    detalle: String?,
    criterios: List<String?>? = null,
    nuncaBloquea: Boolean = false,
    accion: () -> T
): T = if (this == null) accion() else ejecutar(tipo, detalle, criterios, nuncaBloquea, accion)
