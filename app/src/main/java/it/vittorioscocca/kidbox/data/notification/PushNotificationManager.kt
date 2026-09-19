package it.vittorioscocca.kidbox.data.notification

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.AppLanguage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class PushNotificationManager @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationContext private val appContext: Context,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val devicePrefs
        get() = appContext.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)

    /**
     * `true` se QUESTO dispositivo riceve notifiche per l'utente loggato.
     *
     * La scelta è della coppia account+dispositivo: lo stesso utente può
     * volerle spente sul tablet e accese sul telefono, e chi si logga dopo su
     * questo telefono con un altro account parte dalle proprie, accese — la
     * chiave contiene l'uid.
     *
     * Per il server la fonte di verità è `fcmTokens/{token}.enabled`; qui se ne
     * tiene una copia perché il token ruota (reinstallazione, ripristino,
     * cambio account) e con lui sparirebbe la scelta, riaccendendo le notifiche
     * da sole e in silenzio. Mai scritta = accese, come ogni altra preferenza.
     */
    fun isPushEnabledOnThisDevice(): Boolean {
        val uid = auth.currentUser?.uid ?: return true
        return devicePrefs.getBoolean(pushEnabledKey(uid), true)
    }

    /**
     * L'ordine delle due scritture non è indifferente: un fallimento non deve
     * lasciare la copia locale che dice una cosa e il documento del token
     * un'altra, perché è la copia locale a riscriverlo a ogni avvio — e
     * vincerebbe lei, in silenzio.
     */
    suspend fun setPushEnabledOnThisDevice(enabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val key = pushEnabledKey(uid)
        val previous = isPushEnabledOnThisDevice()

        if (enabled) {
            // Accendendo il locale va PRIMA: è `persistFcmToken` a rileggerlo
            // per scrivere il campo.
            devicePrefs.edit().putBoolean(key, true).apply()
            runCatching { registerCurrentFcmToken() }
                .onFailure {
                    devicePrefs.edit().putBoolean(key, previous).apply()
                    throw it
                }
            return
        }

        // Spegnendo, il token NON si cancella: dice dove consegnare, non se.
        // Basta marcarlo, e a tacere pensa il server. Il locale va DOPO, così
        // se la scrittura fallisce resta acceso com'era e il toggle può dire
        // la verità.
        val token = FirebaseMessaging.getInstance().token.await()
        if (token.isNotBlank()) {
            db.collection("users").document(uid)
                .collection("fcmTokens").document(token)
                .set(mapOf("enabled" to false), com.google.firebase.firestore.SetOptions.merge())
                .await()
        }
        devicePrefs.edit().putBoolean(key, false).apply()
    }

    /**
     * Per uid e non per dispositivo e basta: se su questo telefono si logga un
     * altro membro della famiglia, deve partire dalle proprie notifiche,
     * accese, non ereditare lo spegnimento di chi c'era prima.
     */
    private fun pushEnabledKey(uid: String) = "kb_pushEnabled_$uid"

    suspend fun fetchPreferences(): Map<String, Boolean> {
        val uid = auth.currentUser?.uid ?: return PreferenceKeys.all.associateWith { defaultEnabled(it) }
        val snap = db.collection("users").document(uid).get().await()
        val prefs = snap.get("notificationPrefs") as? Map<*, *>
        return buildMap {
            PreferenceKeys.all.forEach { key ->
                val value = prefs?.get(key) as? Boolean
                put(key, value ?: defaultEnabled(key))
            }
        }
    }

    suspend fun setPreference(key: String, enabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("notificationPrefs" to mapOf(key to enabled)),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    suspend fun registerCurrentFcmToken() {
        val token = FirebaseMessaging.getInstance().token.await()
        persistFcmToken(token)
    }

    suspend fun persistFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        if (token.isBlank()) return
        db.collection("users")
            .document(uid)
            .collection("fcmTokens")
            .document(token)
            .set(
                // `enabled` si riscrive a OGNI registrazione, dalla copia
                // locale: il token ruota, e senza questo la scelta di tenere
                // spento il dispositivo sparirebbe con lui, riaccendendo le
                // notifiche in silenzio.
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "enabled" to isPushEnabledOnThisDevice(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()

        // Il token si registra a ogni avvio: è il momento buono per riallineare
        // anche la lingua, così il campo esiste pure per chi non ha mai aperto
        // il selettore o ha cambiato la lingua di sistema fuori dall'app.
        syncNotificationLanguage()
    }

    /**
     * Allinea `users/{uid}.notificationLanguage` alla lingua in uso.
     *
     * Le push arrivano con il testo già scritto — è il sistema a mostrarle
     * quando l'app non gira — quindi a tradurle è il server, che la lingua del
     * device non può vederla. Questo campo è l'unico modo che ha per saperla;
     * se manca, ricade sull'italiano.
     */
    suspend fun syncNotificationLanguage(tag: String = AppLanguage.resolvedTag()) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("notificationLanguage" to tag),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    /**
     * Rimuove un token dal documento `fcmTokens` di uno specifico utente.
     *
     * Usato al cambio account: senza questo il token del dispositivo resta
     * associato all'utente precedente e, se ancora membro della stessa
     * famiglia, continua a ricevere le sue notifiche (incluse quelle dei
     * messaggi che l'utente attuale invia da questo stesso device).
     * Gemello di `removeFCMToken` su iOS.
     */
    suspend fun removeToken(token: String, uid: String) {
        if (token.isBlank()) return
        db.collection("users")
            .document(uid)
            .collection("fcmTokens")
            .document(token)
            .delete()
            .await()
    }

    /** Forza la rotazione del token FCM al cambio account. Gemello di `deleteCurrentFCMToken` su iOS. */
    suspend fun deleteCurrentToken() {
        FirebaseMessaging.getInstance().deleteToken().await()
    }

    /**
     * Tutte le preferenze nascono ATTIVE.
     *
     * Non è una scelta di gusto: è il server a decidere se inviare, e la sua
     * regola (`getUserTokensIfEnabled` in functions/index.js) è "preferenza
     * assente = attiva". Con `false` qui per documenti e posizione,
     * l'interfaccia mostrava spento ciò che invece stava arrivando — l'utente
     * riceveva notifiche che secondo le Impostazioni aveva disattivate.
     */
    private fun defaultEnabled(key: String): Boolean = true

    private companion object {
        const val DEVICE_PREFS = "kidbox_device_push"
    }

    object PreferenceKeys {
        const val NOTIFY_ON_NEW_MESSAGES = "notifyOnNewMessages"
        const val NOTIFY_ON_LOCATION_SHARING = "notifyOnLocationSharing"
        const val NOTIFY_ON_TODO_ASSIGNED = "notifyOnTodoAssigned"
        const val NOTIFY_ON_NEW_GROCERY_ITEM = "notifyOnNewGroceryItem"
        const val NOTIFY_ON_NEW_NOTE = "notifyOnNewNote"
        const val NOTIFY_ON_NEW_CALENDAR_EVENT = "notifyOnNewCalendarEvent"
        const val NOTIFY_ON_NEW_EXPENSE = "notifyOnNewExpense"
        /**
         * Documenti, separati dal Wallet. Erano lo stesso interruttore, e
         * spegnere i biglietti zittiva anche i documenti. Se il campo non c'è,
         * il server ricade su `notifyOnWallet`, così chi aveva già scelto non
         * si ritrova la preferenza ribaltata.
         */
        const val NOTIFY_ON_NEW_DOCUMENT = "notifyOnNewDocument"

        /**
         * Toggle Wallet: biglietti e carte fedeltà. Assorbiva anche i documenti
         * (vecchia `notifyOnNewDocs`), che ora hanno [NOTIFY_ON_NEW_DOCUMENT].
         * Nome identico a iOS: path Firestore `notificationPrefs.notifyOnWallet`.
         */
        const val NOTIFY_ON_WALLET = "notifyOnWallet"

        val all: List<String> = listOf(
            NOTIFY_ON_NEW_MESSAGES,
            NOTIFY_ON_LOCATION_SHARING,
            NOTIFY_ON_TODO_ASSIGNED,
            NOTIFY_ON_NEW_GROCERY_ITEM,
            NOTIFY_ON_NEW_NOTE,
            NOTIFY_ON_NEW_CALENDAR_EVENT,
            NOTIFY_ON_NEW_EXPENSE,
            NOTIFY_ON_NEW_DOCUMENT,
            NOTIFY_ON_WALLET,
        )
    }
}
