package com.security

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SecurityBackendApplication

fun main(args: Array<String>) {
	runApplication<SecurityBackendApplication>(*args)
}