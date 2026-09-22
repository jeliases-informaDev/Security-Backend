package com.security.modules.auth.infrastructure

import com.security.modules.auth.domain.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Int> {

    fun findByToken(token: String): PasswordResetToken?

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.usuario.id = :usuarioId AND t.usado = false")
    fun invalidarPendientesDe(@Param("usuarioId") usuarioId: Int)
}
