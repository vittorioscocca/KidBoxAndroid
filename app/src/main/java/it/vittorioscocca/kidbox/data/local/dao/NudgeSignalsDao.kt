package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Conteggi usati dal motore di nudge per capire cosa l'utente NON usa.
 *
 * Sta in un DAO suo invece che sparso nei DAO di ogni feature per una ragione
 * precisa: qui interessa solo "esiste almeno una riga?", e tenere insieme le
 * sette domande rende evidente quale profilo il motore si costruisce — e quanto
 * poco. Nessuno di questi numeri esce dal dispositivo.
 */
@Dao
interface NudgeSignalsDao {

    @Query("SELECT COUNT(*) FROM kb_family_members WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun familyMemberCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_documents WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun documentCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_wallet_tickets WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun walletTicketCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_medical_exams WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun medicalExamCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_chat_messages WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun chatMessageCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_calendar_events WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun calendarEventCount(familyId: String): Int

    /** `kb_children` non ha `isDeleted`: le righe o ci sono o sono state rimosse. */
    @Query("SELECT COUNT(*) FROM kb_children WHERE familyId = :familyId")
    suspend fun childCount(familyId: String): Int

    /** Le conversazioni AI non hanno `isDeleted`: o ci sono o non ci sono. */
    @Query("SELECT COUNT(*) FROM kb_ai_conversations WHERE familyId = :familyId")
    suspend fun aiConversationCount(familyId: String): Int
}
