package it.vittorioscocca.kidbox.data.remote.ai

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendPreference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Preferenze AI per utente su `users/{uid}` (parity iOS NotificationManager). */
data class AIRemotePrefsSnapshot(
    val aiEnabled: Boolean?,
    val healthContextSendPreference: HealthContextSendPreference?,
)

@Singleton
class AIRemotePreferences @Inject constructor() {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun fetch(): AIRemotePrefsSnapshot? {
        val uid = auth.currentUser?.uid ?: return null
        return runCatching {
            val snap = db.collection("users").document(uid).get().await()
            val notificationPrefs = snap.get("notificationPrefs") as? Map<*, *>
            val aiPrefs = snap.get("aiPrefs") as? Map<*, *>
            AIRemotePrefsSnapshot(
                aiEnabled = notificationPrefs?.get("aiEnabled") as? Boolean,
                healthContextSendPreference = (aiPrefs?.get("healthContextSendPreference") as? String)
                    ?.let { HealthContextSendPreference.fromStorage(it) },
            )
        }.getOrNull()
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .set(
                mapOf("notificationPrefs" to mapOf("aiEnabled" to enabled)),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    suspend fun setHealthContextSendPreference(preference: HealthContextSendPreference) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .set(
                mapOf(
                    "aiPrefs" to mapOf(
                        "healthContextSendPreference" to preference.storageValue,
                        "healthContextSendPreferenceUpdatedAt" to FieldValue.serverTimestamp(),
                    ),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }
}
