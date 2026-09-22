package com.security.shared.exceptions


import java.time.OffsetDateTime

data class ApiErrorResponse(
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String
)