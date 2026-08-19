package it.vittorioscocca.kidbox.data.remote.family

import android.content.Context
import android.net.Uri
import it.vittorioscocca.kidbox.util.KBLog

private const val TAG = "PendingFamilyInvite"

/**
 * Invito famiglia arrivato da un App Link, tenuto da parte finché non è
 * possibile applicarlo.
 *
 * Serve perché i due momenti quasi mai coincidono: chi riceve il link di norma
 * **non ha ancora un account**. Tocca il link, l'app si apre sul login, si
 * registra, e solo allora si può entrare in famiglia. Senza un posto dove
 * parcheggiare l'invito, quel percorso perderebbe il segreto per strada e
 * l'utente entrerebbe senza chiave — il difetto che il link deve eliminare.
 *
 * Gemello di `PendingFamilyInvite` su iOS.
 */
data class PendingFamilyInvite(
    val familyId: String,
    val inviteId: String,
    /**
     * Segreto base64url che sblocca la master key. Arriva dal **frammento**
     * dell'URL, quindi non transita dai server né dai bot delle anteprime.
     */
    val secret: String,
) {

    /**
     * Payload equivalente al QR, per riusare [JoinWrapService] senza duplicarne
     * la logica di sblocco della chiave.
     */
    val qrEquivalentPayload: String
        get() = "kidbox://join?familyId=$familyId&inviteId=$inviteId&secret=$secret"

    companion object {
        private const val PREFS = "kb_pending_invite"
        private const val KEY_FAMILY = "familyId"
        private const val KEY_INVITE = "inviteId"
        private const val KEY_SECRET = "secret"

        /**
         * Estrae l'invito da un App Link, o `null` se non è un invito valido.
         *
         * Formato atteso: `https://<dominio>/join?familyId=…&inviteId=…#k=<secret>`
         *
         * Il segreto è cercato **solo** nel frammento: accettarlo anche dalla
         * query renderebbe possibile generare link che lo espongono ai server,
         * vanificando la ragione per cui sta dopo il cancelletto.
         */
        fun parse(uri: Uri): PendingFamilyInvite? {
            val path = uri.path.orEmpty()
            if (path != "/join" && !path.startsWith("/join/")) return null

            val familyId = uri.getQueryParameter("familyId")?.trim().orEmpty()
            val inviteId = uri.getQueryParameter("inviteId")?.trim().orEmpty()
            val secret = secretFromFragment(uri.fragment).orEmpty()

            if (familyId.isEmpty() || inviteId.isEmpty() || secret.isEmpty()) {
                KBLog.data.error("link non valido o senza segreto", TAG)
                return null
            }
            return PendingFamilyInvite(familyId, inviteId, secret)
        }

        /** Estrae l'invito dal payload di un QR (`kidbox://join?...&secret=...`). */
        fun parseQrPayload(raw: String): PendingFamilyInvite? {
            val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "kidbox" || uri.host != "join") return null
            val familyId = uri.getQueryParameter("familyId")?.trim().orEmpty()
            val inviteId = uri.getQueryParameter("inviteId")?.trim().orEmpty()
            val secret = uri.getQueryParameter("secret")?.trim().orEmpty()
            if (familyId.isEmpty() || inviteId.isEmpty() || secret.isEmpty()) return null
            return PendingFamilyInvite(familyId, inviteId, secret)
        }

        /** Legge `k=<secret>` dal frammento, tollerando altri parametri. */
        private fun secretFromFragment(fragment: String?): String? {
            if (fragment.isNullOrEmpty()) return null
            return fragment.split("&")
                .firstOrNull { it.startsWith("k=") }
                ?.removePrefix("k=")
                ?.takeIf { it.isNotEmpty() }
        }

        /**
         * Mette da parte l'invito in attesa di login / app pronta.
         *
         * Su disco e non in memoria: fra il tocco sul link e il join può esserci
         * una registrazione completa, con l'app che passa in background
         * (verifica email, Google) e può essere terminata dal sistema.
         */
        fun store(context: Context, invite: PendingFamilyInvite) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_FAMILY, invite.familyId)
                .putString(KEY_INVITE, invite.inviteId)
                .putString(KEY_SECRET, invite.secret)
                .apply()
            KBLog.data.info("invito salvato familyId=${invite.familyId}", TAG)
        }

        fun load(context: Context): PendingFamilyInvite? {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val familyId = p.getString(KEY_FAMILY, null) ?: return null
            val inviteId = p.getString(KEY_INVITE, null) ?: return null
            val secret = p.getString(KEY_SECRET, null) ?: return null
            return PendingFamilyInvite(familyId, inviteId, secret)
        }

        /**
         * Da chiamare sempre a esito concluso, riuscito o meno: l'invito è
         * monouso e a scadenza, quindi ritentarlo a ogni avvio produrrebbe solo
         * lo stesso errore all'infinito.
         */
        fun clear(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            KBLog.data.debug("invito rimosso", TAG)
        }
    }
}
