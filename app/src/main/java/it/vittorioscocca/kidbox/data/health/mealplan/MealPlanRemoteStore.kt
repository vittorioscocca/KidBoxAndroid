package it.vittorioscocca.kidbox.data.health.mealplan

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Sync del piano alimentare su Firestore: lo stesso utente ritrova il piano su
 * un altro device, e l'eliminazione vale ovunque.
 *
 * Percorso: `users/{uid}/mealPlans/{childId}`
 *
 * Sta sotto `users` e non sotto `families` di proposito: il piano contiene peso,
 * obiettivo e abitudini alimentari di chi lo genera, e sotto `families` il
 * wildcard delle rules lo renderebbe leggibile a tutta la famiglia.
 *
 * Nessun listener realtime: è un documento singolo, letto all'apertura della
 * schermata e riscritto solo quando il piano viene rigenerato.
 */
@Singleton
class MealPlanRemoteStore @Inject constructor() {

    // Stesso accesso degli altri *RemoteStore: le istanze Firebase non passano
    // da Hilt in questo progetto.
    private val db get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()


    /** Documento remoto già risolto. */
    sealed interface Remote {
        /** Mai sincronizzato. */
        data object None : Remote

        /** Eliminato da un altro device: la copia locale va svuotata. */
        data object Deleted : Remote

        data class Plan(val document: MealPlanDocument) : Remote
    }

    private fun ref(childId: String) = auth.currentUser?.uid?.let { uid ->
        db.collection("users").document(uid)
            .collection("mealPlans").document(childId)
    }

    suspend fun upsert(childId: String, document: MealPlanDocument): Boolean {
        val ref = ref(childId) ?: return false
        val input = document.input
        return runCatching {
            ref.set(
                mapOf(
                    "childId" to childId,
                    "subjectName" to document.subjectName,
                    "text" to document.text,
                    "generatedAt" to Timestamp(Date(document.generatedAtEpochMillis)),
                    "messageUnitsConsumed" to document.messageUnitsConsumed,
                    "goal" to input.goal.name,
                    "activityLevel" to input.activityLevel.name,
                    "preferredFoods" to input.preferredFoods,
                    "avoidedFoods" to input.avoidedFoods,
                    "notes" to input.notes,
                    "manualAgeYears" to input.manualAgeYears,
                    "manualWeightKg" to input.manualWeightKg,
                    "manualHeightCm" to input.manualHeightCm,
                    "isDeleted" to false,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        }.isSuccess
    }

    /**
     * Soft-delete: gli altri device devono poter distinguere "mai sincronizzato"
     * da "eliminato altrove", e un documento cancellato davvero non lo permette.
     */
    suspend fun delete(childId: String): Boolean {
        val ref = ref(childId) ?: return false
        return runCatching {
            ref.set(
                mapOf(
                    "childId" to childId,
                    "isDeleted" to true,
                    "text" to "",
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        }.isSuccess
    }

    suspend fun fetch(childId: String): Remote {
        val ref = ref(childId) ?: return Remote.None
        val snap = runCatching { ref.get().await() }.getOrNull() ?: return Remote.None
        if (!snap.exists()) return Remote.None
        if (snap.getBoolean("isDeleted") == true) return Remote.Deleted

        val text = snap.getString("text").orEmpty()
        val generatedAt = snap.getTimestamp("generatedAt")?.toDate()?.time
        if (text.isBlank() || generatedAt == null) return Remote.None

        val input = MealPlanInput(
            goal = enumOrDefault(snap.getString("goal"), MealPlanGoal.FAT_LOSS),
            activityLevel = enumOrDefault(
                snap.getString("activityLevel"),
                MealPlanActivityLevel.MODERATE,
            ),
            preferredFoods = snap.getString("preferredFoods").orEmpty(),
            avoidedFoods = snap.getString("avoidedFoods").orEmpty(),
            notes = snap.getString("notes").orEmpty(),
            manualAgeYears = snap.getString("manualAgeYears").orEmpty(),
            manualWeightKg = snap.getString("manualWeightKg").orEmpty(),
            manualHeightCm = snap.getString("manualHeightCm").orEmpty(),
        )

        return Remote.Plan(
            MealPlanDocument(
                subjectName = snap.getString("subjectName").orEmpty(),
                input = input,
                text = text,
                generatedAtEpochMillis = generatedAt,
                messageUnitsConsumed = (snap.getLong("messageUnitsConsumed") ?: 0L).toInt(),
            ),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: default
}
