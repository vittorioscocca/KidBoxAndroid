package it.vittorioscocca.kidbox.data.local.mapper

import it.vittorioscocca.kidbox.data.local.entity.KBPediatricProfileEntity
import it.vittorioscocca.kidbox.domain.model.KBEmergencyContact
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun KBPediatricProfileEntity.toDomain(): KBPediatricProfile = KBPediatricProfile(
    id = id,
    familyId = familyId,
    childId = childId,
    emergencyContactsJson = emergencyContactsJson,
    bloodGroup = bloodGroup,
    allergies = allergies,
    medicalNotes = medicalNotes,
    doctorName = doctorName,
    doctorPhone = doctorPhone,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

fun KBPediatricProfile.toEntity(): KBPediatricProfileEntity = KBPediatricProfileEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    emergencyContactsJson = emergencyContactsJson,
    bloodGroup = bloodGroup,
    allergies = allergies,
    medicalNotes = medicalNotes,
    doctorName = doctorName,
    doctorPhone = doctorPhone,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

/** Decode emergency contacts list from the JSON column. Returns an empty list on null / parse error. */
fun KBPediatricProfile.decodeEmergencyContacts(): List<KBEmergencyContact> {
    val raw = emergencyContactsJson ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { idx ->
            val obj = arr.getJSONObject(idx)
            KBEmergencyContact(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = obj.optString("name"),
                relation = obj.optString("relation"),
                phone = obj.optString("phone"),
            )
        }
    }.getOrElse { emptyList() }
}

/** Encode emergency contacts list to the JSON column. Returns null for an empty list. */
fun List<KBEmergencyContact>.encodeForStorage(): String? {
    if (isEmpty()) return null
    val arr = JSONArray()
    forEach { c ->
        val obj = JSONObject().apply {
            put("id", c.id)
            put("name", c.name)
            put("relation", c.relation)
            put("phone", c.phone)
        }
        arr.put(obj)
    }
    return arr.toString()
}
