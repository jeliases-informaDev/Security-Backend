package com.security.modules.creditos.domain

/**
 * Acciones que gastan creditos (tokens). Cada una trae su costo inicial; el costo vigente se guarda en la
 * tabla tarifas_credito y lo edita el ADMINISTRADOR. Todo lo que no esta aqui (consultas de lo ya pagado,
 * historiales, catalogos, ediciones) no cuesta.
 */
enum class AccionCredito(val costoInicial: Int, val descripcion: String) {
    LISTA_BUSQUEDA(1, "Búsqueda en listas negativas"),
    SCORING_EVALUAR(2, "Evaluación de scoring"),
    MATRIZ_REGISTRAR(3, "Registro de un análisis de matriz de riesgo"),
    MATRIZ_PDF(1, "Exportar PDF de una matriz de riesgo"),
    DENUNCIA_CREAR(2, "Registro de una denuncia"),
    DENUNCIA_DOCUMENTOS(1, "Envío de documentos a una denuncia"),
    OPERACION_REGISTRAR(2, "Registro de una operación"),
    OPERACION_PDF(1, "Exportar PDF de una operación")
}

/**
 * Estado de una licencia segun su fecha de vencimiento (proceso P4):
 *  - VIGENTE: hasta la fecha de vencimiento (o siempre, si no tiene).
 *  - SOLO_LECTURA: los 7 dias siguientes al vencimiento; se puede consultar, no registrar ni gastar creditos.
 *  - SUSPENDIDA: despues de eso; solo se puede entrar para ver el estado (los datos quedan a salvo).
 */
enum class EstadoLicencia { VIGENTE, SOLO_LECTURA, SUSPENDIDA }

enum class TipoMovimiento {
    /** Gasto de creditos por una accion. */
    CONSUMO,
    /** El ADMINISTRADOR suma creditos a la bolsa de una empresa. */
    RECARGA,
    /** El ADMINISTRADOR cambia la licencia (total, cupo, vencimiento, tolerancia). */
    AJUSTE
}

enum class TipoAviso {
    /** El consumo paso el 80 % de la bolsa. */
    CONSUMO_80,
    /** La bolsa se agoto y se esta usando la tolerancia. */
    EXCESO,
    /** Faltan 7 dias o menos para el vencimiento. */
    POR_VENCER,
    /** La licencia vencio: 7 dias de solo lectura. */
    SOLO_LECTURA,
    /** La licencia se suspendio. */
    SUSPENDIDA
}
