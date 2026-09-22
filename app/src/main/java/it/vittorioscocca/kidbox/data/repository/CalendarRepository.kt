package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.OnboardingSignalsDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.notification.CalendarEventReminderScheduler
import it.vittorioscocca.kidbox.data.remote.calendar.CalendarEventRemoteChange
import it.vittorioscocca.kidbox.data.remote.calendar.CalendarRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarDao: KBCalendarEventDao,
    private val familyDao: KBFamilyDao,
    private val childDao: KBChildDao,
    private val remoteStore: CalendarRemoteStore,
    private val reminderScheduler: CalendarEventReminderScheduler,
    private val auth: FirebaseAuth,
    private val onboardingSignalsDao: OnboardingSignalsDao,
    @ApplicationContext private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeEvents(familyId: String): Flow<List<KBCalendarEventEntity>> =
        calendarDao.observeByFamilyId(familyId)

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && listener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId
                listener = remoteStore.listenEvents(
                    familyId = familyId,
                    onChange = { changes -> scope.launch { applyInbound(familyId, changes) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )
            }
        }
    }

    /**
     * Pull-to-refresh: stacca e riaggancia il listener realtime.
     *
     * [startRealtime] da solo non basta — se il listener è già attivo sulla
     * stessa famiglia prende la scorciatoia del guard e non fa nulla. Qui il
     * guard viene azzerato prima, così l'aggancio riparte davvero e Firestore
     * rimanda lo snapshot completo. Stesso idioma di
     * [PasswordsRepository.awaitForceRestartRealtime].
     */
    suspend fun awaitForceRestartRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        realtimeMutex.withLock { stopRealtimeLocked() }
        startRealtime(familyId, onPermissionDenied)
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock { stopRealtimeLocked() }
        }
    }

    suspend fun upsertEventLocal(entity: KBCalendarEventEntity) {
        ensureFamilyExists(entity.familyId)
        val safeChildId = sanitizeChildId(entity.childId)
        val isNewEvent = calendarDao.getById(entity.id) == null
        val isFirstEvent = isNewEvent && onboardingSignalsDao.calendarEventCount(entity.familyId) == 0
        calendarDao.upsert(
            entity.copy(
                childId = safeChildId,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                isDeleted = false,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: entity.updatedBy,
                lastSyncError = null,
            ),
        )
        if (isNewEvent) {
            AppAnalytics.contentCreated(appContext, "calendar")
            if (isFirstEvent) {
                AppAnalytics.featureFirstUse(appContext, feature = "calendar")
            }
        }
    }

    suspend fun deleteEventLocal(entity: KBCalendarEventEntity) {
        calendarDao.upsert(
            entity.copy(
                isDeleted = true,
                syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: entity.updatedBy,
                lastSyncError = null,
            ),
        )
    }

    suspend fun flushPending(familyId: String) {
        // Upsert queue
        calendarDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue)
            .forEach { local ->
                runCatching { remoteStore.upsertEvent(local) }
                    .onSuccess {
                        calendarDao.upsert(
                            local.copy(
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                    }
                    .onFailure { err ->
                        calendarDao.upsert(
                            local.copy(
                                lastSyncError = err.localizedMessage,
                            ),
                        )
                    }
            }

        // Delete queue
        calendarDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue)
            .forEach { local ->
                runCatching { remoteStore.softDeleteEvent(familyId, local.id) }
                    .onSuccess { calendarDao.deleteById(local.id) }
                    .onFailure { err ->
                        calendarDao.upsert(
                            local.copy(
                                lastSyncError = err.localizedMessage,
                            ),
                        )
                    }
            }
    }

    private suspend fun applyInbound(
        familyId: String,
        changes: List<CalendarEventRemoteChange>,
    ) {
        changes.forEach { change ->
            when (change) {
                is CalendarEventRemoteChange.Remove -> {
                    // L'evento sparisce: il suo avviso non deve sopravvivergli.
                    // Senza questo una sveglia continuerebbe a suonare per un
                    // evento cancellato da un altro device o dal web.
                    reminderScheduler.cancel(change.id)
                    calendarDao.deleteById(change.id)
                }

                is CalendarEventRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) {
                        reminderScheduler.cancel(dto.id)
                        calendarDao.deleteById(dto.id)
                        return@forEach
                    }

                    val local = calendarDao.getById(dto.id)

                    // anti-resurrect: local pending delete wins
                    if (
                        local != null &&
                        local.isDeleted &&
                        KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.PENDING_DELETE
                    ) {
                        return@forEach
                    }

                    val localSync = local?.syncStateRaw?.let(KBSyncState::fromRaw) ?: KBSyncState.SYNCED
                    val localHasPendingOutbound =
                        local != null &&
                            (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.ERROR)
                    val remoteUpdatedAt = dto.updatedAtEpochMillis ?: 0L
                    if (
                        localHasPendingOutbound &&
                        remoteUpdatedAt < local.updatedAtEpochMillis
                    ) {
                        return@forEach
                    }

                    val now = System.currentTimeMillis()
                    ensureFamilyExists(familyId)
                    val safeChildId = sanitizeChildId(dto.childId)
                    val remoteScope = KBVisibilityScope.normalized(dto.visibilityScope)
                    val remoteMemberIds = dto.visibilityMemberIds
                    calendarDao.upsert(
                        KBCalendarEventEntity(
                            id = dto.id,
                            familyId = familyId,
                            childId = safeChildId,
                            title = dto.title,
                            notes = dto.notes,
                            location = dto.location,
                            startDateEpochMillis = dto.startDateEpochMillis,
                            endDateEpochMillis = dto.endDateEpochMillis,
                            isAllDay = dto.isAllDay,
                            categoryRaw = dto.categoryRaw,
                            recurrenceRaw = dto.recurrenceRaw,
                            reminderMinutes = dto.reminderMinutes,
                            priorityRaw = dto.priority ?: 0,
                            linkedHealthItemId = dto.linkedHealthItemId,
                            linkedHealthItemType = dto.linkedHealthItemType,
                            visibilityScope = remoteScope,
                            visibilityMemberIdsJson = encodeStringList(remoteMemberIds),
                            isDeleted = false,
                            createdAtEpochMillis = local?.createdAtEpochMillis ?: dto.createdAtEpochMillis ?: now,
                            updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                            updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
                            createdBy = local?.createdBy ?: dto.createdBy ?: "",
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ),
                    )

                    // I promemoria restano del dispositivo che li accende: qui
                    // non se ne arma mai uno nuovo. Ma se l'avviso su QUESTO
                    // telefono esiste già e l'evento è stato spostato altrove —
                    // o il promemoria è stato tolto, o è diventato urgente —
                    // va riallineato, altrimenti suona all'ora vecchia.
                    //
                    // Solo se qualcosa che l'avviso porta con sé è davvero
                    // cambiato: ogni salvataggio locale torna indietro come eco
                    // dal listener, e senza questo confronto si rifarebbe
                    // cancel+arm a vuoto a ogni giro.
                    val reminderChanged = local == null ||
                        local.startDateEpochMillis != dto.startDateEpochMillis ||
                        local.reminderMinutes != dto.reminderMinutes ||
                        local.priorityRaw != (dto.priority ?: 0) ||
                        local.title != dto.title
                    if (reminderChanged && reminderScheduler.hasArmed(dto.id)) {
                        reminderScheduler.sync(
                            eventId = dto.id,
                            familyId = familyId,
                            title = dto.title,
                            startEpochMillis = dto.startDateEpochMillis,
                            reminderMinutes = dto.reminderMinutes,
                            isUrgent = (dto.priority ?: 0) == 1,
                        )
                    }
                }
            }
        }
    }

    private fun stopRealtimeLocked() {
        listener?.remove()
        listener = null
        listeningFamilyId = null
    }

    private suspend fun sanitizeChildId(childId: String?): String? {
        val id = childId?.trim().orEmpty()
        if (id.isBlank()) return null
        return if (childDao.getById(id) != null) id else null
    }

    private suspend fun ensureFamilyExists(familyId: String) {
        if (familyId.isBlank()) return
        if (familyDao.getById(familyId) != null) return
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        familyDao.upsert(
            KBFamilyEntity(
                id = familyId,
                name = "Famiglia",
                heroPhotoURL = null,
                heroPhotoLocalPath = null,
                heroPhotoUpdatedAtEpochMillis = null,
                heroPhotoScale = null,
                heroPhotoOffsetX = null,
                heroPhotoOffsetY = null,
                createdBy = uid,
                updatedBy = uid,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                lastSyncAtEpochMillis = null,
                lastSyncError = null,
            ),
        )
    }
}

