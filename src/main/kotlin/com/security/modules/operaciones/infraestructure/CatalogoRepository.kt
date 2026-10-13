package com.security.modules.operaciones.infraestructure

import com.security.modules.operaciones.domain.Opcion
import com.security.modules.operaciones.dto.*
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class CatalogoRepository(private val em: EntityManager) {

    fun monedas(): List<MonedaOpcionResponse> =
        em.createNativeQuery("select id, codigo, nombre, simbolo from monedas where activo = 1 order by nombre")
            .resultList.map { fila ->
                val c = fila as Array<*>
                MonedaOpcionResponse((c[0] as Number).toLong(), c[1] as String, c[2] as String, c[3] as String?)
            }

    fun ocupaciones() = opciones("select id, nombre from scoring_ocupacion order by nombre")

    fun tiposDocumento() = opciones("select id, nombre from tipo_documento order by nombre")

    fun paises() = opciones("select id, nombre from pais order by nombre")

    fun provincias(idDepartamento: Long): List<ProvinciaOpcionResponse> =
        em.createNativeQuery("""
            select id, nombre, id_departamento from provincia
            where id_departamento = :idDepartamento order by nombre
        """)
            .setParameter("idDepartamento", idDepartamento)
            .resultList.map { fila ->
                val c = fila as Array<*>
                ProvinciaOpcionResponse((c[0] as Number).toLong(), c[1] as String, (c[2] as Number).toLong())
            }

    fun departamentos(idPais: Long? = null): List<DepartamentoOpcionResponse> {
        val filtro = if (idPais == null) "" else " where id_pais = :idPais"
        val query = em.createNativeQuery("select id, nombre, id_pais from scoring_departamento$filtro order by nombre")
        if (idPais != null) query.setParameter("idPais", idPais)
        return query.resultList.map { fila ->
            val c = fila as Array<*>
            DepartamentoOpcionResponse((c[0] as Number).toLong(), c[1] as String, (c[2] as Number).toLong())
        }
    }

    private fun opciones(sql: String): List<CatalogoOpcionResponse> =
        em.createNativeQuery(sql).resultList.map { fila ->
            val c = fila as Array<*>
            CatalogoOpcionResponse((c[0] as Number).toLong(), c[1] as String)
        }

    fun tiposOperacion(): List<Opcion> = consultar("select id, nombre from tipo_operacion order by nombre")

    fun estados(): List<Opcion> = consultar("select id, descripcion from estado order by descripcion")

    private fun consultar(sql: String): List<Opcion> =
        em.createNativeQuery(sql).resultList.map { fila ->
            val columnas = fila as Array<*>
            Opcion((columnas[0] as Number).toLong(), columnas[1] as String)
        }
}
