package it.vittorioscocca.kidbox.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bozza / storico locale ticket supporto AI (sync verso Firestore via [SupportTicketRemoteStore]).
 */
@Entity(
    tableName = "kb_support_tickets",
    indices = [Index("familyId"), Index("uid"), Index("status")],
)
data class SupportTicketEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val uid: String,
    val userEmail: String,
    /** "question" | "bug" | "suggestion" */
    val type: String,
    val title: String,
    /** JSON serializzato della chat (messaggi role/content). */
    val conversation: String,
    /** JSON array di URI locali immagini allegate (max 5). */
    val imageUris: String,
    /** "draft" | "sent" | "open" | "closed" */
    val status: String,
    val createdAt: Long,
    val sentAt: Long? = null,
)
