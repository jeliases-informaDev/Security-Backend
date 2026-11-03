package com.security

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.runApplication

@EnableScheduling
@SpringBootApplication
class SecurityBackendApplication

fun main(args: Array<String>) {
	runApplication<SecurityBackendApplication>(*args)
}

