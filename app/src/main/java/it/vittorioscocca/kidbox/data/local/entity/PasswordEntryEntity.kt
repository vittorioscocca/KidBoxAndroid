package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "password_entries",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("familyId"),
        Index(value = ["familyId", "updatedAtEpochMillis"]),
    ],
)
data class PasswordEntryEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val createdBy: String,
    val visibility: String,
    /** JSON array (uid list) for `visibility == members`. */
    val visibilityMemberIdsJson: String = "[]",
    val groupId: String?,
    val titleCipher: ByteArray,
    val usernameCipher: ByteArray?,
    val passwordCipher: ByteArray,
    val websiteCipher: ByteArray?,
    val notesCipher: ByteArray?,
    val otpConfigCipher: ByteArray?,
    val iconURL: String?,
    val lastUsedAtEpochMillis: Long?,
    val passwordUpdatedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
    /** Preferita in app (sincronizzata su Firestore, non cifrata). */
    val isFavorite: Boolean = false,
    /** Numero occorrenze HIBP (null = mai verificata). */
    val pwnedCount: Int? = null,
    /** Epoch millis dell'ultimo controllo HIBP (null = mai verificata). */
    val pwnedCheckedAt: Long? = null,
    val syncStateRaw: Int = 0,
    val lastSyncError: String?,
)
