package it.vittorioscocca.kidbox.data.remote.family

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import it.vittorioscocca.kidbox.util.KBLog
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "InviteReferrerPickup"

/**
 * Recupera un invito toccato **prima** di installare l'app.
 *
 * Il problema, misurato a settembre 2026: chi riceve un link senza avere
 * KidBox arriva su `/join`, installa dallo store, apre l'app — e l'app non sa
 * niente dell'invito, quindi gli propone di creare una famiglia sua. In una
 * settimana: 5 viste della pagina, 2 tap sullo store, 0 join. Tornare sul
 * messaggio e ritoccare il link non lo fa quasi nessuno.
 *
 * Due sorgenti, che insieme ricostruiscono l'invito:
 *
 * 1. **Referrer di Play** — `join.html` costruisce il link dello store con
 *    `referrer=kb_invite=1&familyId=…&inviteId=…`, e Play lo consegna qui al
 *    primo avvio. Porta gli identificativi ma **non il segreto**: il referrer
 *    transita per i server di Google, che ospitano già la chiave di famiglia
 *    avvolta su Firestore — dare loro anche il segreto significherebbe
 *    consegnare le due metà allo stesso soggetto. Per questo il segreto resta
 *    nel frammento dell'URL, che nessun server vede.
 * 2. **Appunti** — la pagina copia il link intero (frammento compreso) un
 *    istante prima di mandare allo store. Da qui esce il segreto.
 *
 * Esiti possibili, in ordine di fortuna:
 * - appunti con il link giusto → invito completo, si entra come se il link
 *   fosse stato toccato ad app installata;
 * - solo referrer → si sa *quale* invito manca e si può dirlo all'utente con
 *   il nome della famiglia, invece del wizard generico;
 * - niente → esattamente il comportamento di prima.
 *
 * Su iOS lo stesso lavoro lo fa `InvitePasteboardPickup.swift` (solo appunti:
 * Apple non ha un equivalente del referrer).
 */
object InviteReferrerPickup {

    private const val PREFS = "kb_invite_pickup"
    private const val KEY_DONE = "referrerChecked"
    private const val KEY_FAMILY = "referrerFamilyId"
    private const val KEY_INVITE = "referrerInviteId"

    /** Invito conosciuto solo per identificativi: manca il segreto. */
    data class PartialInvite(val familyId: String, val inviteId: String)

    /**
     * Da chiamare al primo avvio dopo l'installazione. Silenziosa: non mostra
     * niente e non fallisce mai in modo visibile.
     *
     * Il referrer si chiede una volta sola (Play lo conserva, ma la connessione
     * al servizio costa e il dato non cambia); gli appunti si rileggono a ogni
     * chiamata finché l'invito non è stato applicato, perché l'utente può
     * installare, aprire l'app, e solo dopo tornare a copiare il link.
     */
    suspend fun pickUp(context: Context) {
        if (PendingFamilyInvite.load(context) != null) return

        val partial = referrerInvite(context)
        val fromClipboard = clipboardInvite(context)

        when {
            fromClipboard != null -> {
                // Se il referrer dice un invito diverso da quello negli appunti
                // vince il referrer: è l'invito per cui l'app è stata
                // installata, mentre negli appunti può esserci un link vecchio.
                if (partial == null || partial.inviteId == fromClipboard.inviteId) {
                    PendingFamilyInvite.store(context, fromClipboard)
                    KBLog.data.info("invito recuperato dagli appunti familyId=${fromClipboard.familyId}", TAG)
                } else {
                    KBLog.data.info("appunti con invito diverso dal referrer: ignorati", TAG)
                }
            }
            partial != null ->
                KBLog.data.info("referrer con invito ma senza segreto familyId=${partial.familyId}", TAG)
        }
    }

    /**
     * Invito noto dal referrer ma senza segreto: la UI lo usa per dire «ti
     * hanno invitato nella famiglia X, riapri il link» invece di proporre la
     * creazione di una famiglia.
     */
    fun partialInvite(context: Context): PartialInvite? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val familyId = p.getString(KEY_FAMILY, null)?.takeIf { it.isNotBlank() } ?: return null
        val inviteId = p.getString(KEY_INVITE, null)?.takeIf { it.isNotBlank() } ?: return null
        return PartialInvite(familyId, inviteId)
    }

    /** Da chiamare quando l'invito è stato applicato o abbandonato. */
    fun clearPartial(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_FAMILY).remove(KEY_INVITE).apply()
    }

    // ── Referrer ────────────────────────────────────────────────────────────

    private suspend fun referrerInvite(context: Context): PartialInvite? {
        partialInvite(context)?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return null

        val referrer = runCatching { readReferrer(context) }.getOrNull()
        prefs.edit().putBoolean(KEY_DONE, true).apply()
        if (referrer.isNullOrBlank()) return null

        // Il referrer è una query string: `kb_invite=1&familyId=…&inviteId=…`.
        val parsed = runCatching { Uri.parse("kidbox://referrer?$referrer") }.getOrNull() ?: return null
        if (parsed.getQueryParameter("kb_invite") != "1") return null
        val familyId = parsed.getQueryParameter("familyId")?.trim().orEmpty()
        val inviteId = parsed.getQueryParameter("inviteId")?.trim().orEmpty()
        if (familyId.isEmpty() || inviteId.isEmpty()) return null

        prefs.edit().putString(KEY_FAMILY, familyId).putString(KEY_INVITE, inviteId).apply()
        KBLog.data.info("referrer di installazione con invito familyId=$familyId", TAG)
        return PartialInvite(familyId, inviteId)
    }

    private suspend fun readReferrer(context: Context): String? =
        suspendCancellableCoroutine { cont ->
            val client = InstallReferrerClient.newBuilder(context).build()
            var resumed = false
            fun finish(value: String?) {
                if (resumed) return
                resumed = true
                runCatching { client.endConnection() }
                cont.resume(value)
            }
            runCatching {
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        if (responseCode != InstallReferrerClient.InstallReferrerResponse.OK) {
                            KBLog.data.debug("referrer non disponibile code=$responseCode", TAG)
                            finish(null)
                            return
                        }
                        val value = runCatching { client.installReferrer.installReferrer }.getOrNull()
                        finish(value)
                    }

                    override fun onInstallReferrerServiceDisconnected() = finish(null)
                })
            }.onFailure { finish(null) }
            cont.invokeOnCancellation { runCatching { client.endConnection() } }
        }

    // ── Appunti ─────────────────────────────────────────────────────────────

    private fun clipboardInvite(context: Context): PendingFamilyInvite? {
        val cm = runCatching {
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        }.getOrNull() ?: return null
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return null
        for (i in 0 until clip.itemCount) {
            val text = runCatching { clip.getItemAt(i).coerceToText(context)?.toString() }
                .getOrNull()?.trim().orEmpty()
            if (text.isEmpty() || !text.contains("/join", ignoreCase = true)) continue
            val uri = runCatching { Uri.parse(text) }.getOrNull() ?: continue
            PendingFamilyInvite.parse(uri)?.let { return it }
        }
        return null
    }
}
