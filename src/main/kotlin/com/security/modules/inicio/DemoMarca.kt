package com.security.modules.inicio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Marca de los datos de demostracion del Inicio: que fila (tipo + id) creo la demo en que empresa. Asi los datos que se
 * ven en pantalla no necesitan llevar «DEMO» en el nombre o el documento, y «Quitar» borra exactamente lo creado.
 * La empresa se guarda en minusculas y sin espacios sobrantes.
 */
@Entity
@Table(name = "inicio_demo_marca", indexes = [Index(name = "idx_inicio_demo_marca_empresa_tipo", columnList = "empresa,tipo")])
class DemoMarca(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "empresa", nullable = false, length = 200)
    var empresa: String = "",

    // CONSULTA, REGISTRO, DENUNCIA, SCORING u OFICIAL
    @Column(name = "tipo", nullable = false, length = 20)
    var tipo: String = "",

    @Column(name = "id_fila", nullable = false)
    var idFila: Long = 0
)

interface DemoMarcaRepository : JpaRepository<DemoMarca, Long> {

    @Query("SELECT m.idFila FROM DemoMarca m WHERE m.empresa = :empresa AND m.tipo = :tipo")
    fun idsDe(@Param("empresa") empresa: String, @Param("tipo") tipo: String): List<Long>

    @Modifying
    @Query("DELETE FROM DemoMarca m WHERE m.empresa = :empresa")
    fun borrarDe(@Param("empresa") empresa: String): Int
}
