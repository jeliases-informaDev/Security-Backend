package com.security.modules.denuncias.application.service

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom

@Service
class SeguimientoDenunciaService {

    private val secureRandom =
        SecureRandom()

    private val passwordEncoder =
        BCryptPasswordEncoder()

    /*
     * Evitamos caracteres que puedan confundirse:
     * 0 / O
     * 1 / I
     */
    private val caracteres =
        "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    // =========================================================
    // GENERAR CÓDIGO DE SEGUIMIENTO
    // =========================================================

    fun generarCodigoSeguimiento(): String {

        val codigo =
            generarCadenaAleatoria(
                longitud = 8
            )

        return "DEN-$codigo"
    }

    // =========================================================
    // GENERAR PIN
    // =========================================================

    fun generarPin(): String {

        val bloque1 =
            generarCadenaAleatoria(4)

        val bloque2 =
            generarCadenaAleatoria(4)

        val bloque3 =
            generarCadenaAleatoria(4)

        return "$bloque1-$bloque2-$bloque3"
    }

    // =========================================================
// HASH DEL PIN
// =========================================================

    fun generarHashPin(
        pin: String
    ): String {

        return requireNotNull(
            passwordEncoder.encode(pin)
        ) {
            "No fue posible generar el hash del PIN."
        }
    }

// =========================================================
// VALIDAR PIN
// =========================================================

    fun validarPin(
        pin: String,
        pinHash: String
    ): Boolean {

        return passwordEncoder.matches(
            pin,
            pinHash
        )
    }

    // =========================================================
    // GENERADOR SEGURO
    // =========================================================

    private fun generarCadenaAleatoria(
        longitud: Int
    ): String {

        return buildString {

            repeat(longitud) {

                val posicion =
                    secureRandom.nextInt(
                        caracteres.length
                    )

                append(
                    caracteres[posicion]
                )
            }
        }
    }
}