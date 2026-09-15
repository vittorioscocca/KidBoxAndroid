package it.vittorioscocca.kidbox.util.analytics

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseUser
import it.vittorioscocca.kidbox.util.KBLog
import java.security.MessageDigest

/**
 * Marca come «traffico interno» gli eventi GA4 degli account di test dello
 * sviluppatore, così il filtro «Traffico interno» di GA4 li tiene fuori dai
 * report. Gemello di `InternalTraffic.swift` su iOS: stesso elenco di hash,
 * stesso parametro.
 *
 * Dopo il login si confronta l'hash SHA-256 dell'email con l'elenco compilato
 * qui (mai l'email in chiaro nel binario) e, se corrisponde, si imposta il
 * parametro di default `traffic_type = internal` su tutti gli eventi
 * successivi. Al logout il parametro si toglie. Gli eventi prima del login
 * (`first_open`, `pre_signup_screen_shown`) restano fuori portata.
 *
 * Lo stesso elenco, come uid, sta in `config/internalUsers` su Firestore ed
 * esclude gli stessi account dal rollup analytics e dai report della console.
 */
object InternalTraffic {

    private const val TAG = "InternalTraffic"

    /** SHA-256 esadecimale delle email degli account di test, minuscole. */
    private val emailHashes = setOf(
        "2932b8f0e073118c0ca7484c73ae1e33ba8b9e3011e4b78ae17da4dfb7c74f21",
        "ef18139763e351755d752d5ea05e5efc6aaf7a7dd666a9c559cda03236f540d3",
        "0b45a4dc93984656b8eec293859533018cc6d5dc3bac24d5c554d92bc8fb0486",
        "09ea5430711b0dc611c7eadf90e20a2072af2fc5224f0318ad82501e59d069d9",
        "e4876522f848d46ce31670b18e6e90486e6c90b41107670a199f98d46cff8aa3",
    )

    fun isInternal(user: FirebaseUser?): Boolean {
        if (user == null) return false
        val emails = user.providerData.mapNotNull { it.email } + listOfNotNull(user.email)
        return emails.any { sha256Hex(it) in emailHashes }
    }

    /** Da chiamare a ogni cambio di stato di autenticazione. */
    fun apply(context: Context, user: FirebaseUser?) {
        val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
        if (isInternal(user)) {
            analytics.setDefaultEventParameters(bundleOf("traffic_type" to "internal"))
            KBLog.app.info("account di test: traffic_type=internal", TAG)
        } else {
            analytics.setDefaultEventParameters(null)
        }
    }

    private fun sha256Hex(email: String): String {
        val normalized = email.trim().lowercase()
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
