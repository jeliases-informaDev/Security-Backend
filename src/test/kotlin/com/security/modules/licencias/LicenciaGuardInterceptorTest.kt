package com.security.modules.licencias

import com.security.modules.licencias.config.LicenciasProperties
import com.security.modules.licencias.dominio.LicenciaRemota
import com.security.modules.licencias.web.LicenciaGuardInterceptor
import com.security.shared.exceptions.GlobalExceptionHandler
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Controlador de mentira: responde "ok" a todo, y lanza los errores de licencia para probar el JSON de C2. */
@RestController
class ControladorDePrueba {
    @RequestMapping("/api/**")
    fun cualquiera(): String = "ok"

    @GetMapping("/prueba/bolsa")
    fun bolsa(): String = throw LicenciaBolsaAgotadaException()

    @GetMapping("/prueba/cupo")
    fun cupo(): String = throw LicenciaSinCupoException(10, 10, LicenciaReglas.ENLACE_LICENCIAS)
}

class LicenciaGuardInterceptorTest {
    private val contexto = mockk<LicenciaContexto>()
    private val licencias = mockk<LicenciaService>()
    private val guard = LicenciaGuardInterceptor(
        LicenciasProperties(url = "https://licencias.test", token = "token-de-prueba"), contexto, licencias
    )
    private val mvc = MockMvcBuilders.standaloneSetup(ControladorDePrueba())
        .addInterceptors(guard)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    private fun entrar(rol: String = "USUARIO", estado: String? = null) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("ana", null, listOf(SimpleGrantedAuthority("ROLE_$rol")))
        every { contexto.de(any()) } returns ContextoLicencia("ana", "Gamma", "u-1", rol == "ADMINISTRADOR")
        every { licencias.licenciaDe("u-1", any()) } returns estado?.let { LicenciaRemota(estado = it) }
    }

    @AfterEach
    fun limpiar() = SecurityContextHolder.clearContext()

    @Test
    fun `solo lectura bloquea una escritura con el JSON de siempre mas code, estado y enlace`() {
        entrar(estado = "SOLO_LECTURA")
        mvc.perform(post("/api/scoring/evaluar"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("LICENCIA_SOLO_LECTURA"))
            .andExpect(jsonPath("$.estado").value("SOLO_LECTURA"))
            .andExpect(jsonPath("$.enlace").value(LicenciaReglas.ENLACE_MI_LICENCIA))
            .andExpect(jsonPath("$.message").value(LicenciaReglas.MENSAJE_SOLO_LECTURA))
            .andExpect(jsonPath("$.path").value("/api/scoring/evaluar"))
    }

    @Test
    fun `solo lectura deja leer, desactivar e invitar`() {
        entrar(estado = "SOLO_LECTURA")
        mvc.perform(get("/api/listas-negativas/buscar")).andExpect(status().isOk)
        mvc.perform(patch("/api/usuarios/5/estado").param("activo", "false")).andExpect(status().isOk)
        mvc.perform(post("/api/usuarios/5/invitar")).andExpect(status().isOk)
        mvc.perform(patch("/api/usuarios/5/estado").param("activo", "true"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("LICENCIA_SOLO_LECTURA"))
    }

    @Test
    fun `suspendida solo deja desactivar`() {
        entrar(estado = "SUSPENDIDA")
        mvc.perform(patch("/api/usuarios/5/estado").param("activo", "false")).andExpect(status().isOk)
        mvc.perform(post("/api/usuarios/5/invitar"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("LICENCIA_SUSPENDIDA"))
        mvc.perform(put("/api/operaciones/9"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.estado").value("SUSPENDIDA"))
    }

    @Test
    fun `cancelada no corta el canal de denuncias ni los calculos de matriz`() {
        entrar(estado = "CANCELADA")
        mvc.perform(post("/api/denuncias")).andExpect(status().isOk)
        mvc.perform(post("/api/denuncias/seguimiento")).andExpect(status().isOk)
        mvc.perform(post("/api/denuncias/ABC123/archivos")).andExpect(status().isOk)
        mvc.perform(post("/api/matrices/calcular-inherente")).andExpect(status().isOk)
        mvc.perform(post("/api/matrices/analisis/registrar"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("LICENCIA_CANCELADA"))
    }

    @Test
    fun `el administrador de Sulotec nunca se bloquea y ni se consulta`() {
        entrar(rol = "ADMINISTRADOR", estado = "CANCELADA")
        mvc.perform(post("/api/scoring/evaluar")).andExpect(status().isOk)
        verify(exactly = 0) { contexto.de(any()) }
        verify(exactly = 0) { licencias.licenciaDe(any(), any()) }
    }

    @Test
    fun `activa, sin licencia o desconocida dejan pasar`() {
        listOf("ACTIVA", "SIN_LICENCIA", null).forEach {
            entrar(estado = it)
            mvc.perform(post("/api/scoring/evaluar")).andExpect(status().isOk)
        }
    }

    @Test
    fun `un error propio inesperado deja pasar`() {
        entrar(estado = "SUSPENDIDA")
        every { licencias.licenciaDe(any(), any()) } throws IllegalStateException("se rompió algo")
        mvc.perform(post("/api/scoring/evaluar")).andExpect(status().isOk)
    }

    @Test
    fun `con las licencias apagadas no se mira nada`() {
        val apagado = LicenciaGuardInterceptor(LicenciasProperties(), contexto, licencias)
        val mvcApagado = MockMvcBuilders.standaloneSetup(ControladorDePrueba()).addInterceptors(apagado).build()
        entrar(estado = "CANCELADA")
        mvcApagado.perform(post("/api/scoring/evaluar")).andExpect(status().isOk)
        verify(exactly = 0) { contexto.de(any()) }
    }

    @Test
    fun `402 y 409 llevan code, enlace y el cupo`() {
        mvc.perform(get("/prueba/bolsa"))
            .andExpect(status().isPaymentRequired)
            .andExpect(jsonPath("$.code").value("LICENCIA_BOLSA_AGOTADA"))
            .andExpect(jsonPath("$.enlace").value(LicenciaReglas.ENLACE_MI_LICENCIA))
            .andExpect(jsonPath("$.message").value(LicenciaReglas.MENSAJE_BOLSA_AGOTADA))
        mvc.perform(get("/prueba/cupo"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("LICENCIA_SIN_CUPO"))
            .andExpect(jsonPath("$.enlace").value(LicenciaReglas.ENLACE_LICENCIAS))
            .andExpect(jsonPath("$.activos").value(10))
            .andExpect(jsonPath("$.usuarios_max").value(10))
            .andExpect(jsonPath("$.message").value(LicenciaReglas.textoCupo(10, 10)))
    }
}
