package com.security.modules.ml

import com.security.modules.licencias.ContextoLicencia
import com.security.modules.licencias.LicenciaContexto
import com.security.modules.licencias.LicenciaService
import com.security.modules.ml.client.MlClient
import com.security.modules.ml.dto.IndiraChatRequest
import com.security.modules.ml.service.MlService
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.UsuarioRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** El ML decide de quien es cada conversacion y mensaje con el usuario que le manda este backend. */
class MlServiceIndiraTest {
    private val cliente = mockk<MlClient>(relaxed = true)
    private val usuarios = mockk<UsuarioRepository>()
    private val contexto = mockk<LicenciaContexto>()
    private val licencias = mockk<LicenciaService>(relaxed = true)
    private val servicio = MlService(cliente, usuarios, contexto, licencias)

    private val mensajeId = UUID.randomUUID()
    private val uuidDeAna = UUID.nameUUIDFromBytes("security-usuario-7".toByteArray())

    init {
        val ana = mockk<Usuario>()
        every { ana.id } returns 7
        every { usuarios.findByUsuario("ana") } returns ana
    }

    @Test
    fun `el feedback le dice al ML quien lo envia`() {
        servicio.enviarFeedback("ana", mensajeId, "POSITIVO")

        verify(exactly = 1) { cliente.feedback(uuidDeAna, mensajeId, "positivo") }
    }

    @Test
    fun `un feedback no valido no llega al ML`() {
        assertFailsWith<ResponseStatusException> { servicio.enviarFeedback("ana", mensajeId, "regular") }

        verify(exactly = 0) { cliente.feedback(any(), any(), any()) }
    }

    @Test
    fun `una conversacion ajena responde 404 y devuelve el token del mensaje`() {
        every { licencias.activo } returns true
        every { contexto.actual() } returns
            ContextoLicencia(usuario = "ana", empresa = "Peru Luz", empresaUuid = "u-1", esSulotec = false)
        every { cliente.chat(any()) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el recurso solicitado")

        val error = assertFailsWith<ResponseStatusException> {
            servicio.chatear("ana", IndiraChatRequest(mensaje = "hola", conversacionId = UUID.randomUUID()))
        }

        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        verify(exactly = 1) { licencias.anular(any()) }
    }
}
