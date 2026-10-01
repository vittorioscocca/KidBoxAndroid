package it.vittorioscocca.kidbox.data.remote.requests

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.crypto.InviteCrypto
import it.vittorioscocca.kidbox.data.remote.family.InviteWrapService
import it.vittorioscocca.kidbox.util.KBLocale
import it.vittorioscocca.kidbox.util.KBLog
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Richieste di famiglia: «Chi prende Marco giovedì?».
 *
 * Chi chiede sceglie i membri da avvisare e, se vuole, manda un link a chi non
 * ha l'app; la prima risposta «Ci penso io» chiude la richiesta e fa nascere
 * il to-do assegnato, visibile a tutta la famiglia.
 *
 * Il client CREA la richiesta e può solo ritirarla finché è aperta (le rules
 * non gli permettono altro). Le risposte passano dalla callable
 * `respondToRequest`, che decide «il primo Io vince» in una transazione e crea
 * il to-do lato server: per questo non c'è niente in Room. Gemello di
 * `FamilyRequestService` su iOS; disegno in `internal/richieste-disegno.md`.
 */
data class FamilyRequest(
    val id: String,
    val familyId: String,
    val title: String,
    val notes: String?,
    val dueAtMillis: Long?,
    val dueHasTime: Boolean,
    val listId: String,
    val childId: String,
    val createdBy: String,
    val recipients: List<String>,
    val status: Status,
    val expiresAtMillis: Long?,
    val responses: List<Response>,
    val claimedByUid: String?,
    val claimedByName: String?,
    val claimedByExternal: Boolean,
    val todoId: String?,
    val hasExternalLink: Boolean,
) {
    enum class Status { OPEN, CLAIMED, EXPIRED, CANCELLED }

    data class Response(
        /** `uid` per i membri, `ext_<id>` per chi risponde dal link. */
        val key: String,
        val answer: String,
        val name: String,
        val isExternal: Boolean,
    ) {
        val isYes: Boolean get() = answer == "yes"
    }

    /**
     * Aperta e non ancora scaduta. Lo scheduler la chiude entro 15 minuti
     * dalla scadenza: nel frattempo non va offerta come aperta.
     */
    val isOpen: Boolean
        get() = status == Status.OPEN && (expiresAtMillis?.let { it > System.currentTimeMillis() } ?: true)

    fun responseOf(key: String?): Response? = key?.let { k -> responses.firstOrNull { it.key == k } }

    companion object {
        fun from(snap: DocumentSnapshot, familyId: String): FamilyRequest? {
            val title = snap.getString("title") ?: return null
            val createdBy = snap.getString("createdBy") ?: return null
            @Suppress("UNCHECKED_CAST")
            val rawResponses = snap.get("responses") as? Map<String, Any?> ?: emptyMap()
            val responses = rawResponses.mapNotNull { (key, value) ->
                val m = value as? Map<*, *> ?: return@mapNotNull null
                val answer = m["answer"] as? String ?: return@mapNotNull null
                Response(
                    key = key,
                    answer = answer,
                    name = m["name"] as? String ?: "",
                    isExternal = m["type"] == "external",
                )
            }.sortedBy { it.name.lowercase() }
            val claimed = snap.get("claimedBy") as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val recipients = (snap.get("recipients") as? List<Any?>)?.filterIsInstance<String>().orEmpty()
            return FamilyRequest(
                id = snap.id,
                familyId = familyId,
                title = title,
                notes = snap.getString("notes")?.trim()?.takeIf { it.isNotEmpty() },
                dueAtMillis = snap.getTimestamp("dueAt")?.toDate()?.time,
                dueHasTime = snap.getBoolean("dueHasTime") ?: true,
                listId = snap.getString("listId").orEmpty(),
                childId = snap.getString("childId").orEmpty(),
                createdBy = createdBy,
                recipients = recipients,
                status = when (snap.getString("status")) {
                    "claimed" -> Status.CLAIMED
                    "expired" -> Status.EXPIRED
                    "cancelled" -> Status.CANCELLED
                    else -> Status.OPEN
                },
                expiresAtMillis = snap.getTimestamp("expiresAt")?.toDate()?.time,
                responses = responses,
                claimedByUid = claimed?.get("uid") as? String,
                claimedByName = claimed?.get("name") as? String,
                claimedByExternal = claimed?.get("type") == "external",
                todoId = snap.getString("todoId"),
                hasExternalLink = snap.get("external") is Map<*, *>,
            )
        }
    }
}

object FamilyRequestRemoteStore {

    private const val TAG = "FamilyRequests"
    const val LINK_BASE_URL = "https://kidboxapp.com/r"
    /** Senza scadenza la richiesta resta aperta due giorni. */
    private const val DEFAULT_TTL_MS = 48L * 3600 * 1000
    /** Le rules accettano al massimo 7 giorni (più un'ora di margine). */
    private const val MAX_TTL_MS = 7L * 24 * 3600 * 1000 - 600_000
    private const val TITLE_MAX = 200
    private const val PREFS = "kb_family_requests"

    /** Chi chiedere, come lo sceglie l'utente nel foglio «Chiedi a…». */
    data class Draft(
        val recipients: List<String> = emptyList(),
        val askOutside: Boolean = false,
        /** Come chi chiede chiama la persona fuori dall'app («Nonna»). */
        val outsideLabel: String = "",
        /** Il link porta anche l'invito alla famiglia: sì di default. */
        val includeInvite: Boolean = true,
    ) {
        val isEmpty: Boolean get() = recipients.isEmpty() && !askOutside
    }

    data class Created(
        val requestId: String,
        val shareLink: String?,
        val shareText: String?,
        val notifiedCount: Int,
    )

    sealed interface Outcome {
        data object ClaimedByMe : Outcome
        data class ClaimedBy(val name: String) : Outcome
        data object Declined : Outcome
        data object Closed : Outcome
    }

    class DueInPastException : IllegalStateException("due in past")

    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance("europe-west1")

    private fun collection(familyId: String) =
        db.collection("families").document(familyId).collection("requests")

    /**
     * Fine della richiesta: alla scadenza del to-do (dopo non ha senso), al
     * massimo 7 giorni; senza scadenza 48 ore. `null` se la scadenza è già
     * passata. Un to-do senza orario resta chiedibile fino a fine giornata.
     */
    fun expiresAt(dueAtMillis: Long?, hasTime: Boolean, now: Long = System.currentTimeMillis()): Long? {
        if (dueAtMillis == null) return now + DEFAULT_TTL_MS
        val cutoff = if (hasTime) {
            dueAtMillis
        } else {
            Instant.ofEpochMilli(dueAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                .plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 60_000
        }
        if (cutoff <= now + 5 * 60_000) return null
        return minOf(cutoff, now + MAX_TTL_MS)
    }

    /**
     * Crea la richiesta. Se si chiede fuori dall'app prepara anche il link,
     * con il token nel frammento (il server ne vede solo l'impronta) e, se
     * scelto, l'invito alla famiglia: lo stesso `createInvite` del foglio
     * «Invita», quindi monouso e valido 7 giorni.
     */
    suspend fun create(
        context: Context,
        familyId: String,
        childId: String,
        listId: String,
        title: String,
        notes: String?,
        isUrgent: Boolean,
        dueAtMillis: Long?,
        dueHasTime: Boolean,
        draft: Draft,
        familyName: String,
        inviterName: String,
    ): Created {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not authenticated")
        val expires = expiresAt(dueAtMillis, dueHasTime) ?: throw DueInPastException()

        val requestId = UUID.randomUUID().toString().uppercase()
        val cleanTitle = title.take(TITLE_MAX)
        val recipients = draft.recipients.filter { it != uid }.distinct().sorted().take(20)

        var external: Map<String, Any>? = null
        var shareLink: String? = null
        if (draft.askOutside) {
            val token = InviteCrypto.toBase64Url(InviteCrypto.randomBytes(32))
            val tokenHash = MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val fields = mutableMapOf<String, Any>(
                "label" to draft.outsideLabel.trim(),
                "tokenHash" to tokenHash,
            )
            var fragment = "t=$token"
            if (draft.includeInvite) {
                val invite = InviteWrapService().createInvite(
                    context = context,
                    familyId = familyId,
                    familyName = familyName,
                    inviterDisplayName = inviterName,
                )
                fields["inviteId"] = invite.inviteId
                fragment += "&k=${invite.secretBase64url}"
            }
            external = fields
            shareLink = "$LINK_BASE_URL?f=$familyId&r=$requestId#$fragment"
        }

        val data = hashMapOf<String, Any?>(
            "kind" to "todo",
            "title" to cleanTitle,
            "notes" to notes,
            "priority" to if (isUrgent) 1 else 0,
            "dueAt" to dueAtMillis?.let { Timestamp(Date(it)) },
            "dueHasTime" to dueHasTime,
            "listId" to listId,
            "childId" to childId,
            "createdBy" to uid,
            "createdVia" to "app",
            "recipients" to recipients,
            "expiresAt" to Timestamp(Date(expires)),
            "status" to "open",
            "external" to external,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        collection(familyId).document(requestId).set(data).await()
        KBLog.app.info(
            "FamilyRequest creata requestId=$requestId destinatari=${recipients.size} fuori=${draft.askOutside}",
            TAG,
        )

        val shareText = shareLink?.let { link ->
            saveShareLink(context, requestId, link)
            shareText(context, cleanTitle, dueAtMillis, dueHasTime, link)
        }
        return Created(requestId, shareLink, shareText, recipients.size)
    }

    fun shareText(context: Context, title: String, dueAtMillis: Long?, dueHasTime: Boolean, link: String): String {
        val what = dueAtMillis?.let { "$title · ${whenText(it, dueHasTime)}" } ?: title
        return context.getString(R.string.requests_share_text, what, link)
    }

    fun whenText(millis: Long, hasTime: Boolean): String {
        val pattern = if (hasTime) "EEE d MMM, HH:mm" else "EEE d MMM"
        return DateTimeFormatter.ofPattern(pattern, KBLocale.current())
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }

    /** «Ci penso io» o «Non posso». Il server decide chi vince e crea il to-do. */
    suspend fun respond(familyId: String, requestId: String, yes: Boolean): Outcome {
        try {
            val result = functions.getHttpsCallable("respondToRequest").call(
                hashMapOf(
                    "familyId" to familyId,
                    "requestId" to requestId,
                    "answer" to if (yes) "yes" else "no",
                ),
            ).await()
            val d = result.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
            val outcome = d["outcome"] as? String
            KBLog.app.info("FamilyRequest risposta requestId=$requestId yes=$yes outcome=$outcome", TAG)
            if (outcome == "declined") return Outcome.Declined
            if (d["status"] == "claimed") {
                if (d["mine"] == true) return Outcome.ClaimedByMe
                val claimed = d["claimedBy"] as? Map<*, *>
                return Outcome.ClaimedBy(claimed?.get("name") as? String ?: "")
            }
            return Outcome.Closed
        } catch (e: FirebaseFunctionsException) {
            val reason = (e.details as? Map<*, *>)?.get("reason") as? String
            // Richiesta sparita o propria: per l'utente è «non più disponibile».
            if (reason == "not_found" || reason == "own_request") return Outcome.Closed
            KBLog.app.error("FamilyRequest risposta fallita requestId=$requestId code=${e.code}", TAG)
            throw e
        }
    }

    /**
     * Ritira una richiesta aperta. Le rules lo permettono solo a chi l'ha
     * fatta e solo finché nessuno l'ha presa.
     */
    suspend fun cancel(context: Context, familyId: String, requestId: String) {
        collection(familyId).document(requestId).update(
            mapOf(
                "status" to "cancelled",
                "cancelledAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        forgetShareLink(context, requestId)
        KBLog.app.info("FamilyRequest ritirata requestId=$requestId", TAG)
    }

    /**
     * Le richieste aperte della famiglia. Dal risultato completo
     * (`snap.documents`), non dal delta: con la persistenza locale il delta può
     * arrivare vuoto anche con risultati veri.
     */
    fun observeOpen(familyId: String): Flow<List<FamilyRequest>> = callbackFlow {
        val registration = collection(familyId)
            .whereEqualTo("status", "open")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    KBLog.app.error("FamilyRequest listener familyId=$familyId err=${error.message}", TAG)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty()
                    .mapNotNull { FamilyRequest.from(it, familyId) }
                    .filter { it.isOpen }
                    .sortedBy { it.dueAtMillis ?: Long.MAX_VALUE }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /** Una richiesta sola, anche chiusa: serve alla schermata aperta da notifica. */
    fun observe(familyId: String, requestId: String): Flow<FamilyRequest?> = callbackFlow {
        val registration = collection(familyId).document(requestId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    KBLog.app.error("FamilyRequest doc listener requestId=$requestId err=${error.message}", TAG)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snap?.takeIf { it.exists() }?.let { FamilyRequest.from(it, familyId) })
            }
        awaitClose { registration.remove() }
    }

    // ── Link salvati sul telefono di chi chiede ─────────────────────────────
    // Il token esiste solo nel link: il server ne ha l'impronta. Per poterlo
    // rimandare («Invia di nuovo il link») lo si tiene su questo telefono.

    fun savedShareLink(context: Context, requestId: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("link_$requestId", null)

    private fun saveShareLink(context: Context, requestId: String, link: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        // Le richieste durano al massimo 7 giorni: ne bastano poche.
        val stale = prefs.all.keys.filter { it.startsWith("link_") }
        if (stale.size > 30) stale.take(stale.size - 30).forEach { editor.remove(it) }
        editor.putString("link_$requestId", link).apply()
    }

    private fun forgetShareLink(context: Context, requestId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("link_$requestId").apply()
    }
}
