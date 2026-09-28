package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.enums.NivelImpacto
import kotlin.test.Test
import kotlin.test.assertEquals
import java.math.BigDecimal

class ClasificadorImpactoServiceTest {

    private val service =
        ClasificadorImpactoService()

    @Test
    fun `30000 es insignificante`() {

        assertEquals(
            NivelImpacto.INSIGNIFICANTE,
            service.clasificar(
                BigDecimal("30000")
            )
        )
    }

    @Test
    fun `30000 punto 01 es menor`() {

        assertEquals(
            NivelImpacto.MENOR,
            service.clasificar(
                BigDecimal("30000.01")
            )
        )
    }

    @Test
    fun `200000 es menor`() {

        assertEquals(
            NivelImpacto.MENOR,
            service.clasificar(
                BigDecimal("200000")
            )
        )
    }

    @Test
    fun `200000 punto 01 es moderado`() {

        assertEquals(
            NivelImpacto.MODERADO,
            service.clasificar(
                BigDecimal("200000.01")
            )
        )
    }

    @Test
    fun `850000 es moderado`() {

        assertEquals(
            NivelImpacto.MODERADO,
            service.clasificar(
                BigDecimal("850000")
            )
        )
    }

    @Test
    fun `850000 punto 01 es mayor`() {

        assertEquals(
            NivelImpacto.MAYOR,
            service.clasificar(
                BigDecimal("850000.01")
            )
        )
    }

    @Test
    fun `1700000 es mayor`() {

        assertEquals(
            NivelImpacto.MAYOR,
            service.clasificar(
                BigDecimal("1700000")
            )
        )
    }

    @Test
    fun `1700000 punto 01 es catastrofico`() {

        assertEquals(
            NivelImpacto.CATASTROFICO,
            service.clasificar(
                BigDecimal("1700000.01")
            )
        )
    }
}