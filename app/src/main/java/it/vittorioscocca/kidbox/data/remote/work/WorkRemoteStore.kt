package it.vittorioscocca.kidbox.data.remote.work

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBWorkProfileEntity
import it.vittorioscocca.kidbox.data.local.entity.KBWorkRecordEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.work.WorkRecordKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class WorkProfileRemoteDto(
    val ownerUid: String,
    val familyId: String,
    val employerEnc: String?,
    val visibilityScope: String,
    val visibilityMemberIds: List<String>,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val createdBy: String?,
    val createdByName: String?,
    val updatedBy: String?,
    val updatedByName: String?,
)

data class WorkRecordRemoteDto(
    val id: String,
    val familyId: String,
    val ownerUid: String,
    val kind: String,
    val referenceDateEpochMillis: Long?,
    val fieldsEnc: String?,
    val attachmentDocumentId: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val createdBy: String?,
    val createdByName: String?,
    val updatedBy: String?,
    val updatedByName: String?,
)

sealed interface WorkProfileRemoteChange {
    data class Upsert(val dto: WorkProfileRemoteDto) : WorkProfileRemoteChange
    data class Remove(val ownerUid: String) : WorkProfileRemoteChange
}

sealed interface WorkRecordRemoteChange {
    data class Upsert(val dto: WorkRecordRemoteDto) : WorkRecordRemoteChange
    data class Remove(val id: String) : WorkRecordRemoteChange
}

/**
 * Path Firestore: `families/{familyId}/workProfiles/{ownerUid}` e
 * `families/{familyId}/workRecords/{recordId}`. Mirror di `WorkRemoteStore` (iOS).
 *
 * Le rules hanno una regola dedicata: il proprietario scrive, un altro adulto
 * legge solo se è in `visibilityMemberIds`. Una LIST non filtra, quindi le
 * query sono SEMPRE vincolate: le schede condivise con me con
 * `array-contains` sul mio uid, i documenti con `ownerUid ==`.
 */
@Singleton
class WorkRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun profiles(familyId: String) =
        db.collection("families").document(familyId).collection("workProfiles")

    private fun records(familyId: String) =
        db.collection("families").document(familyId).collection("workRecords")

    // ── Scritture ───────────────────────────────────────────────────────

    suspend fun upsert(profile: KBWorkProfileEntity, memberIds: List<String>) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = profiles(profile.familyId).document(profile.ownerUid)
        val exists = ref.get().await().exists()
        val scope = KBVisibilityScope.normalizedWork(profile.visibilityScope)
        val payload = mutableMapOf<String, Any?>(
            "schemaVersion" to 1,
            "ownerUid" to profile.ownerUid,
            "employerEnc" to profile.employerEnc,
            "visibilityScope" to scope,
            // Autorevole per le rules: tornando privata la lista si svuota.
            "visibilityMemberIds" to if (scope == KBVisibilityScope.MEMBERS) memberIds else emptyList<String>(),
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedByName" to profile.updatedByName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!exists) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["createdBy"] = uid
            payload["createdByName"] = profile.createdByName
        }
        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun upsert(record: KBWorkRecordEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = records(record.familyId).document(record.id)
        val exists = ref.get().await().exists()
        val payload = mutableMapOf<String, Any?>(
            "schemaVersion" to 1,
            "ownerUid" to record.ownerUid,
            "kind" to record.kind,
            "referenceDate" to Timestamp(java.util.Date(record.referenceDateEpochMillis)),
            "fieldsEnc" to record.fieldsEnc,
            "attachmentDocumentId" to record.attachmentDocumentId,
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedByName" to record.updatedByName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!exists) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["createdBy"] = uid
            payload["createdByName"] = record.createdByName
        }
        ref.set(payload, SetOptions.merge()).await()
    }

    /** Il tombstone svuota i campi cifrati: un documento cancellato non porta con sé i valori. */
    suspend fun softDelete(recordId: String, familyId: String) {
        val uid = auth.currentUser?.uid ?: return
        records(familyId).document(recordId).set(
            mapOf(
                "isDeleted" to true,
                "fieldsEnc" to FieldValue.delete(),
                "attachmentDocumentId" to FieldValue.delete(),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    // ── Listener (tutti vincolati) ──────────────────────────────────────

    /** La propria scheda (documento singolo: id = uid). */
    fun listenOwnProfile(
        familyId: String,
        uid: String,
        onChange: (List<WorkProfileRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration = profiles(familyId).document(uid).addSnapshotListener(
        MetadataChanges.INCLUDE,
        EventListener<DocumentSnapshot> { snap, err ->
            if (err != null) onError(err)
            else if (snap != null && snap.exists()) onChange(listOf(WorkProfileRemoteChange.Upsert(profileDto(snap, familyId))))
        },
    )

    /** Le schede che altri adulti hanno condiviso con me. */
    fun listenSharedProfiles(
        familyId: String,
        uid: String,
        onChange: (List<WorkProfileRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration = profiles(familyId)
        .whereArrayContains("visibilityMemberIds", uid)
        .addSnapshotListener(
            MetadataChanges.INCLUDE,
            EventListener<QuerySnapshot> { snap, err ->
                if (err != null) {
                    onError(err)
                } else if (snap != null) {
                    // Upsert dal risultato completo, rimozioni dal delta (vedi porting-android).
                    val upserts = snap.documents.map { WorkProfileRemoteChange.Upsert(profileDto(it, familyId)) }
                    val removes = snap.documentChanges
                        .filter { it.type == DocumentChange.Type.REMOVED }
                        .map { WorkProfileRemoteChange.Remove(it.document.id) }
                    val changes = upserts + removes
                    if (changes.isNotEmpty()) onChange(changes)
                }
            },
        )

    /** I documenti di un proprietario di cui posso leggere la scheda. */
    fun listenRecords(
        familyId: String,
        ownerUid: String,
        onChange: (List<WorkRecordRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration = records(familyId)
        .whereEqualTo("ownerUid", ownerUid)
        .addSnapshotListener(
            MetadataChanges.INCLUDE,
            EventListener<QuerySnapshot> { snap, err ->
                if (err != null) {
                    onError(err)
                } else if (snap != null) {
                    val upserts = snap.documents.map { WorkRecordRemoteChange.Upsert(recordDto(it, familyId)) }
                    val removes = snap.documentChanges
                        .filter { it.type == DocumentChange.Type.REMOVED }
                        .map { WorkRecordRemoteChange.Remove(it.document.id) }
                    val changes = upserts + removes
                    if (changes.isNotEmpty()) onChange(changes)
                }
            },
        )

    // ── Mapping ─────────────────────────────────────────────────────────

    private fun profileDto(doc: DocumentSnapshot, familyId: String): WorkProfileRemoteDto {
        val d = doc.data ?: emptyMap()
        fun str(k: String): String? = (d[k] as? String)?.takeIf { it.isNotBlank() }
        return WorkProfileRemoteDto(
            ownerUid = doc.id,
            familyId = familyId,
            employerEnc = str("employerEnc"),
            visibilityScope = KBVisibilityScope.normalizedWork(d["visibilityScope"] as? String),
            visibilityMemberIds = (d["visibilityMemberIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            isDeleted = d["isDeleted"] as? Boolean ?: false,
            createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
            updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
            createdBy = str("createdBy"), createdByName = str("createdByName"),
            updatedBy = str("updatedBy"), updatedByName = str("updatedByName"),
        )
    }

    private fun recordDto(doc: DocumentSnapshot, familyId: String): WorkRecordRemoteDto {
        val d = doc.data ?: emptyMap()
        fun str(k: String): String? = (d[k] as? String)?.takeIf { it.isNotBlank() }
        return WorkRecordRemoteDto(
            id = doc.id,
            familyId = familyId,
            ownerUid = str("ownerUid") ?: "",
            kind = str("kind") ?: WorkRecordKind.PAYSLIP,
            referenceDateEpochMillis = (d["referenceDate"] as? Timestamp)?.toDate()?.time,
            fieldsEnc = str("fieldsEnc"),
            attachmentDocumentId = str("attachmentDocumentId"),
            isDeleted = d["isDeleted"] as? Boolean ?: false,
            createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
            updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
            createdBy = str("createdBy"), createdByName = str("createdByName"),
            updatedBy = str("updatedBy"), updatedByName = str("updatedByName"),
        )
    }
}
