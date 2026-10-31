package it.vittorioscocca.kidbox.ui.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.remote.family.FamilyFirestoreCreationRepository
import it.vittorioscocca.kidbox.data.remote.family.isPermissionDenied
import it.vittorioscocca.kidbox.domain.auth.LogoutUseCase
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.util.UUID
import it.vittorioscocca.kidbox.util.analytics.OnboardingAnalyticsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val familyCreation: FamilyFirestoreCreationRepository,
    private val logoutUseCase: LogoutUseCase,
    private val calendarEventDao: KBCalendarEventDao,
    private val groceryItemDao: KBGroceryItemDao,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    /**
     * «Esci» dal wizard: chi si è registrato con l'account sbagliato, o vuole
     * solo tornare al login, prima non aveva nessuna uscita se non
     * disinstallare. Stesso logout del Profilo (sign-out + wipe locale).
     */
    fun signOut(onDone: () -> Unit) {
        if (_isSigningOut.value) return
        _isSigningOut.value = true
        OnboardingAnalyticsState.lastStepSeen = null
        OnboardingAnalyticsState.abandonReportedStep = null
        viewModelScope.launch {
            try {
                logoutUseCase.logout()
            } finally {
                _isSigningOut.value = false
            }
            onDone()
        }
    }

    private val _createdFamilyId = MutableStateFlow<String?>(null)
    val createdFamilyId: StateFlow<String?> = _createdFamilyId.asStateFlow()

    private val _isCreatingFamily = MutableStateFlow(false)
    val isCreatingFamily: StateFlow<Boolean> = _isCreatingFamily.asStateFlow()

    private val _createFamilyError = MutableStateFlow<String?>(null)
    val createFamilyError: StateFlow<String?> = _createFamilyError.asStateFlow()

    fun clearCreateError() {
        _createFamilyError.value = null
    }

    fun createFamily(
        familyName: String,
        childName: String,
        birthDateMillis: Long?,
    ) {
        val name = familyName.trim()
        val child = childName.trim()
        // Solo il nome della famiglia è obbligatorio: senza, non c'è niente da
        // creare. Il figlio è facoltativo — il repository scarta da sé i nomi
        // vuoti, quindi la famiglia nasce semplicemente senza figli e se ne
        // aggiungono dopo. Bloccare qui il wizard costringeva a inventare un
        // nome per superare la schermata.
        if (name.isEmpty()) return
        viewModelScope.launch {
            _isCreatingFamily.value = true
            _createFamilyError.value = null
            try {
                val id = familyCreation.createFamilyWithInitialChild(
                    familyName = name,
                    childName = child,
                    birthDateMillis = birthDateMillis,
                )
                _createdFamilyId.value = id
            } catch (e: Exception) {
                // Nessun retry in questo percorso: un permission-denied qui è
                // firestore.rules che rifiuta la creazione per limite famiglie raggiunto
                // (vedi ownedFamilyCount() in firestore.rules) — non un problema transitorio.
                _createFamilyError.value = if (isPermissionDenied(e)) {
                    appContext.getString(R.string.settings_family_limit_reached)
                } else {
                    e.localizedMessage ?: "Errore durante la creazione"
                }
            } finally {
                _isCreatingFamily.value = false
            }
        }
    }

    // ── Primo contenuto (ultima pagina del wizard «crea») ──────────────────
    //
    // Stessa scrittura dell'assistente (PlanningActionExecutor): riga in Room
    // da sincronizzare. Il `content_created` su Firestore lo scrive il trigger
    // del server alla creazione del documento, ed è la misura della
    // scommessa 13 del registro.

    fun saveFirstEvent(title: String, startMillis: Long, onSaved: () -> Unit) {
        val familyId = _createdFamilyId.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            calendarEventDao.upsert(
                KBCalendarEventEntity(
                    id = UUID.randomUUID().toString(),
                    familyId = familyId,
                    childId = null,
                    title = clean,
                    notes = null,
                    location = null,
                    startDateEpochMillis = startMillis,
                    endDateEpochMillis = startMillis + 3_600_000L,
                    isAllDay = false,
                    categoryRaw = "family",
                    recurrenceRaw = "none",
                    reminderMinutes = null,
                    linkedHealthItemId = null,
                    linkedHealthItemType = null,
                    visibilityScope = KBVisibilityScope.FAMILY,
                    visibilityMemberIdsJson = "[]",
                    isDeleted = false,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    updatedBy = uid,
                    createdBy = uid,
                    syncStateRaw = SYNC_PENDING_UPSERT,
                    lastSyncError = null,
                ),
            )
            onSaved()
        }
    }

    fun saveFirstGroceries(names: List<String>, onSaved: () -> Unit) {
        val familyId = _createdFamilyId.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val unique = names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        if (unique.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            for (name in unique) {
                groceryItemDao.upsert(
                    KBGroceryItemEntity(
                        id = UUID.randomUUID().toString(),
                        familyId = familyId,
                        name = name,
                        category = null,
                        notes = null,
                        quantity = null,
                        isPurchased = false,
                        purchasedAtEpochMillis = null,
                        purchasedBy = null,
                        isDeleted = false,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        updatedBy = uid,
                        createdBy = uid,
                        syncStateRaw = SYNC_PENDING_UPSERT,
                        lastSyncError = null,
                    ),
                )
            }
            onSaved()
        }
    }

    private companion object {
        const val SYNC_PENDING_UPSERT = 1
    }
}
