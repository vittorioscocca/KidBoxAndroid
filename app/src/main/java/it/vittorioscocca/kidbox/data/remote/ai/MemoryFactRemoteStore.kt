package it.vittorioscocca.kidbox.data.remote.ai

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBMemoryFactEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteMemoryFactDto(
    val id: String,
    val familyId: String,
    val content: String,
    val categoryRaw: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val sourceConversationId: String?,
)

/**
 * Firestore remote store per i fatti di memoria familiare dell'agente AI.
 * Path: `families/{familyId}/memoryFacts/{factId}` (allineato a iOS).
 */
@Singleton
class MemoryFactRemoteStore @Inject constructor() {

    private val db get() = FirebaseFirestore.getInstance()

    suspend fun fetchAll(familyId: String): List<RemoteMemoryFactDto> = runCatching {
        val snap = db.collection("families")
            .document(familyId)
            .collection("memoryFacts")
            .get()
            .await()
        snap.documents.mapNotNull { doc -> decode(doc.id, doc.data, familyId) }
    }.getOrElse { err ->
        Log.w(TAG, "fetchAll failed familyId=$familyId: ${err.message}")
        emptyList()
    }

    suspend fun upsert(fact: KBMemoryFactEntity) {
        runCatching {
            val createdTs = timestampFromEpochMillis(fact.createdAtEpochMillis)
            val updatedTs = timestampFromEpochMillis(fact.updatedAtEpochMillis)
            val payload = buildMap<String, Any?> {
                put("id", fact.id)
                put("familyId", fact.familyId)
                put("content", fact.content)
                put("categoryRaw", fact.categoryRaw)
                put("createdAt", createdTs)
                put("updatedAt", updatedTs)
                val sid = fact.sourceConversationId
                if (!sid.isNullOrBlank()) {
                    put("sourceConversationId", sid)
                }
            }
            db.collection("families")
                .document(fact.familyId)
                .collection("memoryFacts")
                .document(fact.id)
                .set(payload, SetOptions.merge())
                .await()
        }.onFailure { err ->
            Log.w(TAG, "upsert failed factId=${fact.id}: ${err.message}")
        }
    }

    private fun decode(
        documentId: String,
        data: Map<String, Any>?,
        familyId: String,
    ): RemoteMemoryFactDto? {
        if (data == null) return null
        val content = data["content"] as? String ?: return null
        if (content.isBlank()) return null
        val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: documentId
        val fid = (data["familyId"] as? String)?.takeIf { it.isNotBlank() } ?: familyId
        val categoryRaw = (data["categoryRaw"] as? String)?.takeIf { it.isNotBlank() } ?: "altro"
        val createdAt = epochMillisFromFirestore(data["createdAt"]) ?: 0L
        val updatedAt = epochMillisFromFirestore(data["updatedAt"]) ?: createdAt
        return RemoteMemoryFactDto(
            id = id,
            familyId = fid,
            content = content,
            categoryRaw = categoryRaw,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = updatedAt,
            sourceConversationId = data["sourceConversationId"] as? String,
        )
    }

    private fun epochMillisFromFirestore(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }

    private fun timestampFromEpochMillis(epochMillis: Long): Timestamp {
        val ms = if (epochMillis > 0) epochMillis else System.currentTimeMillis()
        return Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
    }

    private companion object {
        private const val TAG = "MemoryFactRemoteStore"
    }
}
