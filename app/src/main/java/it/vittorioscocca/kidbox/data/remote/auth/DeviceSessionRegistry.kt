package it.vittorioscocca.kidbox.data.remote.auth

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.BuildConfig
import it.vittorioscocca.kidbox.util.KBLog
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Un documento per dispositivo in `users/{uid}/sessions/{sessionId}`.
 *
 * Firebase Auth non sa niente dei dispositivi: ognuno ha un refresh token suo,
 * ma lato server non esiste un elenco delle sessioni aperte né un modo di
 * revocarne una sola — `revokeRefreshTokens` vale per tutto l'account. Questo
 * registro è quell'elenco, tenuto da noi.
 *
 * Il logout remoto che ne deriva è **cooperativo**: ogni client ascolta il
 * proprio documento e, quando lo vede sparire, si slogga da sé. Copre il caso
 * reale — il vecchio tablet, il telefono venduto — ma non un'app modificata che
 * decidesse di ignorarlo. Per quello c'è la callable `signOutAllDevices`.
 *
 * Gemello di `KBDeviceSessionRegistry` su iOS.
 */
@Singleton
class DeviceSessionRegistry @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationContext private val appContext: Context,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private var listener: ListenerRegistration? = null
    private var activeUid: String? = null
    private var onRevoked: (() -> Unit)? = null

    /**
     * Identificatore di QUESTA installazione, stabile finché l'app non viene
     * disinstallata. Non è l'identificatore del telefono — non serve che lo
     * sia, basta distinguere una riga dall'altra nell'elenco — e non è un
     * identificatore pubblicitario: non esce di qui.
     *
     * Il documento vive sotto l'uid, quindi stessa installazione e account
     * diversi restano sessioni diverse: se sul telefono si logga un altro
     * familiare, non eredita la sessione di chi c'era prima.
     */
    val installId: String
        get() {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(KEY_INSTALL_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
            val fresh = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALL_ID, fresh).apply()
            return fresh
        }

    /** «Google Pixel 8», «Samsung SM-G991B»: il modello è l'unica etichetta leggibile senza permessi. */
    private val deviceName: String
        get() = listOfNotNull(
            Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() },
            Build.MODEL?.takeIf { it.isNotBlank() },
        ).distinct().joinToString(" ").ifBlank { "Android" }

    /**
     * Registra questa sessione e comincia ad ascoltarla.
     *
     * [onRevoked] scatta quando il documento viene cancellato da un altro
     * dispositivo: il logout vero lo esegue il chiamante, perché comporta
     * ripulire dati locali di cui questo oggetto non deve sapere niente.
     */
    suspend fun start(uid: String, onRevoked: () -> Unit) {
        if (activeUid == uid) return
        stop()
        activeUid = uid
        this.onRevoked = onRevoked

        val ref = db.collection("users").document(uid)
            .collection("sessions").document(installId)

        runCatching {
            ref.set(
                mapOf(
                    "platform" to "android",
                    "deviceName" to deviceName,
                    "osVersion" to "Android ${Build.VERSION.RELEASE}",
                    "appVersion" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        }.onFailure {
            KBLog.app.warning("Registrazione sessione fallita: ${it.message}", "Session")
        }

        // L'ascolto parte comunque: la condizione qui sotto non slogga nessuno
        // se il documento non è mai esistito, quindi una registrazione fallita
        // non si trasforma in un logout.
        observe(ref)
    }

    private fun observe(ref: DocumentReference) {
        var sawDocument = false

        listener = ref.addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) {
                KBLog.app.warning("Listener sessione: ${err.message}", "Session")
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener

            // Solo dal server. Offline, o all'avvio senza rete, Firestore
            // risponde dalla cache: un'assenza da lì non è una prova di
            // cancellazione, e sloggherebbe chi apre l'app in aereo.
            if (snap.metadata.isFromCache) return@addSnapshotListener

            if (snap.exists()) {
                sawDocument = true
                return@addSnapshotListener
            }
            if (!sawDocument) return@addSnapshotListener

            KBLog.app.info("Sessione revocata da un altro dispositivo — logout", "Session")
            val callback = onRevoked
            stop()
            callback?.invoke()
        }
    }

    /** Chiude l'ascolto senza toccare il documento. */
    fun stop() {
        listener?.remove()
        listener = null
        activeUid = null
        onRevoked = null
    }

    /**
     * Logout volontario da questo dispositivo: il documento va tolto, o la
     * sessione resterebbe nell'elenco degli altri per sempre.
     *
     * Va chiamata PRIMA di `auth.signOut()`: dopo, le rules non lascerebbero
     * più scrivere.
     */
    suspend fun stopAndRemove() {
        val uid = activeUid ?: auth.currentUser?.uid
        val id = installId
        stop()
        if (uid == null) return
        runCatching {
            db.collection("users").document(uid)
                .collection("sessions").document(id)
                .delete().await()
        }.onFailure {
            KBLog.app.warning("Rimozione sessione fallita: ${it.message}", "Session")
        }
    }

    private companion object {
        const val PREFS = "kidbox_device_push"
        const val KEY_INSTALL_ID = "kb_installId"
    }
}
