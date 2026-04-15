package it.vittorioscocca.kidbox.data.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class HomeBadges(
    val chat: Int = 0,
    val documents: Int = 0,
    val photos: Int = 0,
    val location: Int = 0,
    val todos: Int = 0,
    val shopping: Int = 0,
    val notes: Int = 0,
    val calendar: Int = 0,
    val expenses: Int = 0,
)

enum class CounterField(val raw: String) {
    CHAT("chat"),
    DOCUMENTS("documents"),
    PHOTOS("photos"),
    LOCATION("location"),
    TODOS("todos"),
    SHOPPING("shopping"),
    NOTES("notes"),
    CALENDAR("calendar"),
    EXPENSES("expenses"),
}

@Singleton
class HomeBadgeManager @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val _badges = MutableStateFlow(HomeBadges())
    val badges: StateFlow<HomeBadges> = _badges.asStateFlow()

    private var listener: ListenerRegistration? = null
    private var currentFamilyId: String? = null

    fun startListening(familyId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (familyId.isBlank()) return
        if (currentFamilyId == familyId && listener != null) return

        stopListening()
        currentFamilyId = familyId
        listener = db.collection("families")
            .document(familyId)
            .collection("counters")
            .document(uid)
            .addSnapshotListener { snap, _ ->
                val d = snap?.data.orEmpty()
                _badges.value = HomeBadges(
                    chat = d.intValue("chat"),
                    documents = d.intValue("documents"),
                    photos = d.intValue("photos"),
                    location = d.intValue("location"),
                    todos = d.intValue("todos"),
                    shopping = d.intValue("shopping"),
                    notes = d.intValue("notes"),
                    calendar = d.intValue("calendar"),
                    expenses = d.intValue("expenses"),
                )
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
        currentFamilyId = null
        _badges.value = HomeBadges()
    }

    fun clearLocal(field: CounterField) {
        val curr = _badges.value
        _badges.value = when (field) {
            CounterField.CHAT -> curr.copy(chat = 0)
            CounterField.DOCUMENTS -> curr.copy(documents = 0)
            CounterField.PHOTOS -> curr.copy(photos = 0)
            CounterField.LOCATION -> curr.copy(location = 0)
            CounterField.TODOS -> curr.copy(todos = 0)
            CounterField.SHOPPING -> curr.copy(shopping = 0)
            CounterField.NOTES -> curr.copy(notes = 0)
            CounterField.CALENDAR -> curr.copy(calendar = 0)
            CounterField.EXPENSES -> curr.copy(expenses = 0)
        }
    }

    suspend fun resetRemote(familyId: String, field: CounterField) {
        val uid = auth.currentUser?.uid ?: return
        if (familyId.isBlank()) return
        db.collection("families")
            .document(familyId)
            .collection("counters")
            .document(uid)
            .set(
                mapOf(
                    field.raw to 0,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    private fun Map<String, Any>.intValue(key: String): Int =
        when (val value = this[key]) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            is Number -> value.toInt()
            else -> 0
        }
}
