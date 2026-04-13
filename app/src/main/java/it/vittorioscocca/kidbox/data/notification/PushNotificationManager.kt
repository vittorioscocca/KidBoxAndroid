package it.vittorioscocca.kidbox.data.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
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
    }

    private fun defaultEnabled(key: String): Boolean = when (key) {
        PreferenceKeys.NOTIFY_ON_NEW_DOCS -> false
        PreferenceKeys.NOTIFY_ON_LOCATION_SHARING -> false
        else -> true
    }

    object PreferenceKeys {
        const val NOTIFY_ON_NEW_DOCS = "notifyOnNewDocs"
        const val NOTIFY_ON_NEW_MESSAGES = "notifyOnNewMessages"
        const val NOTIFY_ON_LOCATION_SHARING = "notifyOnLocationSharing"
        const val NOTIFY_ON_TODO_ASSIGNED = "notifyOnTodoAssigned"
        const val NOTIFY_ON_NEW_GROCERY_ITEM = "notifyOnNewGroceryItem"
        const val NOTIFY_ON_NEW_NOTE = "notifyOnNewNote"
        const val NOTIFY_ON_NEW_CALENDAR_EVENT = "notifyOnNewCalendarEvent"
        const val NOTIFY_ON_NEW_EXPENSE = "notifyOnNewExpense"

        val all: List<String> = listOf(
            NOTIFY_ON_NEW_DOCS,
            NOTIFY_ON_NEW_MESSAGES,
            NOTIFY_ON_LOCATION_SHARING,
            NOTIFY_ON_TODO_ASSIGNED,
            NOTIFY_ON_NEW_GROCERY_ITEM,
            NOTIFY_ON_NEW_NOTE,
            NOTIFY_ON_NEW_CALENDAR_EVENT,
            NOTIFY_ON_NEW_EXPENSE,
        )
    }
}
