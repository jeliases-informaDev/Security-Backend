package com.security.shared.security

import java.time.OffsetDateTime

fun buildErrorJson(status: Int, error: String, message: String, path: String): String {
    return """
        {"timestamp":"${OffsetDateTime.now()}","status":$status,"error":"${escapeJson(error)}","message":"${escapeJson(message)}","path":"${escapeJson(path)}"}
    """.trimIndent()
}

private fun escapeJson(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
}
