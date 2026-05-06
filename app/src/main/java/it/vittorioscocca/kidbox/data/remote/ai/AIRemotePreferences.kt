package it.vittorioscocca.kidbox.data.remote.ai

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class AIRemotePrefs(
    val aiEnabled: Boolean,
    val usageToday: Int,
)

@Singleton
class AIRemotePreferences @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val uid get() = auth.currentUser?.uid

    suspend fun fetch(): AIRemotePrefs? {
        val u = uid ?: return null
        return runCatching {
            val doc = firestore.collection("users").document(u).get().await()
            val notifPrefs = doc.get("notificationPrefs") as? Map<*, *>
            val aiEnabled = notifPrefs?.get("aiEnabled") as? Boolean ?: true
            val aiUsage = doc.get("aiUsage") as? Map<*, *>
            val usageToday = (aiUsage?.get("today") as? Number)?.toInt() ?: 0
            AIRemotePrefs(aiEnabled = aiEnabled, usageToday = usageToday)
        }.getOrNull()
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        val u = uid ?: return
        runCatching {
            firestore.collection("users").document(u)
                .set(mapOf("notificationPrefs" to mapOf("aiEnabled" to enabled)), SetOptions.merge())
                .await()
        }
    }
}
