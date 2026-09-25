package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.enums.NivelImpacto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ClasificadorImpactoServiceTest {

    private val service = ClasificadorImpactoService()

    @Test
    fun `monto menor a 4600 debe ser insignificante`() {

        val resultado = service.clasificar(
            BigDecimal("4599.99")
        )

        assertEquals(
            NivelImpacto.INSIGNIFICANTE,
            resultado
        )
    }

    @Test
    fun `monto de 4600 debe ser menor`() {

        val resultado = service.clasificar(
            BigDecimal("4600")
        )

        assertEquals(
            NivelImpacto.MENOR,
            resultado
        )
    }

    @Test
    fun `monto de 230000 debe ser moderado`() {

        val resultado = service.clasificar(
            BigDecimal("230000")
        )

        assertEquals(
            NivelImpacto.MODERADO,
            resultado
        )
    }

    @Test
    fun `monto de 460000 debe ser mayor`() {

        val resultado = service.clasificar(
            BigDecimal("460000")
        )

        assertEquals(
            NivelImpacto.MAYOR,
            resultado
        )
    }

    @Test
    fun `monto de 920000 debe ser catastrofico`() {

        val resultado = service.clasificar(
            BigDecimal("920000")
        )

        assertEquals(
            NivelImpacto.CATASTROFICO,
            resultado
        )
    }

    @Test
    fun `monto negativo debe generar error`() {

        assertThrows(IllegalArgumentException::class.java) {
            service.clasificar(
                BigDecimal("-1")
            )
        }
    }
}