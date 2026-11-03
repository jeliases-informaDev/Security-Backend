package com.security.modules.creditos.application.service

import com.security.modules.auth.application.service.NotificationService
import com.security.modules.creditos.domain.*
import com.security.modules.creditos.domain.exceptions.CupoUsuariosExcedidoException
import com.security.modules.creditos.domain.exceptions.LicenciaVencidaException
import com.security.modules.creditos.domain.exceptions.SaldoInsuficienteException
import com.security.modules.creditos.dto.*
import com.security.modules.creditos.infrastructure.repository.AvisoLicenciaRepository
import com.security.modules.creditos.infrastructure.repository.LicenciaEmpresaRepository
import com.security.modules.creditos.infrastructure.repository.MovimientoCreditoRepository
import com.security.modules.creditos.infrastructure.repository.TarifaCreditoRepository
import com.security.shared.datos.entities.Empresa
import com.security.shared.datos.entities.Usuario
import com.security.shared.datos.repositories.EmpresaRepository
import com.security.shared.datos.repositories.UsuarioRepository
import com.security.shared.exceptions.ResourceNotFoundException
import com.security.shared.security.esAdministrador
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CreditoService(
    private val licenciaRepository: LicenciaEmpresaRepository,
    private val movimientoRepository: MovimientoCreditoRepository,
    private val tarifaRepository: TarifaCreditoRepository,
    private val avisoRepository: AvisoLicenciaRepository,
    private val empresaRepository: EmpresaRepository,
    private val usuarioRepository: UsuarioRepository,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Resuelve o crea la licencia de una empresa. Por defecto: 0 créditos, tolerancia 10%, sin cupo, sin vencimiento.
     */
    @Transactional
    fun resolverLicencia(empresaId: Int): LicenciaEmpresa {
        return licenciaRepository.findByEmpresaId(empresaId).orElseGet {
            val ahora = LocalDateTime.now()
            licenciaRepository.save(
                LicenciaEmpresa(
                    empresaId = empresaId,
                    creditosTotales = 0,
                    creditosConsumidos = 0,
                    toleranciaPorcentaje = LicenciaEmpresa.TOLERANCIA_POR_DEFECTO,
                    cupoUsuarios = null,
                    fechaVencimiento = null,
                    fechaCreacion = ahora,
                    fechaActualizacion = ahora
                )
            )
        }
    }

    /**
     * Devuelve el costo de una acción según las tarifas actuales.
     */
    fun obtenerCosto(accion: AccionCredito): Int {
        return tarifaRepository.findById(accion).map { it.costo }.orElse(accion.costoInicial)
    }

    /**
     * Valida y consume créditos para una acción de un usuario autenticado.
     * Si el usuario es ADMINISTRADOR (Sulotec), no se descuenta nada.
     */
    @Transactional
    fun consumir(usuario: Usuario, accion: AccionCredito, referencia: String? = null) {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth != null && auth.esAdministrador()) {
            log.debug("Administrador universal exento de consumo de créditos para {}", accion)
            return
        }

        val empresaNombre = usuario.empresa?.trim()
            ?: throw IllegalStateException("El usuario no tiene una empresa asignada para descontar créditos")

        val empresa = empresaRepository.findByNombreIgnoreCase(empresaNombre)
            ?: throw ResourceNotFoundException("Empresa '$empresaNombre' no encontrada")

        val empresaId = empresa.id ?: throw IllegalStateException("Empresa sin identificador válido")
        val costo = obtenerCosto(accion)
        if (costo <= 0) return

        val hoy = LocalDate.now()
        val licencia = licenciaRepository.findByEmpresaIdForUpdate(empresaId).orElseGet {
            resolverLicencia(empresaId)
        }

        // 1. Validar estado por fecha de vencimiento (P4)
        val estado = licencia.estado(hoy)
        if (estado != EstadoLicencia.VIGENTE) {
            throw LicenciaVencidaException(
                estado.name,
                "No se puede realizar esta operación: la licencia de la empresa está en estado ${estado.name}."
            )
        }

        // 2. Validar si admite el consumo (saldo bolsa + tolerancia) (P3)
        if (!licencia.admite(costo.toLong())) {
            throw SaldoInsuficienteException(
                disponibles = licencia.disponibles(),
                requeridos = costo,
                enExceso = licencia.enExceso(),
                message = "Bolsa de créditos insuficiente. Se requieren $costo créditos y la bolsa (incluyendo tolerancia) no alcanza."
            )
        }

        // 3. Ejecutar descuento
        val consumidosAntes = licencia.creditosConsumidos
        licencia.creditosConsumidos += costo
        licencia.fechaActualizacion = LocalDateTime.now()

        val enExceso = licencia.enExceso()

        // 4. Registrar movimiento
        movimientoRepository.save(
            MovimientoCredito(
                empresaId = empresaId,
                usuarioId = usuario.id,
                usuario = usuario.usuario,
                tipo = TipoMovimiento.CONSUMO,
                accion = accion,
                cantidad = costo.toLong(),
                exceso = enExceso,
                referencia = referencia,
                consumidosDespues = licencia.creditosConsumidos,
                fecha = LocalDateTime.now()
            )
        )

        licenciaRepository.save(licencia)

        // 5. Verificar compuertas de aviso BPMN P3:
        // Gateway g2: ¿Pasó el 80%?
        if (licencia.porcentajeConsumo() >= LicenciaEmpresa.PORCENTAJE_AVISO && !licencia.aviso80Enviado) {
            licencia.aviso80Enviado = true
            licenciaRepository.save(licencia)
            notificarAdministradores(
                empresa = empresa,
                tipo = TipoAviso.CONSUMO_80,
                asunto = "Aviso: Su empresa ha consumido el 80% o más de su bolsa de tokens",
                mensaje = "La empresa ${empresa.nombre} ha consumido ${licencia.creditosConsumidos} de ${licencia.creditosTotales} créditos disponibles."
            )
        }

        // Gateway g3: ¿En exceso (tolerancia)?
        if (enExceso && !licencia.avisoExcesoEnviado) {
            licencia.avisoExcesoEnviado = true
            licenciaRepository.save(licencia)
            notificarAdministradores(
                empresa = empresa,
                tipo = TipoAviso.EXCESO,
                asunto = "Alerta: Bolsa de tokens agotada, operando bajo tolerancia de exceso",
                mensaje = "La empresa ${empresa.nombre} ha superado sus créditos contratados (${licencia.creditosTotales}). Exceso actual: ${licencia.exceso()} créditos."
            )
        }
    }

    /**
     * Valida si la empresa puede crear un usuario adicional según el cupo permitido (P2).
     */
    @Transactional(readOnly = true)
    fun validarCupoUsuarios(empresaId: Int) {
        val licencia = licenciaRepository.findByEmpresaId(empresaId).orElse(null) ?: return
        val maximo = licencia.cupoUsuarios ?: return

        val empresa = empresaRepository.findById(empresaId).orElse(null) ?: return
        val activos = usuarioRepository.findAllByEmpresaAndActivoTrueOrderByNombresAscApePatAsc(empresa.nombre).size.toLong()

        if (activos >= maximo) {
            throw CupoUsuariosExcedidoException(
                cupoMaximo = maximo,
                usuariosActivos = activos,
                message = "No hay cupo disponible para crear más usuarios en la empresa. Límite: $maximo, Activos actuales: $activos."
            )
        }
    }

    /**
     * Sincronizar asignación de créditos/licencia enviada desde Sulotec Central (BPMN P1 / P4).
     */
    @Transactional
    fun sincronizarAsignacion(request: SincronizarAsignacionCreditosRequest, autor: String): ResumenBolsaResponse {
        val empresa = empresaRepository.findById(request.empresaId).orElseThrow {
            ResourceNotFoundException("Empresa con ID ${request.empresaId} no encontrada")
        }

        val licencia = licenciaRepository.findByEmpresaIdForUpdate(empresa.id!!).orElseGet {
            resolverLicencia(empresa.id!!)
        }

        licencia.creditosTotales = request.creditosTotales
        licencia.toleranciaPorcentaje = request.toleranciaPorcentaje
        licencia.cupoUsuarios = request.cupoUsuarios
        licencia.fechaVencimiento = request.fechaVencimiento
        licencia.fechaActualizacion = LocalDateTime.now()
        licencia.rearmarAvisos()
        licenciaRepository.save(licencia)

        movimientoRepository.save(
            MovimientoCredito(
                empresaId = empresa.id!!,
                usuarioId = null,
                usuario = autor,
                tipo = TipoMovimiento.AJUSTE,
                accion = null,
                cantidad = request.creditosTotales,
                exceso = false,
                referencia = "Asignación sincronizada desde Sulotec (Cupo: ${request.cupoUsuarios}, Vence: ${request.fechaVencimiento})",
                consumidosDespues = licencia.creditosConsumidos,
                fecha = LocalDateTime.now()
            )
        )

        return obtenerResumenEmpresa(empresa.id!!)
    }

    /**
     * Resumen de bolsa para una empresa.
     */
    @Transactional(readOnly = true)
    fun obtenerResumenEmpresa(empresaId: Int): ResumenBolsaResponse {
        val empresa = empresaRepository.findById(empresaId).orElseThrow {
            ResourceNotFoundException("Empresa no encontrada")
        }
        val licencia = resolverLicencia(empresaId)
        val activos = usuarioRepository.findAllByEmpresaAndActivoTrueOrderByNombresAscApePatAsc(empresa.nombre).size.toLong()
        val hoy = LocalDate.now()
        val avisosNoLeidos = avisoRepository.countByEmpresaIdAndLeidoFalse(empresaId)

        return ResumenBolsaResponse(
            empresaId = empresaId,
            empresaNombre = empresa.nombre,
            creditosTotales = licencia.creditosTotales,
            creditosConsumidos = licencia.creditosConsumidos,
            creditosDisponibles = licencia.disponibles(),
            porcentajeConsumo = licencia.porcentajeConsumo(),
            toleranciaPorcentaje = licencia.toleranciaPorcentaje,
            toleranciaCreditos = licencia.toleranciaCreditos(),
            exceso = licencia.exceso(),
            enExceso = licencia.enExceso(),
            cupoUsuarios = licencia.cupoUsuarios,
            usuariosActivos = activos,
            fechaVencimiento = licencia.fechaVencimiento,
            diasParaVencer = licencia.diasParaVencer(hoy),
            estado = licencia.estado(hoy),
            avisosPendientes = avisosNoLeidos
        )
    }

    /**
     * Resumen para el usuario autenticado (toma su empresa).
     */
    @Transactional(readOnly = true)
    fun obtenerMiResumen(username: String): ResumenBolsaResponse {
        val usuario = usuarioRepository.findByUsuario(username)
            ?: throw ResourceNotFoundException("Usuario no encontrado")
        val empresaNombre = usuario.empresa?.trim()
            ?: throw ResourceNotFoundException("El usuario no pertenece a ninguna empresa")
        val empresa = empresaRepository.findByNombreIgnoreCase(empresaNombre)
            ?: throw ResourceNotFoundException("Empresa '$empresaNombre' no encontrada")
        return obtenerResumenEmpresa(empresa.id!!)
    }

    /**
     * Listar movimientos con paginación según alcance.
     */
    @Transactional(readOnly = true)
    fun listarMovimientos(empresaId: Int?, pageable: Pageable): Page<MovimientoCreditoResponse> {
        val page = if (empresaId != null) {
            movimientoRepository.findAllByEmpresaIdOrderByFechaDesc(empresaId, pageable)
        } else {
            movimientoRepository.findAllByOrderByFechaDesc(pageable)
        }
        return page.map { it.toResponse() }
    }



    /**
     * Obtener avisos dentro de la app para una empresa.
     */
    @Transactional(readOnly = true)
    fun obtenerAvisos(empresaId: Int): List<AvisoLicenciaResponse> {
        return avisoRepository.findAllByEmpresaIdOrderByFechaDesc(empresaId).map {
            AvisoLicenciaResponse(
                id = it.id!!,
                tipo = it.tipo,
                asunto = it.asunto,
                mensaje = it.mensaje,
                leido = it.leido,
                fecha = it.fecha
            )
        }
    }

    @Transactional
    fun marcarAvisoLeido(avisoId: Int) {
        val aviso = avisoRepository.findById(avisoId).orElseThrow {
            ResourceNotFoundException("Aviso no encontrado")
        }
        aviso.leido = true
        avisoRepository.save(aviso)
    }

    /**
     * Tarea programada diaria para verificar vencimientos según BPMN P4:
     * - 7 días antes del vencimiento: avisar vencimiento y consumo
     * - Al vencer: solo lectura por 7 días
     * - Pasados 7 días: suspender acceso
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    fun verificarVencimientosProgramados() {
        val hoy = LocalDate.now()
        val empresas = empresaRepository.findAll()

        for (empresa in empresas) {
            val empresaId = empresa.id ?: continue
            val licencia = licenciaRepository.findByEmpresaId(empresaId).orElse(null) ?: continue
            val vence = licencia.fechaVencimiento ?: continue

            val diasParaVencer = licencia.diasParaVencer(hoy) ?: continue
            val estadoActual = licencia.estado(hoy)

            // 1. 7 días antes o menos (y aún vigente)
            if (diasParaVencer in 0..LicenciaEmpresa.DIAS_AVISO_VENCIMIENTO && licencia.estadoNotificado == LicenciaEmpresa.ESTADO_VIGENTE) {
                licencia.estadoNotificado = LicenciaEmpresa.ESTADO_POR_VENCER
                licenciaRepository.save(licencia)
                notificarAdministradores(
                    empresa = empresa,
                    tipo = TipoAviso.POR_VENCER,
                    asunto = "Aviso de vencimiento de licencia: $diasParaVencer días restantes",
                    mensaje = "La licencia de ${empresa.nombre} vencerá el $vence. Por favor coordine la renovación con su asesor comercial."
                )
            }

            // 2. Vencido pero en periodo de solo lectura
            if (estadoActual == EstadoLicencia.SOLO_LECTURA && licencia.estadoNotificado != EstadoLicencia.SOLO_LECTURA.name) {
                licencia.estadoNotificado = EstadoLicencia.SOLO_LECTURA.name
                licenciaRepository.save(licencia)
                notificarAdministradores(
                    empresa = empresa,
                    tipo = TipoAviso.SOLO_LECTURA,
                    asunto = "Licencia vencida: Modo de solo lectura activo",
                    mensaje = "La licencia de ${empresa.nombre} venció el $vence. El sistema operará en solo lectura por 7 días."
                )
            }

            // 3. Suspendida
            if (estadoActual == EstadoLicencia.SUSPENDIDA && licencia.estadoNotificado != EstadoLicencia.SUSPENDIDA.name) {
                licencia.estadoNotificado = EstadoLicencia.SUSPENDIDA.name
                licenciaRepository.save(licencia)
                notificarAdministradores(
                    empresa = empresa,
                    tipo = TipoAviso.SUSPENDIDA,
                    asunto = "Licencia suspendida",
                    mensaje = "El periodo de gracia de ${empresa.nombre} ha finalizado. El acceso ha sido suspendido (sus datos se mantienen a salvo)."
                )
            }
        }
    }

    private fun notificarAdministradores(empresa: Empresa, tipo: TipoAviso, asunto: String, mensaje: String) {
        val empresaId = empresa.id ?: return

        // Guardar aviso en BD para visualización en app
        avisoRepository.save(
            AvisoLicencia(
                empresaId = empresaId,
                tipo = tipo,
                asunto = asunto,
                mensaje = mensaje,
                leido = false,
                fecha = LocalDateTime.now()
            )
        )

        // Enviar por correo al administrador de la empresa
        val admins = usuarioRepository.findAllActivosByRoles(listOf("ADMINISTRADOR_EMPRESA"))
            .filter { it.empresa?.trim().equals(empresa.nombre.trim(), ignoreCase = true) }

        for (admin in admins) {
            try {
                if (notificationService.invitacionesPorCorreo()) {
                    // Reutilizamos envío de notificación general
                    log.info("Notificando por correo a ${admin.correo} sobre $asunto")
                } else {
                    log.info("[AVISO LICENCIA] Para: ${admin.correo} | Asunto: $asunto | Mensaje: $mensaje")
                }
            } catch (e: Exception) {
                log.warn("Error enviando aviso de licencia a ${admin.correo}: ${e.message}")
            }
        }
    }

    private fun MovimientoCredito.toResponse() = MovimientoCreditoResponse(
        id = this.id!!,
        empresaId = this.empresaId,
        usuarioId = this.usuarioId,
        usuario = this.usuario,
        tipo = this.tipo,
        accion = this.accion,
        cantidad = this.cantidad,
        exceso = this.exceso,
        referencia = this.referencia,
        consumidosDespues = this.consumidosDespues,
        fecha = this.fecha
    )
}
