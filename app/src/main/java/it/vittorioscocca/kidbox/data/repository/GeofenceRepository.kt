package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBGeofenceDao
import it.vittorioscocca.kidbox.data.local.entity.KBGeofenceEntity
import it.vittorioscocca.kidbox.data.remote.location.GeofenceRemoteChange
import it.vittorioscocca.kidbox.data.remote.location.GeofenceRemoteStore
import it.vittorioscocca.kidbox.data.remote.location.geofenceEntityFromDto
import it.vittorioscocca.kidbox.util.KBLog
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
class GeofenceRepository @Inject constructor(
    private val geofenceDao: KBGeofenceDao,
    private val remoteStore: GeofenceRemoteStore,
    @ApplicationContext private val appContext: Context,
) {
    private companion object {
        const val TAG = "GeofenceRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeGeofences(familyId: String): Flow<List<KBGeofenceEntity>> =
        geofenceDao.observeByFamily(familyId)

    fun startRealtime(
        familyId: String,
        onError: (Throwable) -> Unit,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && listener != null) return@withLock
                stopRealtimeLocked()
                runCatching {
                    remoteStore.listenGeofences(
                        familyId = familyId,
                        onChange = { changes -> scope.launch { applyInbound(changes) } },
                        onError = { onError(it) },
                    )
                }.onSuccess { registration ->
                    listeningFamilyId = familyId
                    listener = registration
                }.onFailure { err ->
                    KBLog.data.error("startRealtime geofence listen failed familyId=$familyId: ${err.message}", TAG, err)
                    listeningFamilyId = null
                    listener = null
                    onError(err)
                }
            }
        }
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock { stopRealtimeLocked() }
        }
    }

    suspend fun upsert(entity: KBGeofenceEntity) {
        val isNew = geofenceDao.getById(entity.id) == null
        val isFirstUse = isNew && geofenceDao.countByFamilyId(entity.familyId) == 0
        geofenceDao.upsert(entity)
        remoteStore.upsertToFirestore(entity)
        if (isNew) {
            AppAnalytics.contentCreated(appContext, "location")
            if (isFirstUse) {
                AppAnalytics.featureFirstUse(appContext, feature = "location")
            }
        }
    }

    suspend fun setActive(entity: KBGeofenceEntity, active: Boolean) {
        val updated = entity.copy(
            isActive = active,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        upsert(updated)
    }

    suspend fun softDelete(id: String, familyId: String) {
        val existing = geofenceDao.getById(id) ?: return
        val deleted = existing.copy(
            isDeleted = true,
            isActive = false,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        geofenceDao.upsert(deleted)
        remoteStore.softDelete(id, familyId)
    }

    private suspend fun applyInbound(changes: List<GeofenceRemoteChange>) {
        for (change in changes) {
            when (change) {
                is GeofenceRemoteChange.Upsert -> {
                    if (change.dto.isDeleted) {
                        geofenceDao.getById(change.dto.id)?.let { existing ->
                            geofenceDao.upsert(existing.copy(isDeleted = true, isActive = false))
                        }
                    } else {
                        geofenceDao.upsert(geofenceEntityFromDto(change.dto))
                    }
                }
                is GeofenceRemoteChange.Remove -> {
                    geofenceDao.getById(change.id)?.let { existing ->
                        geofenceDao.upsert(existing.copy(isDeleted = true, isActive = false))
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
}
