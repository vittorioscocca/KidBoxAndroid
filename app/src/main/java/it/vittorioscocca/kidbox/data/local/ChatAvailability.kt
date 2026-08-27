package it.vittorioscocca.kidbox.data.local

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Interruttore della chat di famiglia.
 *
 * È una preferenza **dell'account**, non del telefono: vive su
 * `users/{uid}.appPrefs.chatEnabled` e segue l'utente su tutti i suoi
 * dispositivi, Android e iOS insieme — stesso trattamento delle preferenze di
 * notifica in `users/{uid}.notificationPrefs`.
 *
 * Le `SharedPreferences` restano solo come **cache locale**: servono a disegnare
 * la Home subito e a funzionare offline.
 *
 * Sta in un `object` con uno [StateFlow] come `CurrentPlanStore` perché lo
 * leggono anche punti fuori dal grafo Hilt di una schermata — la lista delle
 * feature di Home è una funzione pura, non un ViewModel.
 *
 * Gemello di `KBChatAvailability` su iOS, stesso campo Firestore.
 */
object ChatAvailability {

    private const val PREFS_FILE = "kidbox_prefs"
    private const val KEY_CHAT_ENABLED = "kb_chatEnabled"
    private const val PREFS_FIELD = "appPrefs"
    private const val CHAT_FIELD = "chatEnabled"
    private const val NOTIFY_FIELD = "notifyOnNewMessages"

    /**
     * Ricorda che le notifiche dei messaggi le abbiamo spente **noi** insieme
     * alla chat: serve a non riaccenderle a chi le aveva già disattivate.
     */
    private const val KEY_NOTIFY_PAUSED = "kb_chatNotificationsPausedByChatOff"

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    val isEnabled: Boolean get() = _enabled.value

    /** Da `KidBoxApplication.onCreate()`: allinea lo stato alla cache locale. */
    fun init(context: Context) {
        _enabled.value = prefs(context).getBoolean(KEY_CHAT_ENABLED, true)
    }

    /**
     * Allinea cache e stato al valore sull'account.
     *
     * Va chiamata al login e a ogni ingresso in Impostazioni → Messaggi: è ciò
     * che fa arrivare su questo telefono la scelta fatta su un altro dispositivo.
     */
    suspend fun refreshFromRemote(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            val snap = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            val remotePrefs = snap.get(PREFS_FIELD) as? Map<*, *>
            // Campo assente = mai toccato: la chat resta attiva.
            (remotePrefs?.get(CHAT_FIELD) as? Boolean) ?: true
        }.onSuccess { remote ->
            prefs(context).edit().putBoolean(KEY_CHAT_ENABLED, remote).apply()
            _enabled.value = remote
        }.onFailure {
            // Offline: si tiene la cache, che è già il valore giusto dell'ultima volta.
            KBLog.data.debug("ChatAvailability refresh fallito: ${it.message}", "Chat")
        }
    }

    /** Aggiorna prima la cache — così l'interfaccia reagisce subito — poi l'account. */
    suspend fun set(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHAT_ENABLED, enabled).apply()
        _enabled.value = enabled
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            FirebaseFirestore.getInstance().collection("users").document(uid).set(
                mapOf(PREFS_FIELD to mapOf(CHAT_FIELD to enabled)),
                SetOptions.merge(),
            ).await()
        }.onFailure {
            KBLog.data.error("ChatAvailability salvataggio fallito: ${it.message}", "Chat")
        }
        syncMessageNotifications(context, uid, enabled)
    }

    /**
     * Con la chat spenta una notifica di messaggio porterebbe a una schermata che
     * si rifiuta di aprirsi: si spengono insieme.
     *
     * Riaccendendo la chat le notifiche tornano **solo se le avevamo spente noi**.
     * Chi le aveva già disattivate per conto suo se le ritrova disattivate.
     */
    private suspend fun syncMessageNotifications(context: Context, uid: String, chatEnabled: Boolean) {
        val local = prefs(context)
        val doc = FirebaseFirestore.getInstance().collection("users").document(uid)
        runCatching {
            if (!chatEnabled) {
                val snap = doc.get().await()
                val notifPrefs = snap.get("notificationPrefs") as? Map<*, *>
                val wasOn = (notifPrefs?.get(NOTIFY_FIELD) as? Boolean) ?: true
                if (!wasOn) return
                local.edit().putBoolean(KEY_NOTIFY_PAUSED, true).apply()
                doc.set(mapOf("notificationPrefs" to mapOf(NOTIFY_FIELD to false)), SetOptions.merge()).await()
                KBLog.data.info("Chat disattivata: spente anche le notifiche dei messaggi", "Chat")
            } else if (local.getBoolean(KEY_NOTIFY_PAUSED, false)) {
                local.edit().putBoolean(KEY_NOTIFY_PAUSED, false).apply()
                doc.set(mapOf("notificationPrefs" to mapOf(NOTIFY_FIELD to true)), SetOptions.merge()).await()
                KBLog.data.info("Chat riattivata: riaccese le notifiche dei messaggi", "Chat")
            }
        }.onFailure {
            KBLog.data.error("Sync notifiche chat fallito: ${it.message}", "Chat")
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
}
