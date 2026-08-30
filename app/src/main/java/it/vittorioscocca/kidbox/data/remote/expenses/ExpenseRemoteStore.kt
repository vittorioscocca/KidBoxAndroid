package it.vittorioscocca.kidbox.data.remote.expenses

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteExpenseDto(
    val id: String,
    val familyId: String,
    val title: String,
    val amount: Double,
    val dateEpochMillis: Long,
    val categoryId: String?,
    val notes: String?,
    val attachedDocumentId: String?,
    val createdByUid: String?,
    val updatedBy: String?,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val isDeleted: Boolean,
)

sealed interface ExpenseRemoteChange {
    data class Upsert(val dto: RemoteExpenseDto) : ExpenseRemoteChange
    data class Remove(val id: String) : ExpenseRemoteChange
}

@Singleton
class ExpenseRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun listenExpenses(
        familyId: String,
        onChange: (List<ExpenseRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration = db.collection("families")
        .document(familyId)
        .collection("expenses")
        .addSnapshotListener(
            MetadataChanges.EXCLUDE,
            EventListener<QuerySnapshot> { snap, err ->
                if (err != null) {
                    onError(err)
                    return@EventListener
                }
                // Gli upsert vengono dal risultato COMPLETO della query
                // (`snap.documents`), non dal delta (`snap.documentChanges`): con la
                // persistenza locale attiva Firestore può riusare una snapshot in cache e
                // farsela confermare "invariata" dal server con un existence filter, senza
                // inviare alcun document_change. In quel caso il delta è vuoto anche se la
                // query ha risultati reali, e in Room non arrivava più nulla. Le rimozioni
                // restano sul delta, dove sono affidabili.
                val upserts = snap?.documents?.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val title = (d["title"] as? String)?.trim().orEmpty()
                    val amount = (d["amount"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val dateEpochMillis = (d["date"] as? Timestamp)?.toDate()?.time ?: return@mapNotNull null
                    if (title.isBlank()) return@mapNotNull null
                    ExpenseRemoteChange.Upsert(
                        RemoteExpenseDto(
                            id = doc.id,
                            familyId = familyId,
                            title = title,
                            amount = amount,
                            dateEpochMillis = dateEpochMillis,
                            categoryId = (d["categoryId"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                            notes = (d["notes"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                            attachedDocumentId = (d["attachedDocumentId"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                            createdByUid = (d["createdByUid"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                            updatedBy = (d["updatedBy"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                            createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                            updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                            isDeleted = d["isDeleted"] as? Boolean ?: false,
                        ),
                    )
                }.orEmpty()
                val removes = snap?.documentChanges
                    ?.filter { it.type == DocumentChange.Type.REMOVED }
                    ?.map { ExpenseRemoteChange.Remove(it.document.id) }
                    .orEmpty()
                val changes = upserts + removes
                if (changes.isNotEmpty()) onChange(changes)
            },
        )

    suspend fun upsertExpense(entity: KBExpenseEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families")
            .document(entity.familyId)
            .collection("expenses")
            .document(entity.id)
            .set(
                mapOf(
                    "id" to entity.id,
                    "familyId" to entity.familyId,
                    "title" to entity.title,
                    "amount" to entity.amount,
                    "date" to timestampFromMillis(entity.dateEpochMillis),
                    "categoryId" to entity.categoryId,
                    "notes" to entity.notes,
                    "attachedDocumentId" to entity.attachedDocumentId,
                    "createdByUid" to entity.createdByUid,
                    "isDeleted" to entity.isDeleted,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to timestampFromMillis(entity.createdAtEpochMillis),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun softDeleteExpense(
        familyId: String,
        expenseId: String,
    ) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families")
            .document(familyId)
            .collection("expenses")
            .document(expenseId)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private fun timestampFromMillis(epochMillis: Long): Timestamp =
        Timestamp(epochMillis / 1000, ((epochMillis % 1000) * 1_000_000).toInt())
}
