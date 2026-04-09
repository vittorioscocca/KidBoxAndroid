package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Allineato a [KBUserProfile] iOS (SwiftData) — profilo genitore / account. */
@Entity(tableName = "kb_user_profiles")
data class KBUserProfileEntity(
    @PrimaryKey val uid: String,
    val email: String?,
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
    val familyAddress: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** Come `SyncCenter` iOS: nome mostrato per `members` quando sei tu. */
fun KBUserProfileEntity.canonicalMemberDisplayName(): String? {
    val dn = displayName?.trim().orEmpty()
    val fn = firstName?.trim().orEmpty()
    val ln = lastName?.trim().orEmpty()
    val composed = "$fn $ln".trim()
    return when {
        dn.isNotEmpty() && dn != "Utente" -> dn
        composed.isNotEmpty() -> composed
        else -> null
    }
}
