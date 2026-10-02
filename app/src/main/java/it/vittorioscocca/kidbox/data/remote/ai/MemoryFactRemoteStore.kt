package it.vittorioscocca.kidbox.data.remote.ai

import it.vittorioscocca.kidbox.util.KBLog

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.chat.crypto.ChatCryptoService
import it.vittorioscocca.kidbox.data.local.KBFeatureFlags
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
    /** Ancora in chiaro su Firestore: a interruttore acceso va riscritto cifrato. */
    val isLegacyPlain: Boolean = false,
)

/**
 * Firestore remote store per i fatti di memoria familiare dell'agente AI.
 * Path: `families/{familyId}/memoryFacts/{factId}` (allineato a iOS).
 *
 * Cifratura (dal 02/10/2026): il testo può viaggiare cifrato con la chiave di
 * famiglia (`contentEnc`, formato di iOS e web). La lettura capisce entrambi i
 * formati; la scrittura cifra con [KBFeatureFlags.textEncryptionEnabled]
 * acceso, e senza chiave non scrive: mai il chiaro come ripiego.
 */
@Singleton
class MemoryFactRemoteStore @Inject constructor(
    private val crypto: ChatCryptoService,
) {

    private val db get() = FirebaseFirestore.getInstance()

    suspend fun fetchAll(familyId: String): List<RemoteMemoryFactDto> = runCatching {
        val snap = db.collection("families")
            .document(familyId)
            .collection("memoryFacts")
            .get()
            .await()
        snap.documents.mapNotNull { doc -> decode(doc.id, doc.data, familyId) }
            .also { facts ->
                // A interruttore acceso i fatti ancora in chiaro si riscrivono cifrati.
                if (KBFeatureFlags.textEncryptionEnabled.value) {
                    facts.filter { it.isLegacyPlain }.forEach { reencrypt(it) }
                }
            }
    }.getOrElse { err ->
        KBLog.ai.warning("fetchAll failed familyId=$familyId: ${err.message}", TAG)
        emptyList()
    }

    suspend fun upsert(fact: KBMemoryFactEntity) {
        runCatching {
            val createdTs = timestampFromEpochMillis(fact.createdAtEpochMillis)
            val updatedTs = timestampFromEpochMillis(fact.updatedAtEpochMillis)
            val payload = buildMap<String, Any?> {
                put("id", fact.id)
                put("familyId", fact.familyId)
                putContent(fact.content, fact.familyId)
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
            KBLog.ai.warning("upsert failed factId=${fact.id}: ${err.message}", TAG)
        }
    }

    /** Un solo formato per volta; acceso l'interruttore, senza chiave lancia. */
    private fun MutableMap<String, Any?>.putContent(content: String, familyId: String) {
        if (KBFeatureFlags.textEncryptionEnabled.value) {
            put("contentEnc", crypto.encryptStringToBase64(content, familyId))
            put("content", FieldValue.delete())
        } else {
            put("content", content)
            put("contentEnc", FieldValue.delete())
        }
    }

    private suspend fun reencrypt(fact: RemoteMemoryFactDto) {
        runCatching {
            val payload = buildMap<String, Any?> { putContent(fact.content, fact.familyId) }
            db.collection("families")
                .document(fact.familyId)
                .collection("memoryFacts")
                .document(fact.id)
                .set(payload, SetOptions.merge())
                .await()
        }.onFailure { err ->
            KBLog.ai.warning("re-encrypt failed factId=${fact.id}: ${err.javaClass.simpleName}", TAG)
        }
    }

    private fun decode(
        documentId: String,
        data: Map<String, Any>?,
        familyId: String,
    ): RemoteMemoryFactDto? {
        if (data == null) return null
        val fid = (data["familyId"] as? String)?.takeIf { it.isNotBlank() } ?: familyId
        // Cifrato se c'è; un blob che non si apre fa saltare il fatto.
        val enc = (data["contentEnc"] as? String)?.takeIf { it.isNotBlank() }
        val content = if (enc != null) {
            runCatching { crypto.decryptStringFromBase64(enc, fid) }.getOrNull() ?: return null
        } else {
            data["content"] as? String ?: return null
        }
        if (content.isBlank()) return null
        val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: documentId
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
            isLegacyPlain = enc == null,
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
