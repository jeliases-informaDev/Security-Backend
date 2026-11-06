package com.security.modules.operaciones

import com.security.modules.operaciones.FixturesOperaciones.beneficiarioDto
import com.security.modules.operaciones.FixturesOperaciones.catalogos
import com.security.modules.operaciones.FixturesOperaciones.operacionDto
import com.security.modules.operaciones.FixturesOperaciones.personaDto
import com.security.modules.operaciones.FixturesOperaciones.registroViejo
import com.security.modules.operaciones.application.service.CodigosOperaciones
import com.security.modules.operaciones.application.service.FotoOperacion
import com.security.modules.operaciones.application.service.OperacionesException
import com.security.modules.operaciones.application.service.ValidadorOperacion
import com.tuempresa.operaciones.dto.BeneficiarioDto
import com.tuempresa.operaciones.dto.OperacionDto
import com.tuempresa.operaciones.dto.PersonaDto
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.*

class ValidadorOperacionTest {

    private fun validar(
        oficina: String? = "Lima",
        pf: PersonaDto = personaDto(),
        pr: PersonaDto? = null,
        op: OperacionDto = operacionDto(),
        bens: List<BeneficiarioDto> = listOf(beneficiarioDto())
    ) = ValidadorOperacion(catalogos()).validar(oficina, pf, pr, op, bens)

    private fun errores(bloque: () -> Unit): Map<String, String> {
        val e = assertFailsWith<OperacionesException> { bloque() }
        assertEquals(400, e.statusCode.value())
        assertEquals(CodigosOperaciones.DATOS_INVALIDOS, e.code)
        return requireNotNull(e.campos)
    }

    @Test
    fun `un formulario con los datos minimos pasa, limpio y con codigos canonicos`() {
        val f = validar(op = operacionDto().copy(modalidadPago = " transferencia_bancaria ", moneda = "usd"))
        assertEquals("TRANSFERENCIA_BANCARIA", f.operacion.modalidadPago)
        assertEquals("USD", f.operacion.moneda)
        assertEquals(BigDecimal("125800.00"), f.monto)
        assertEquals("Lima", f.oficina)
    }

    @Test
    fun `sin datos minimos responde cada campo que falta`() {
        val campos = errores {
            validar(oficina = " ", pf = PersonaDto(), op = OperacionDto(), bens = emptyList())
        }
        listOf(
            "registro.oficina", "personaFisica.tipoDocumento", "personaFisica.numeroDocumento", "personaFisica.nombres",
            "personaFisica.apellidos", "operacion.fechaOperacion", "operacion.tipoOperacion", "operacion.monto",
            "operacion.modalidadPago", "operacion.moneda", "operacion.origenFondos", "beneficiarios"
        ).forEach { assertTrue(it in campos, "Falta el error de $it: $campos") }
    }

    @Test
    fun `monto cero, con tres decimales o un codigo inexistente no pasan`() {
        assertTrue("operacion.monto" in errores { validar(op = operacionDto().copy(monto = BigDecimal.ZERO)) })
        assertTrue("operacion.monto" in errores { validar(op = operacionDto().copy(monto = BigDecimal("1.001"))) })
        assertTrue("operacion.tipoOperacion" in errores { validar(op = operacionDto().copy(tipoOperacion = "Deposito")) })
        assertTrue("operacion.moneda" in errores { validar(op = operacionDto().copy(moneda = "JPY")) })
        assertTrue("personaFisica.estadoCivil" in errores { validar(pf = personaDto().copy(estadoCivil = "OTRO")) })
    }

    @Test
    fun `otro pide su texto y sin documento pide motivo pero no numero`() {
        val campos = errores {
            validar(
                pf = personaDto().copy(tipoDocumento = "SIN_DOCUMENTO", numeroDocumento = null),
                op = operacionDto().copy(modalidadPago = "OTRO", origenFondos = "OTRO")
            )
        }
        assertEquals(setOf("personaFisica.motivoSinDocumento", "operacion.modalidadPagoTexto", "operacion.origenFondosTexto"), campos.keys)

        val f = validar(
            pf = personaDto().copy(tipoDocumento = "SIN_DOCUMENTO", numeroDocumento = null, motivoSinDocumento = "Extraviado"),
            op = operacionDto().copy(modalidadPago = "OTRO", modalidadPagoTexto = "Permuta", origenFondos = "OTRO", origenFondosTexto = "Premio")
        )
        assertEquals("Extraviado", f.personaFisica.motivoSinDocumento)
        assertEquals("Permuta", f.operacion.modalidadPagoTexto)
    }

    @Test
    fun `representada juridica pide razon social y no nombres, natural pide nombres`() {
        val juridica = PersonaDto(tipoPersona = "JURIDICA", tipoDocumento = "RUC", numeroDocumento = "20000000001")
        assertEquals(setOf("personaRepresentada.razonSocial"), errores { validar(pr = juridica) }.keys)
        val f = validar(pr = juridica.copy(razonSocial = "Inversiones Ficticias S.A.C.", representanteLegal = "Luis Demo Rojas", nombres = "x"))
        assertEquals("JURIDICA", f.personaRepresentada!!.tipoPersona)
        assertEquals("Inversiones Ficticias S.A.C.", f.personaRepresentada!!.razonSocial)

        val natural = PersonaDto(tipoDocumento = "DNI", numeroDocumento = "00000002", razonSocial = "se ignora")
        val campos = errores { validar(pr = natural) }
        assertEquals(setOf("personaRepresentada.nombres", "personaRepresentada.apellidos"), campos.keys)
        assertTrue("personaRepresentada.tipoPersona" in errores { validar(pr = natural.copy(tipoPersona = "OTRA")) })
    }

    @Test
    fun `textos demasiado largos no pasan`() {
        assertEquals("Máximo 500 caracteres.", errores { validar(op = operacionDto().copy(descripcion = "x".repeat(501))) }["operacion.descripcion"])
    }

    @Test
    fun `al editar un registro viejo, lo que no cambia se acepta tal cual`() {
        val viejo = registroViejo()
        val anterior = FotoOperacion.valores(FotoOperacion.campos(viejo, catalogos()))
        val validador = ValidadorOperacion(catalogos(), anterior)
        // Mismo tipo y modalidad escritos a mano, moneda vieja y sin origen de fondos: pasa
        val f = validador.validar(
            "Sede",
            PersonaDto(tipoDocumento = "DNI", numeroDocumento = "00000002", nombres = "Luis", apellidos = "Demo Rojas"),
            null,
            OperacionDto(fechaOperacion = viejo.operacion.fechaOperacion, modalidadPago = "Efectivo", moneda = "PEN — Soles",
                monto = BigDecimal("1250.25"), tipoOperacion = "Deposito"),
            listOf(BeneficiarioDto("Persona Ficticia", "DNI", "00000003"))
        )
        assertEquals("Deposito", f.operacion.tipoOperacion)
        assertNull(f.operacion.origenFondos)

        // Pero un valor nuevo fuera del catalogo no
        val campos = errores {
            ValidadorOperacion(catalogos(), anterior).validar(
                "Sede", PersonaDto(tipoDocumento = "DNI", numeroDocumento = "00000002", nombres = "Luis", apellidos = "Demo Rojas"), null,
                OperacionDto(fechaOperacion = viejo.operacion.fechaOperacion, modalidadPago = "Trueque", moneda = "PEN — Soles",
                    monto = BigDecimal("1250.25"), tipoOperacion = "Deposito"),
                listOf(BeneficiarioDto("Persona Ficticia", "DNI", "00000003"))
            )
        }
        assertEquals(setOf("operacion.modalidadPago"), campos.keys)
    }
}
