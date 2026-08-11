package it.vittorioscocca.kidbox.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Conteggi usati dalla checklist "Per iniziare" per capire cosa l'utente ha già
 * fatto.
 *
 * Gemello di `OnboardingSignals.build` in `OnboardingChecklist.swift`. Sta in un
 * DAO suo, accanto a `NudgeSignalsDao`, per la stessa ragione: qui interessa
 * solo "esiste almeno una riga?", e tenere insieme le sette domande rende
 * evidente quale profilo la checklist si costruisce — e quanto poco. Nessuno di
 * questi numeri esce dal dispositivo.
 *
 * Non si riusa `NudgeSignalsDao` perché i due insiemi coincidono solo a metà:
 * la checklist chiede di foto, spese, note e lista della spesa, che al motore
 * di nudge non servono; il motore chiede di wallet, esami e AI, che non sono
 * passi della checklist.
 */
@Dao
interface OnboardingSignalsDao {

    @Query("SELECT COUNT(*) FROM kb_family_members WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun familyMemberCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_documents WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun documentCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_family_photos WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun familyPhotoCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_calendar_events WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun calendarEventCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_expenses WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun expenseCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_notes WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun noteCount(familyId: String): Int

    @Query("SELECT COUNT(*) FROM kb_grocery_items WHERE familyId = :familyId AND isDeleted = 0")
    suspend fun groceryItemCount(familyId: String): Int
}
