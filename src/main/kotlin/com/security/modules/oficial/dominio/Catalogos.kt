package com.security.modules.oficial.dominio

/**
 * Opciones del modulo Oficial de Cumplimiento. Los nombres son exactamente los de las fichas «para desarrollo» del diseño
 * (OC 2, OC 3, OC 8) y de los marcos OC 2 y OC 12. El front manda y recibe el codigo (name del enum).
 */
interface ConNombre {
    val nombre: String
    val codigo: String get() = (this as Enum<*>).name
}

enum class TipoCargo(override val nombre: String, val disponible: Boolean) : ConNombre {
    TITULAR("Titular", true),
    ALTERNO("Alterno", false),
    CORPORATIVO("Corporativo", false)
}

enum class Dedicacion(override val nombre: String) : ConNombre {
    EXCLUSIVA("Exclusiva"),
    NO_EXCLUSIVA("No exclusiva")
}

enum class QuienDesigna(override val nombre: String) : ConNombre {
    SUJETO_OBLIGADO("El propio sujeto obligado (persona natural)"),
    DIRECTORIO("Directorio u órgano equivalente"),
    GERENTE_GENERAL("Gerente general"),
    GERENTE("Gerente"),
    TITULAR_GERENTE("Titular-gerente"),
    ADMINISTRADOR("Administrador")
}

/** Art. 7.1: quien designa depende del tipo de empresa. Los textos de «quienDesigna» son los de la tarjeta de OC 0. */
enum class TipoEmpresaOc(override val nombre: String, val quienDesigna: String, val designantes: List<String>) : ConNombre {
    PERSONA_NATURAL("Persona natural", "El propio dueño. Puede ser su propio oficial.", listOf("SUJETO_OBLIGADO")),
    PERSONA_JURIDICA("Persona jurídica", "El directorio u órgano equivalente.", listOf("DIRECTORIO")),
    SIN_DIRECTORIO(
        "Sin directorio", "El gerente general, gerente, titular-gerente o administrador.",
        listOf("GERENTE_GENERAL", "GERENTE", "TITULAR_GERENTE", "ADMINISTRADOR")
    );

    fun permite(designa: QuienDesigna) = designa.name in designantes
}

/** Documento de identidad del oficial (persona natural: sin RUC). */
enum class TipoDocumentoOc(override val nombre: String, val patron: Regex, val mensaje: String) : ConNombre {
    DNI("DNI", Regex("\\d{8}"), "El DNI debe tener 8 dígitos."),
    CE("CE (carné de extranjería)", Regex("[A-Za-z0-9]{6,12}"), "El carné de extranjería debe tener de 6 a 12 letras o dígitos."),
    PASAPORTE("PASAPORTE", Regex("[A-Za-z0-9]{5,12}"), "El pasaporte debe tener de 5 a 12 letras o dígitos.")
}

enum class Vinculo(override val nombre: String) : ConNombre {
    LABORAL("Laboral (planilla)"),
    CONTRACTUAL_DIRECTO("Contractual directo")
}

enum class DocumentoDesignacion(override val nombre: String) : ConNombre {
    ACTA_DIRECTORIO("Acta de sesión de directorio"),
    ACTA_JUNTA("Acta de junta general de accionistas"),
    OTRO("Otro documento que acredite la designación")
}

enum class ResultadoUif(override val nombre: String) : ConNombre {
    EN_EVALUACION("En evaluación"),
    PROCEDENTE("Procedente"),
    NO_PROCEDENTE("No procedente")
}

enum class MotivoRemocion(override val nombre: String, val ayuda: String, val disponible: Boolean) : ConNombre {
    REMOCION_EMPRESA("Remoción por decisión de la empresa", "Exige sustento y la aprobación de quien designó al oficial.", true),
    INCUMPLE_REQUISITO(
        "El oficial dejó de cumplir un requisito",
        "El oficial debe avisarlo por escrito en 5 días hábiles; la empresa lo remueve y lo informa a la UIF.", true
    ),
    VACANCIA("Vacancia del cargo", "Renuncia, fallecimiento u otra causa. No puede durar más de 30 días.", true),
    AUSENCIA_TEMPORAL("Ausencia temporal", "Se activa al alterno hasta por 4 meses, avisando 5 días hábiles antes.", false)
}

enum class AmbitoAdjunto(override val nombre: String, val esDeclaracion: Boolean = false) : ConNombre {
    ACTA_DESIGNACION("Acta o documento de designación"),
    HOJA_DE_VIDA("Hoja de vida del oficial"),
    DOCUMENTO_VINCULO("Documento que acredita el vínculo"),
    DJ_REQUISITOS_FIRMADA("Declaración jurada de requisitos firmada", true),
    DJ_DESIGNANTE_FIRMADA("Declaración jurada de quién designó firmada", true),
    CONSTANCIA_UIF("Constancia de la UIF"),
    ACTA_REMOCION("Acta de la remoción"),
    CONSTANCIA_COMUNICACION("Constancia de una comunicación a la UIF")
}

enum class EstadoDesignacion(val etiqueta: String, val tono: String) {
    PENDIENTE_COMUNICAR("Pendiente de comunicar", "ambar"),
    EN_EVALUACION("En evaluación", "lila"),
    VIGENTE("Vigente", "verde"),
    REMOVIDA("Removida", "rojo");

    val activa get() = this != REMOVIDA
}

enum class EstadoEmpresaOc(val etiqueta: String) {
    SIN_OFICIAL("Sin oficial designado"),
    PENDIENTE_COMUNICAR("Pendiente de comunicar"),
    EN_EVALUACION("En evaluación"),
    VIGENTE("Vigente"),
    VACANTE("Cargo vacante")
}

enum class TipoPendiente { CAMBIO_DATOS, REMOCION }

enum class TipoEvento {
    DESIGNACION_GUARDADA,
    DESIGNACION_CORREGIDA,
    COMUNICACION_PRESENTADA,
    DESIGNACION_PROCEDENTE,
    DESIGNACION_NO_PROCEDENTE,
    CODIGOS_ACTUALIZADOS,
    CAMBIO_DATOS,
    CAMBIO_COMUNICADO,
    REMOCION,
    REMOCION_COMUNICADA
}

enum class UnidadPlazo { DIAS_HABILES, DIAS, MESES }

/** Busca un enum por su codigo, sin distinguir mayusculas ni espacios. Null si no existe. */
inline fun <reified E : Enum<E>> enumDe(valor: String?): E? {
    val limpio = valor?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    return enumValues<E>().firstOrNull { it.name == limpio }
}
