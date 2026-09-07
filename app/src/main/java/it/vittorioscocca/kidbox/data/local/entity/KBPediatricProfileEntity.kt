package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `childId` non ha foreign key, e non deve averla: il campo punta a un
 * **soggetto salute**, che può essere un figlio (`kb_children`) oppure un
 * membro adulto della famiglia (`kb_family_members`). Nessuna singola tabella
 * li contiene entrambi, quindi un vincolo verso `kb_children` era
 * insoddisfacibile per ogni profilo di un adulto e faceva fallire l'insert.
 * L'indice resta, perché le letture avvengono per `childId`.
 */
@Entity(
    tableName = "kb_pediatric_profiles",
    foreignKeys = [
        ForeignKey(
            entity = KBFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("familyId"), Index("childId")],
)
data class KBPediatricProfileEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val childId: String,
    val emergencyContactsJson: String?,
    val bloodGroup: String?,
    val allergies: String?,
    val medicalNotes: String?,
    val doctorName: String?,
    val doctorPhone: String?,
    val doctorEmail: String?,
    val doctorAddress: String?,
    val doctorWebsite: String?,
    val doctorOfficeHoursJson: String?,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val syncStateRaw: Int,
    val lastSyncError: String?,
)
