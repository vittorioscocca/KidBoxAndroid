package it.vittorioscocca.kidbox.data.health.fitness

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
 * Sync del piano fitness su Firestore: lo stesso utente ritrova piano e stato
 * delle sedute su un altro device, e l'eliminazione vale ovunque.
 *
 * Percorso: `users/{uid}/fitnessPlans/{childId}`
 *
 * Sta sotto `users` e non sotto `families` per lo stesso motivo del piano
 * alimentare: contiene peso, infortuni e adattamenti clinici di chi lo genera.
 *
 * Il documento viaggia come JSON in un singolo campo `payload`: la struttura
 * (settimane, sedute, esercizi, stati) è annidata e non guadagna nulla a essere
 * esplosa in campi Firestore, che nessuna query interroga.
 */
@Singleton
class FitnessPlanRemoteStore @Inject constructor() {

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

        data class Plan(val document: FitnessPlanDocument) : Remote
    }

    private fun ref(childId: String) = auth.currentUser?.uid?.let { uid ->
        db.collection("users").document(uid)
            .collection("fitnessPlans").document(childId)
    }

    suspend fun upsert(childId: String, document: FitnessPlanDocument): Boolean {
        val ref = ref(childId) ?: return false
        return runCatching {
            ref.set(
                mapOf(
                    "childId" to childId,
                    "subjectName" to document.subjectName,
                    "payload" to FitnessPlanJson.encode(document),
                    "generatedAt" to Timestamp(Date(document.generatedAtEpochMillis)),
                    "startDate" to Timestamp(Date(document.startDateEpochMillis)),
                    "messageUnitsConsumed" to document.messageUnitsConsumed,
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
                    "payload" to "",
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

        val payload = snap.getString("payload").orEmpty()
        if (payload.isBlank()) return Remote.None
        val document = FitnessPlanJson.decode(payload) ?: return Remote.None
        return Remote.Plan(document)
    }
}
