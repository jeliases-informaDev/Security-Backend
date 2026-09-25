package com.security.modules.matrices.domain.service

import com.security.modules.matrices.domain.enums.NivelSupervision
import com.security.modules.matrices.domain.enums.OperatividadControl
import com.security.modules.matrices.domain.enums.PeriodicidadControl
import com.security.modules.matrices.domain.enums.RespuestaControl
import com.security.modules.matrices.domain.enums.TipoControl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MitigacionControlServiceTest {

    private val service = MitigacionControlService()

    @Test
    fun `directivo preventivo automatico permanente debe devolver 0800`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.DIRECTIVO_AUTOMATICO,
            tipoControl = TipoControl.PREVENTIVO,
            operatividad = OperatividadControl.AUTOMATICO,
            periodicidad = PeriodicidadControl.PERMANENTE,
            frecuenciaOportuna = RespuestaControl.SI,
            seguimientoAdecuado = RespuestaControl.SI
        )

        assertEquals(
            BigDecimal("0.800"),
            resultado
        )
    }

    @Test
    fun `analista detectivo semiautomatico periodico debe devolver 0520`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.ANALISTA_COORDINADOR,
            tipoControl = TipoControl.DETECTIVO,
            operatividad = OperatividadControl.SEMI_AUTOMATICO,
            periodicidad = PeriodicidadControl.PERIODICO,
            frecuenciaOportuna = RespuestaControl.SI,
            seguimientoAdecuado = RespuestaControl.SI
        )

        assertEquals(
            BigDecimal("0.520"),
            resultado
        )
    }

    @Test
    fun `operativo detectivo manual eventual debe devolver 0280`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.OPERATIVO,
            tipoControl = TipoControl.DETECTIVO,
            operatividad = OperatividadControl.MANUAL,
            periodicidad = PeriodicidadControl.EVENTUAL,
            frecuenciaOportuna = RespuestaControl.SI,
            seguimientoAdecuado = RespuestaControl.SI
        )

        assertEquals(
            BigDecimal("0.280"),
            resultado
        )
    }

    @Test
    fun `frecuencia no debe reducir resultado a la mitad`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.DIRECTIVO_AUTOMATICO,
            tipoControl = TipoControl.PREVENTIVO,
            operatividad = OperatividadControl.AUTOMATICO,
            periodicidad = PeriodicidadControl.PERMANENTE,
            frecuenciaOportuna = RespuestaControl.NO,
            seguimientoAdecuado = RespuestaControl.SI
        )

        assertEquals(
            BigDecimal("0.400"),
            resultado
        )
    }

    @Test
    fun `frecuencia y seguimiento no deben reducir resultado a la cuarta parte`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.DIRECTIVO_AUTOMATICO,
            tipoControl = TipoControl.PREVENTIVO,
            operatividad = OperatividadControl.AUTOMATICO,
            periodicidad = PeriodicidadControl.PERMANENTE,
            frecuenciaOportuna = RespuestaControl.NO,
            seguimientoAdecuado = RespuestaControl.NO
        )

        assertEquals(
            BigDecimal("0.200"),
            resultado
        )
    }
}