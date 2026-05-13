package it.vittorioscocca.kidbox.data.remote.passwords

import android.util.Base64
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
import com.google.firebase.firestore.Source
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

data class PasswordEntryRemoteDto(
    val id: String,
    val familyId: String,
    val createdBy: String,
    val visibility: String,
    val visibilityMemberIds: List<String>,
    val groupId: String?,
    val titleCipherB64: String?,
    val usernameCipherB64: String?,
    val passwordCipherB64: String?,
    val websiteCipherB64: String?,
    val notesCipherB64: String?,
    val otpConfigCipherB64: String?,
    val iconURL: String?,
    val lastUsedAtEpochMillis: Long?,
    val passwordUpdatedAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val deletedAtEpochMillis: Long?,
    val isFavorite: Boolean = false,
    val pwnedCount: Int? = null,
    val pwnedCheckedAt: Long? = null,
)

data class PasswordGroupRemoteDto(
    val id: String,
    val familyId: String,
    val nameCipherB64: String?,
    val icon: String?,
    val color: String?,
    val visibility: String,
    val createdBy: String,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val deletedAtEpochMillis: Long?,
)

sealed interface PasswordRemoteChange {
    data class UpsertEntry(val dto: PasswordEntryRemoteDto) : PasswordRemoteChange
    data class RemoveEntry(val id: String) : PasswordRemoteChange
    data class UpsertGroup(val dto: PasswordGroupRemoteDto) : PasswordRemoteChange
    data class RemoveGroup(val id: String) : PasswordRemoteChange
}

@Singleton
class PasswordRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun b64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

    private fun tsMillis(v: Any?): Long? = when (v) {
        is Timestamp -> v.toDate().time
        is Long -> v
        is Int -> v.toLong()
        else -> null
    }

    private fun PasswordEntryEntity.visibilityMemberIdsJsonToList(): List<String> {
        return try {
            val arr = JSONArray(visibilityMemberIdsJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseEntry(docId: String, familyId: String, d: Map<String, Any>): PasswordEntryRemoteDto {
        return PasswordEntryRemoteDto(
            id = docId,
            familyId = familyId,
            createdBy = d["createdBy"] as? String ?: "",
            visibility = d["visibility"] as? String ?: KBVisibilityScope.FAMILY,
            visibilityMemberIds = (d["visibilityMemberIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            groupId = d["groupId"] as? String,
            titleCipherB64 = d["titleCipherB64"] as? String,
            usernameCipherB64 = d["usernameCipherB64"] as? String,
            passwordCipherB64 = d["passwordCipherB64"] as? String,
            websiteCipherB64 = d["websiteCipherB64"] as? String,
            notesCipherB64 = d["notesCipherB64"] as? String,
            otpConfigCipherB64 = d["otpConfigCipherB64"] as? String,
            iconURL = d["iconURL"] as? String,
            lastUsedAtEpochMillis = tsMillis(d["lastUsedAt"]),
            passwordUpdatedAtEpochMillis = tsMillis(d["passwordUpdatedAt"]),
            expiresAtEpochMillis = tsMillis(d["expiresAt"]),
            createdAtEpochMillis = tsMillis(d["createdAt"]),
            updatedAtEpochMillis = tsMillis(d["updatedAt"]),
            deletedAtEpochMillis = tsMillis(d["deletedAt"]),
            isFavorite = when (val v = d["isFavorite"]) {
                is Boolean -> v
                is Long -> v == 1L
                is Int -> v == 1
                else -> false
            },
            pwnedCount = when (val v = d["pwnedCount"]) {
                is Long -> v.toInt()
                is Int -> v
                else -> null
            },
            pwnedCheckedAt = tsMillis(d["pwnedCheckedAt"]),
        )
    }

    private fun parseGroup(docId: String, familyId: String, d: Map<String, Any>): PasswordGroupRemoteDto {
        return PasswordGroupRemoteDto(
            id = docId,
            familyId = familyId,
            nameCipherB64 = d["nameCipherB64"] as? String,
            icon = d["icon"] as? String,
            color = d["color"] as? String,
            visibility = d["visibility"] as? String ?: KBVisibilityScope.FAMILY,
            createdBy = d["createdBy"] as? String ?: "",
            createdAtEpochMillis = tsMillis(d["createdAt"]),
            updatedAtEpochMillis = tsMillis(d["updatedAt"]),
            deletedAtEpochMillis = tsMillis(d["deletedAt"]),
        )
    }

    /**
     * Lettura one-shot da Firestore (cache+rete) per popolare Room subito dopo join / cold start,
     * evitando di dipendere solo dal primo snapshot del listener (a volte [documentChanges] vuoto).
     */
    suspend fun fetchAllPasswordRemoteChanges(familyId: String): List<PasswordRemoteChange> {
        val eSnap = db.collection("families").document(familyId).collection("passwords")
            .get(Source.DEFAULT)
            .await()
        val gSnap = db.collection("families").document(familyId).collection("passwordGroups")
            .get(Source.DEFAULT)
            .await()
        val entries = eSnap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            PasswordRemoteChange.UpsertEntry(parseEntry(doc.id, familyId, data))
        }
        val groups = gSnap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            PasswordRemoteChange.UpsertGroup(parseGroup(doc.id, familyId, data))
        }
        return entries + groups
    }

    fun listenPasswordEntries(
        familyId: String,
        onChange: (List<PasswordRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        val allowFullDocFallback = AtomicBoolean(true)
        return db.collection("families").document(familyId).collection("passwords")
            .addSnapshotListener(
                MetadataChanges.INCLUDE,
                EventListener<QuerySnapshot> { snap, err ->
                    if (err != null) {
                        onError(err)
                        return@EventListener
                    }
                    if (snap == null) return@EventListener

                    val changes: List<PasswordRemoteChange> = when {
                        snap.documentChanges.isNotEmpty() -> {
                            allowFullDocFallback.set(false)
                            snap.documentChanges.map { diff ->
                                val doc = diff.document
                                val data = doc.data ?: emptyMap<String, Any>()
                                when (diff.type) {
                                    DocumentChange.Type.ADDED,
                                    DocumentChange.Type.MODIFIED,
                                    -> PasswordRemoteChange.UpsertEntry(parseEntry(doc.id, familyId, data))
                                    DocumentChange.Type.REMOVED -> PasswordRemoteChange.RemoveEntry(doc.id)
                                }
                            }
                        }
                        allowFullDocFallback.get() && !snap.isEmpty -> {
                            allowFullDocFallback.set(false)
                            snap.documents.mapNotNull { doc ->
                                val data = doc.data ?: return@mapNotNull null
                                PasswordRemoteChange.UpsertEntry(parseEntry(doc.id, familyId, data))
                            }
                        }
                        else -> emptyList()
                    }
                    if (changes.isNotEmpty()) onChange(changes)
                },
            )
    }

    fun listenPasswordGroups(
        familyId: String,
        onChange: (List<PasswordRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        val allowFullDocFallback = AtomicBoolean(true)
        return db.collection("families").document(familyId).collection("passwordGroups")
            .addSnapshotListener(
                MetadataChanges.INCLUDE,
                EventListener<QuerySnapshot> { snap, err ->
                    if (err != null) {
                        onError(err)
                        return@EventListener
                    }
                    if (snap == null) return@EventListener

                    val changes: List<PasswordRemoteChange> = when {
                        snap.documentChanges.isNotEmpty() -> {
                            allowFullDocFallback.set(false)
                            snap.documentChanges.map { diff ->
                                val doc = diff.document
                                val data = doc.data ?: emptyMap<String, Any>()
                                when (diff.type) {
                                    DocumentChange.Type.ADDED,
                                    DocumentChange.Type.MODIFIED,
                                    -> PasswordRemoteChange.UpsertGroup(parseGroup(doc.id, familyId, data))
                                    DocumentChange.Type.REMOVED -> PasswordRemoteChange.RemoveGroup(doc.id)
                                }
                            }
                        }
                        allowFullDocFallback.get() && !snap.isEmpty -> {
                            allowFullDocFallback.set(false)
                            snap.documents.mapNotNull { doc ->
                                val data = doc.data ?: return@mapNotNull null
                                PasswordRemoteChange.UpsertGroup(parseGroup(doc.id, familyId, data))
                            }
                        }
                        else -> emptyList()
                    }
                    if (changes.isNotEmpty()) onChange(changes)
                },
            )
    }

    suspend fun upsertEntry(entity: PasswordEntryEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("families").document(entity.familyId).collection("passwords").document(entity.id)
        val exists = ref.get().await().exists()
        val vis = KBVisibilityScope.normalizedPassword(entity.visibility)

        val payload = mutableMapOf<String, Any?>(
            "schemaVersion" to 1,
            "familyId" to entity.familyId,
            "createdBy" to entity.createdBy,
            "visibility" to vis,
            "visibilityMemberIds" to entity.visibilityMemberIdsJsonToList(),
            "titleCipherB64" to b64(entity.titleCipher),
            "passwordCipherB64" to b64(entity.passwordCipher),
            "passwordUpdatedAt" to Timestamp(entity.passwordUpdatedAtEpochMillis / 1000, ((entity.passwordUpdatedAtEpochMillis % 1000) * 1_000_000).toInt()),
            "createdAt" to Timestamp(entity.createdAtEpochMillis / 1000, ((entity.createdAtEpochMillis % 1000) * 1_000_000).toInt()),
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to uid,
            "isFavorite" to entity.isFavorite,
            "pwnedCount" to entity.pwnedCount,
        )
        entity.pwnedCheckedAt?.let { ms ->
            payload["pwnedCheckedAt"] = Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
        }
        entity.groupId?.takeIf { it.isNotEmpty() }?.let { payload["groupId"] = it }
        entity.usernameCipher?.let { payload["usernameCipherB64"] = b64(it) }
        entity.websiteCipher?.let { payload["websiteCipherB64"] = b64(it) }
        entity.notesCipher?.let { payload["notesCipherB64"] = b64(it) }
        entity.otpConfigCipher?.let { payload["otpConfigCipherB64"] = b64(it) }
        entity.iconURL?.takeIf { it.isNotEmpty() }?.let { payload["iconURL"] = it }
        entity.lastUsedAtEpochMillis?.let { ms ->
            payload["lastUsedAt"] = Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
        }
        entity.expiresAtEpochMillis?.let { ms ->
            payload["expiresAt"] = Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
        }
        entity.deletedAtEpochMillis?.let { ms ->
            payload["deletedAt"] = Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
        }

        if (!exists) {
            payload["createdBy"] = entity.createdBy.ifBlank { uid }
        }

        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun upsertGroup(entity: PasswordGroupEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("families").document(entity.familyId).collection("passwordGroups").document(entity.id)
        val exists = ref.get().await().exists()
        val vis = KBVisibilityScope.normalizedPassword(entity.visibility)

        val payload = mutableMapOf<String, Any?>(
            "schemaVersion" to 1,
            "familyId" to entity.familyId,
            "createdBy" to entity.createdBy,
            "visibility" to vis,
            "nameCipherB64" to b64(entity.nameCipher),
            "icon" to entity.icon,
            "color" to entity.color,
            "createdAt" to Timestamp(entity.createdAtEpochMillis / 1000, ((entity.createdAtEpochMillis % 1000) * 1_000_000).toInt()),
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to uid,
        )
        entity.deletedAtEpochMillis?.let { ms ->
            payload["deletedAt"] = Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
        }

        if (!exists) {
            payload["createdBy"] = entity.createdBy.ifBlank { uid }
        }

        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun softDeleteEntry(familyId: String, entryId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("families").document(familyId).collection("passwords").document(entryId)
            .set(
                mapOf(
                    "deletedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to uid,
                ),
                SetOptions.merge(),
            ).await()
    }

    suspend fun softDeleteGroup(familyId: String, groupId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("families").document(familyId).collection("passwordGroups").document(groupId)
            .set(
                mapOf(
                    "deletedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to uid,
                ),
                SetOptions.merge(),
            ).await()
    }
}
