package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.util.KBLog

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import it.vittorioscocca.kidbox.data.remote.travel.TripRemoteStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TripRepository @Inject constructor(
    private val remoteStore: TripRemoteStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var tripsListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        if (familyId.isBlank()) return
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && tripsListener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId
                tripsListener = remoteStore.listenTrips(
                    familyId = familyId,
                    onError = { err ->
                        if (err is FirebaseFirestoreException &&
                            err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        ) {
                            onPermissionDenied?.invoke()
                        } else {
                            KBLog.data.warning("Trip realtime error: ${err.message}", TAG)
                        }
                    },
                )
            }
        }
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock { stopRealtimeLocked() }
        }
    }

    suspend fun deleteTrip(trip: KBTripEntity): Boolean = remoteStore.deleteTrip(trip)

    private fun stopRealtimeLocked() {
        tripsListener?.remove()
        tripsListener = null
        listeningFamilyId = null
    }

    private companion object {
        const val TAG = "TripRepository"
    }
}
