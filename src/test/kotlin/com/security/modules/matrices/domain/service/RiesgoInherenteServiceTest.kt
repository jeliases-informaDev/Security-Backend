package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.enums.NivelImpacto
import com.security.modules.matrices.domain.enums.NivelProbabilidad
import com.security.modules.matrices.domain.enums.NivelRiesgo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RiesgoInherenteServiceTest {

    private val service = RiesgoInherenteService()

    @Test
    fun `alta y mayor debe devolver alto`() {

        val resultado = service.calcular(
            NivelProbabilidad.ALTA,
            NivelImpacto.MAYOR
        )

        assertEquals(
            NivelRiesgo.ALTO,
            resultado
        )
    }

    @Test
    fun `muy alta y mayor debe devolver muy alto`() {

        val resultado = service.calcular(
            NivelProbabilidad.MUY_ALTA,
            NivelImpacto.MAYOR
        )

        assertEquals(
            NivelRiesgo.MUY_ALTO,
            resultado
        )
    }

    @Test
    fun `baja e insignificante debe devolver minimo`() {

        val resultado = service.calcular(
            NivelProbabilidad.BAJA,
            NivelImpacto.INSIGNIFICANTE
        )

        assertEquals(
            NivelRiesgo.MINIMO,
            resultado
        )
    }

    @Test
    fun `media y catastrofico debe devolver alto`() {

        val resultado = service.calcular(
            NivelProbabilidad.MEDIA,
            NivelImpacto.CATASTROFICO
        )

        assertEquals(
            NivelRiesgo.ALTO,
            resultado
        )
    }

    @Test
    fun `muy baja y menor debe devolver minimo`() {

        val resultado = service.calcular(
            NivelProbabilidad.MUY_BAJA,
            NivelImpacto.MENOR
        )

        assertEquals(
            NivelRiesgo.MINIMO,
            resultado
        )
    }
}