package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log
import it.vittorioscocca.kidbox.data.local.dao.KBSharedLocationDao
import it.vittorioscocca.kidbox.data.local.entity.KBSharedLocationEntity
import it.vittorioscocca.kidbox.data.remote.location.LocationRemoteStore
import it.vittorioscocca.kidbox.data.remote.location.RemoteSharedLocationDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LocationShareMode(val raw: String) {
    REALTIME("realtime"),
    TEMPORARY("temporary"),
}

@Singleton
class FamilyLocationRepository @Inject constructor(
    private val sharedLocationDao: KBSharedLocationDao,
    private val remoteStore: LocationRemoteStore,
    private val auth: FirebaseAuth,
) {
    private companion object {
        const val TAG = "FamilyLocationRepo"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeSharedUsers(familyId: String): Flow<List<KBSharedLocationEntity>> =
        sharedLocationDao.observeActiveByFamilyId(familyId)

    fun startRealtime(
        familyId: String,
        onError: (Throwable) -> Unit,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && listener != null) return@withLock
                stopRealtimeLocked()
                runCatching {
                    remoteStore.listen(
                        familyId = familyId,
                        onChange = { users ->
                            scope.launch { applyInbound(familyId, users) }
                        },
                        onError = onError,
                    )
                }.onSuccess { registration ->
                    listeningFamilyId = familyId
                    listener = registration
                }.onFailure { err ->
                    Log.e(TAG, "startRealtime listen attach failed familyId=$familyId: ${err.message}", err)
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

    suspend fun startSharing(
        familyId: String,
        displayName: String,
        mode: LocationShareMode,
        expiresAtEpochMillis: Long? = null,
    ) {
        val uid = auth.currentUser?.uid ?: return
        remoteStore.startSharing(
            familyId = familyId,
            uid = uid,
            displayName = displayName,
            modeRaw = mode.raw,
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }

    suspend fun stopSharing(familyId: String) {
        val uid = auth.currentUser?.uid ?: return
        remoteStore.stopSharing(familyId, uid)
    }

    suspend fun updateMyLocation(
        familyId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double?,
        displayName: String,
    ) {
        val uid = auth.currentUser?.uid ?: return
        remoteStore.updateLocation(
            familyId = familyId,
            uid = uid,
            lat = latitude,
            lon = longitude,
            accuracy = accuracy,
            displayName = displayName,
        )
    }

    suspend fun updateDisplayName(
        familyId: String,
        displayName: String,
    ) {
        val uid = auth.currentUser?.uid ?: return
        remoteStore.updateDisplayName(
            familyId = familyId,
            uid = uid,
            displayName = displayName,
        )
    }

    private suspend fun applyInbound(
        familyId: String,
        users: List<RemoteSharedLocationDto>,
    ) {
        val now = System.currentTimeMillis()
        users.forEach { dto ->
            val expiresAt = dto.expiresAtEpochMillis
            val isExpiredTemporary = dto.modeRaw == LocationShareMode.TEMPORARY.raw &&
                expiresAt != null &&
                expiresAt <= now
            val hasCoordinates = dto.latitude != null && dto.longitude != null
            if (!dto.isSharing || !hasCoordinates || isExpiredTemporary) {
                sharedLocationDao.deleteById(dto.id)
                return@forEach
            }
            sharedLocationDao.upsert(
                KBSharedLocationEntity(
                    id = dto.id,
                    familyId = familyId,
                    name = dto.name.ifBlank { "Utente" },
                    latitude = dto.latitude ?: 0.0,
                    longitude = dto.longitude ?: 0.0,
                    accuracyMeters = dto.accuracyMeters,
                    isSharing = dto.isSharing,
                    modeRaw = dto.modeRaw.ifBlank { LocationShareMode.REALTIME.raw },
                    startedAtEpochMillis = dto.startedAtEpochMillis,
                    expiresAtEpochMillis = dto.expiresAtEpochMillis,
                    lastUpdateAtEpochMillis = dto.lastUpdateAtEpochMillis,
                    avatarUrl = dto.avatarUrl,
                ),
            )
        }
    }

    private fun stopRealtimeLocked() {
        listener?.remove()
        listener = null
        listeningFamilyId = null
    }
}
