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
    fun `preventivo automatico permanente debe devolver 0600`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.DIRECTIVO_AUTOMATICO,
            tipoControl = TipoControl.PREVENTIVO,
            operatividad = OperatividadControl.AUTOMATICO,
            periodicidad = PeriodicidadControl.PERMANENTE,
            frecuenciaOportuna = RespuestaControl.SI,
            seguimientoAdecuado = RespuestaControl.SI
        )

        assertEquals(
            BigDecimal("0.600"),
            resultado
        )
    }

    @Test
    fun `detectivo semiautomatico periodico debe devolver 0380`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.ANALISTA_COORDINADOR,
            tipoControl = TipoControl.DETECTIVO,
            operatividad = OperatividadControl.SEMI_AUTOMATICO,
            periodicidad = PeriodicidadControl.PERIODICO,
            frecuenciaOportuna = RespuestaControl.SI,
            seguimientoAdecuado = RespuestaControl.SI
        )

        assertEquals(
            BigDecimal("0.380"),
            resultado
        )
    }

    @Test
    fun `detectivo manual eventual debe devolver 0220`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.OPERATIVO,
            tipoControl = TipoControl.DETECTIVO,
            operatividad = OperatividadControl.MANUAL,
            periodicidad = PeriodicidadControl.EVENTUAL,
            frecuenciaOportuna = RespuestaControl.SI,
            seguimientoAdecuado = RespuestaControl.SI
        )

        assertEquals(
            BigDecimal("0.220"),
            resultado
        )
    }

    @Test
    fun `frecuencia no y seguimiento si deben aplicar cincuenta por ciento de ejecucion`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.DIRECTIVO_AUTOMATICO,
            tipoControl = TipoControl.PREVENTIVO,
            operatividad = OperatividadControl.AUTOMATICO,
            periodicidad = PeriodicidadControl.PERMANENTE,
            frecuenciaOportuna = RespuestaControl.NO,
            seguimientoAdecuado = RespuestaControl.SI
        )

        assertEquals(
            BigDecimal("0.300"),
            resultado
        )
    }

    @Test
    fun `frecuencia y seguimiento no deben producir mitigacion cero`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.DIRECTIVO_AUTOMATICO,
            tipoControl = TipoControl.PREVENTIVO,
            operatividad = OperatividadControl.AUTOMATICO,
            periodicidad = PeriodicidadControl.PERMANENTE,
            frecuenciaOportuna = RespuestaControl.NO,
            seguimientoAdecuado = RespuestaControl.NO
        )

        assertEquals(
            BigDecimal("0.000"),
            resultado
        )
    }

    @Test
    fun `periodico automatico detectivo debe devolver 0440`() {

        val resultado = service.calcular(
            supervision = NivelSupervision.ANALISTA_COORDINADOR,
            tipoControl = TipoControl.DETECTIVO,
            operatividad = OperatividadControl.AUTOMATICO,
            periodicidad = PeriodicidadControl.PERIODICO,
            frecuenciaOportuna = RespuestaControl.SI,
            seguimientoAdecuado = RespuestaControl.SI
        )

        assertEquals(
            BigDecimal("0.440"),
            resultado
        )
    }
}