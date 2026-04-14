package it.vittorioscocca.kidbox.data.remote.calendar

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
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class CalendarEventRemoteDto(
    val id: String,
    val familyId: String,
    val childId: String?,
    val title: String,
    val notes: String?,
    val location: String?,
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long,
    val isAllDay: Boolean,
    val categoryRaw: String,
    val recurrenceRaw: String,
    val reminderMinutes: Int?,
    val linkedHealthItemId: String?,
    val linkedHealthItemType: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val createdBy: String?,
)

sealed interface CalendarEventRemoteChange {
    data class Upsert(val dto: CalendarEventRemoteDto) : CalendarEventRemoteChange
    data class Remove(val id: String) : CalendarEventRemoteChange
}

@Singleton
class CalendarRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun listenEvents(
        familyId: String,
        onChange: (List<CalendarEventRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return db.collection("families").document(familyId).collection("calendarEvents")
            .addSnapshotListener(
                MetadataChanges.EXCLUDE,
                EventListener<QuerySnapshot> { snap, err ->
                    if (err != null) {
                        onError(err)
                    } else if (snap != null) {
                        val changes = snap.documentChanges.mapNotNull { diff ->
                            val doc = diff.document
                            val d = doc.data
                            val title = (d["title"] as? String)?.trim().orEmpty()
                            if (title.isBlank()) {
                                null
                            } else {
                                val dto = CalendarEventRemoteDto(
                                    id = doc.id,
                                    familyId = familyId,
                                    childId = (d["childId"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                    title = title,
                                    notes = (d["notes"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                    location = (d["location"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                    startDateEpochMillis = (d["startDate"] as? Timestamp)?.toDate()?.time ?: return@mapNotNull null,
                                    endDateEpochMillis = (d["endDate"] as? Timestamp)?.toDate()?.time ?: return@mapNotNull null,
                                    isAllDay = d["isAllDay"] as? Boolean ?: false,
                                    categoryRaw = (d["categoryRaw"] as? String)?.trim().orEmpty().ifBlank { "family" },
                                    recurrenceRaw = (d["recurrenceRaw"] as? String)?.trim().orEmpty().ifBlank { "none" },
                                    reminderMinutes = (d["reminderMinutes"] as? Number)?.toInt(),
                                    linkedHealthItemId = (d["linkedHealthItemId"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                    linkedHealthItemType = (d["linkedHealthItemType"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                    isDeleted = d["isDeleted"] as? Boolean ?: false,
                                    createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                                    updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                                    updatedBy = d["updatedBy"] as? String,
                                    createdBy = d["createdBy"] as? String,
                                )
                                when (diff.type) {
                                    DocumentChange.Type.ADDED,
                                    DocumentChange.Type.MODIFIED,
                                    -> CalendarEventRemoteChange.Upsert(dto)

                                    DocumentChange.Type.REMOVED -> CalendarEventRemoteChange.Remove(doc.id)
                                }
                            }
                        }
                        if (changes.isNotEmpty()) onChange(changes)
                    }
                },
            )
    }

    suspend fun upsertEvent(event: KBCalendarEventEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val payload = mutableMapOf<String, Any?>(
            "id" to event.id,
            "familyId" to event.familyId,
            "childId" to event.childId,
            "title" to event.title,
            "notes" to event.notes,
            "location" to event.location,
            "isAllDay" to event.isAllDay,
            "categoryRaw" to event.categoryRaw,
            "recurrenceRaw" to event.recurrenceRaw,
            "reminderMinutes" to event.reminderMinutes,
            "linkedHealthItemId" to event.linkedHealthItemId,
            "linkedHealthItemType" to event.linkedHealthItemType,
            "isDeleted" to event.isDeleted,
            "createdBy" to event.createdBy,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        payload["startDate"] = timestampFromMillis(event.startDateEpochMillis)
        payload["endDate"] = timestampFromMillis(event.endDateEpochMillis)
        payload["createdAt"] = timestampFromMillis(event.createdAtEpochMillis)

        db.collection("families")
            .document(event.familyId)
            .collection("calendarEvents")
            .document(event.id)
            .set(payload, SetOptions.merge())
            .await()
    }

    suspend fun softDeleteEvent(
        familyId: String,
        eventId: String,
    ) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families")
            .document(familyId)
            .collection("calendarEvents")
            .document(eventId)
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

