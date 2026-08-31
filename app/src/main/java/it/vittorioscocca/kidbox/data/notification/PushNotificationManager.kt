package it.vittorioscocca.kidbox.data.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import it.vittorioscocca.kidbox.data.local.AppLanguage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class PushNotificationManager @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

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
                mapOf(
                    "token" to token,
                    "platform" to "android",
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

    object PreferenceKeys {
        const val NOTIFY_ON_NEW_MESSAGES = "notifyOnNewMessages"
        const val NOTIFY_ON_LOCATION_SHARING = "notifyOnLocationSharing"
        const val NOTIFY_ON_TODO_ASSIGNED = "notifyOnTodoAssigned"
        const val NOTIFY_ON_NEW_GROCERY_ITEM = "notifyOnNewGroceryItem"
        const val NOTIFY_ON_NEW_NOTE = "notifyOnNewNote"
        const val NOTIFY_ON_NEW_CALENDAR_EVENT = "notifyOnNewCalendarEvent"
        const val NOTIFY_ON_NEW_EXPENSE = "notifyOnNewExpense"
        /**
         * Unico toggle Wallet: assorbe le vecchie `notifyOnNewDocs` (che
         * copriva anche i documenti d'identità del Wallet, collezione
         * Firestore condivisa) e `notifyOnNewWalletTicket`. Nome identico a
         * iOS: path Firestore `notificationPrefs.notifyOnWallet`.
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
            NOTIFY_ON_WALLET,
        )
    }
}
