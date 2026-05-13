package it.vittorioscocca.kidbox.data.passwords.otp

import android.net.Uri

object OtpUriParser {
    fun parse(raw: String): OtpConfig? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("otpauth://", ignoreCase = true)) return null
        val uri = Uri.parse(trimmed)
        val secret = uri.getQueryParameter("secret")?.trim()?.uppercase().orEmpty()
        if (secret.isBlank()) return null
        val period = uri.getQueryParameter("period")?.toIntOrNull() ?: 30
        val digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: 6
        val algorithm = uri.getQueryParameter("algorithm")?.trim()?.uppercase().takeUnless { it.isNullOrBlank() } ?: "SHA1"
        return OtpConfig(
            secret = secret,
            period = period,
            digits = digits,
            algorithm = algorithm,
        )
    }
}
