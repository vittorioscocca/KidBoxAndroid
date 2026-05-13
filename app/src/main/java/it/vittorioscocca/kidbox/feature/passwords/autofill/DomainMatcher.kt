package it.vittorioscocca.kidbox.feature.passwords.autofill

import com.google.common.net.InternetDomainName

/**
 * eTLD+1 tramite Public Suffix List (Guava). Nessun match per substring grezze:
 * si confrontano solo eTLD+1 normalizzati.
 */
object DomainMatcher {
    fun registrableDomain(host: String?): String? {
        val h = host?.trim()?.lowercase()?.removePrefix("www.")
            ?.trim { it == '.' }
            ?.ifBlank { null } ?: return null
        return try {
            val idn = InternetDomainName.from(h)
            if (!idn.hasPublicSuffix()) return null
            idn.topPrivateDomain().toString()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun sameRegistrableDomain(a: String?, b: String?): Boolean {
        val ra = registrableDomain(a) ?: return false
        val rb = registrableDomain(b) ?: return false
        return ra == rb
    }
}
