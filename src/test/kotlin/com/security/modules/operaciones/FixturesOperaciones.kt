package com.security.modules.operaciones

import com.security.modules.operaciones.application.service.Catalogos
import com.security.modules.operaciones.application.service.OpcionRegistro
import com.security.modules.operaciones.domain.BeneficiarioEntity
import com.security.modules.operaciones.domain.Operacion
import com.security.modules.operaciones.domain.Persona
import com.security.modules.operaciones.domain.RegistroOperacionesEntity
import com.security.modules.operaciones.dto.MonedaOpcionResponse
import com.security.shared.datos.entities.Usuario
import com.tuempresa.operaciones.dto.BeneficiarioDto
import com.tuempresa.operaciones.dto.OperacionDto
import com.tuempresa.operaciones.dto.PersonaDto
import com.tuempresa.operaciones.dto.RegistrarOperacionRequest
import com.tuempresa.operaciones.dto.RegistroDto
import org.springframework.core.io.ClassPathResource
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Datos de prueba del Registro de Operaciones: nombres claramente ficticios y documentos falsos. */
object FixturesOperaciones {

    val FECHA: LocalDate = LocalDate.of(2026, 11, 5)

    val MONEDAS = listOf(
        MonedaOpcionResponse(1, "PEN", "Soles", "S/"),
        MonedaOpcionResponse(2, "USD", "Dolares americanos", "$"),
        MonedaOpcionResponse(3, "EUR", "Euros", "€")
    )

    /** Las opciones del archivo real del catalogo (el mismo que se siembra). */
    fun opciones(): List<OpcionRegistro> =
        ClassPathResource("operaciones/catalogos-registro.csv").inputStream.bufferedReader(Charsets.UTF_8).useLines { lineas ->
            lineas.drop(1).filter { it.isNotBlank() }.map { l ->
                val c = l.split(";").map { it.trim() }
                OpcionRegistro(c[0], c[1], c[2], c[3].toInt(), c[4].toBoolean(), c.getOrNull(5)?.takeIf { it.isNotEmpty() })
            }.toList()
        }

    fun catalogos(): Catalogos = Catalogos(opciones(), MONEDAS)

    fun usuario(id: Int, nombre: String, empresa: String?, activo: Boolean = true) = Usuario(
        id = id, usuario = nombre, clave = "x", correo = "$nombre@example.test", nombres = "Nombre$id",
        apePat = "Prueba", empresa = empresa, activo = activo
    )

    fun personaDto() = PersonaDto(
        tipoDocumento = "DNI", numeroDocumento = "00000001", nombres = "Ana", apellidos = "Prueba Ejemplo",
        telefono = "900000001", estadoCivil = "SOLTERO"
    )

    fun operacionDto() = OperacionDto(
        fechaOperacion = FECHA, lugar = "Lima", modalidadPago = "TRANSFERENCIA_BANCARIA", moneda = "USD",
        monto = BigDecimal("125800.00"), tipoOperacion = "VENTA_DEPARTAMENTO", origenFondos = "AHORROS",
        numeroComprobante = "F001-000001"
    )

    fun beneficiarioDto() = BeneficiarioDto(nombre = "Inversiones Ficticias S.A.C.", tipoDocumento = "RUC", numeroDocumento = "20000000001")

    fun alta(usuario: String = "ana", usuarioId: String? = "7") = RegistrarOperacionRequest(
        registro = RegistroDto(usuarioId = usuarioId, usuario = usuario, empresa = "Empresa Gamma", oficina = "Lima"),
        personaFisica = personaDto(),
        operacion = operacionDto(),
        beneficiarios = listOf(beneficiarioDto())
    )

    /** Registro guardado con el formato nuevo. */
    fun registro(id: Long = 42, empresa: String = "Empresa Gamma", idUsuario: Int = 7): RegistroOperacionesEntity {
        val persona = Persona().apply {
            this.id = 20; tipoDocumento = "DNI"; numeroDocumento = "00000001"; nombres = "Ana"; apellidos = "Prueba Ejemplo"
            telefono = "900000001"; estadoCivil = "SOLTERO"
        }
        return RegistroOperacionesEntity(
            id = id, numeroRegistro = "RO-2026-000001", idUsuario = idUsuario, empresa = empresa, oficina = "Lima",
            fechaRegistro = FECHA, fechaCreacion = LocalDateTime.of(2026, 11, 5, 10, 42),
            personaFisica = persona, personaRepresentada = null,
            operacion = Operacion(
                id = 9, fechaOperacion = FECHA, lugar = "Lima", modalidadPago = "TRANSFERENCIA_BANCARIA", moneda = "USD",
                monto = BigDecimal("125800.00"), tipoOperacion = "VENTA_DEPARTAMENTO", cuenta1 = null, cuenta2 = null, cuenta3 = null,
                origenFondos = "AHORROS", numeroComprobante = "F001-000001"
            ),
            idUsuarioResponsable = idUsuario,
            beneficiarios = listOf(BeneficiarioEntity(nombre = "Inversiones Ficticias S.A.C.", tipoDocumento = "RUC", numeroDocumento = "20000000001"))
        )
    }

    /** Registro anterior al diseño nuevo: textos libres, moneda "PEN — Soles", sin origen de fondos. */
    fun registroViejo(id: Long = 43): RegistroOperacionesEntity {
        val persona = Persona().apply {
            this.id = 21; tipoDocumento = "DNI"; numeroDocumento = "00000002"; nombres = "Luis"; apellidos = "Demo Rojas"
            fechaNacimiento = LocalDate.of(1980, 1, 1)
        }
        return RegistroOperacionesEntity(
            id = id, numeroRegistro = "OP-20261012-0001", idUsuario = 7, empresa = "Empresa Gamma", oficina = "Sede",
            fechaRegistro = LocalDate.of(2026, 10, 12), fechaCreacion = LocalDateTime.of(2026, 10, 12, 9, 15),
            personaFisica = persona, personaRepresentada = null,
            operacion = Operacion(
                id = 10, fechaOperacion = LocalDate.of(2026, 10, 12), lugar = null, modalidadPago = "Efectivo",
                moneda = "PEN — Soles", monto = BigDecimal("1250.25"), tipoOperacion = "Deposito",
                cuenta1 = null, cuenta2 = null, cuenta3 = null
            ),
            beneficiarios = listOf(BeneficiarioEntity(nombre = "Persona Ficticia", tipoDocumento = "DNI",
                numeroDocumento = "00000003", fechaNacimiento = LocalDate.of(1990, 5, 5)))
        )
    }
}
