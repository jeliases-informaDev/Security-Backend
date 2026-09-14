package com.security

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public")
class PingController {

    @GetMapping("/ping")
    fun ping(): Map<String, String> {
        return mapOf(
            "status" to "OK",
            "message" to "Security Backend API is running.",
            "version" to "1.0.0-MVP"
        )
    }
}