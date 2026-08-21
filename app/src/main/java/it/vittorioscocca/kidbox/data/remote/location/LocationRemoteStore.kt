package it.vittorioscocca.kidbox.data.remote.location

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteSharedLocationDto(
    val id: String,
    val isSharing: Boolean,
    val name: String,
    val modeRaw: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double?,
    val startedAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val lastUpdateAtEpochMillis: Long?,
    val avatarUrl: String?,
)

/**
 * `ListenerRegistration` composito: alla `remove()` chiude sia il listener
 * principale sia tutti quelli aperti in fan-out, così il chiamante continua a
 * vedere un singolo handle da tenere e rilasciare.
 */
private class FanOutListenerRegistration(
    private val onRemove: () -> Unit,
) : ListenerRegistration {
    private var removed = false

    override fun remove() {
        if (removed) return
        removed = true
        onRemove()
    }
}

@Singleton
class LocationRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val firestore get() = FirebaseFirestore.getInstance()

    private data class Status(
        val isSharing: Boolean,
        val name: String,
        val modeRaw: String,
        val startedAtEpochMillis: Long?,
        val expiresAtEpochMillis: Long?,
        val avatarUrl: String?,
    )

    private data class Coords(
        val lat: Double,
        val lon: Double,
        val accuracy: Double?,
        val lastUpdateAtEpochMillis: Long?,
    )

    /**
     * Ascolta lo stato condivisione di tutta la famiglia (`locations/{uid}`,
     * scritture rare) e apre/chiude in fan-out un listener di coordinate per
     * ogni utente attualmente in sharing (`locations/{uid}/live/current`,
     * scritture frequenti). I due stream vengono uniti a ogni cambiamento
     * dell'uno o dell'altro. `isSharing=false` continua a essere emesso (non
     * filtrato) perché `FamilyLocationRepository.applyInbound` se ne serve
     * per cancellare l'entry locale — stesso contratto di prima.
     */
    fun listen(
        familyId: String,
        onChange: (List<RemoteSharedLocationDto>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration {

        val statusByUid = mutableMapOf<String, Status>()
        val coordListeners = mutableMapOf<String, ListenerRegistration>()
        val coordByUid = mutableMapOf<String, Coords>()

        fun emit() {
            val dtos = statusByUid.map { (uid, status) ->
                val coords = coordByUid[uid]
                RemoteSharedLocationDto(
                    id = uid,
                    isSharing = status.isSharing,
                    name = status.name,
                    modeRaw = status.modeRaw,
                    latitude = coords?.lat,
                    longitude = coords?.lon,
                    accuracyMeters = coords?.accuracy,
                    startedAtEpochMillis = status.startedAtEpochMillis,
                    expiresAtEpochMillis = status.expiresAtEpochMillis,
                    lastUpdateAtEpochMillis = coords?.lastUpdateAtEpochMillis,
                    avatarUrl = status.avatarUrl,
                )
            }
            onChange(dtos)
        }

        fun syncCoordListeners() {
            // Solo chi sta condividendo ha bisogno di un listener sulle
            // coordinate: per gli altri non arriveranno mai scritture lì.
            val wanted = statusByUid.filterValues { it.isSharing }.keys

            val toRemove = coordListeners.keys - wanted
            toRemove.forEach { uid ->
                coordListeners.remove(uid)?.remove()
                coordByUid.remove(uid)
            }

            val toAdd = wanted - coordListeners.keys
            toAdd.forEach { uid ->
                val reg = liveLocationRef(familyId, uid)
                    .addSnapshotListener { snap, _ ->
                        val data = snap?.data
                        val lat = data?.numberOrNull("lat")?.toDouble()
                        val lon = data?.numberOrNull("lon")?.toDouble()
                        if (lat == null || lon == null) {
                            coordByUid.remove(uid)
                        } else {
                            coordByUid[uid] = Coords(
                                lat = lat,
                                lon = lon,
                                accuracy = data.numberOrNull("accuracy")?.toDouble(),
                                lastUpdateAtEpochMillis = data.timestampOrNull("lastUpdateAt"),
                            )
                        }
                        emit()
                    }
                coordListeners[uid] = reg
            }
        }

        val statusReg = firestore
            .collection("families")
            .document(familyId)
            .collection("locations")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                statusByUid.clear()
                docs.forEach { doc ->
                    val data = doc.data.orEmpty()
                    statusByUid[doc.id] = Status(
                        isSharing = data["isSharing"] as? Boolean ?: false,
                        name = data["name"] as? String ?: "",
                        modeRaw = data["mode"] as? String ?: "realtime",
                        startedAtEpochMillis = data.timestampOrNull("startedAt"),
                        expiresAtEpochMillis = data.timestampOrNull("expiresAt"),
                        avatarUrl = data["avatarURL"] as? String,
                    )
                }
                syncCoordListeners()
                emit()
            }

        return FanOutListenerRegistration {
            statusReg.remove()
            coordListeners.values.forEach { it.remove() }
            coordListeners.clear()
        }
    }

    suspend fun startSharing(
        familyId: String,
        uid: String,
        displayName: String,
        modeRaw: String,
        expiresAtEpochMillis: Long?,
    ) {
        val data = mutableMapOf<String, Any?>(
            "isSharing" to true,
            "mode" to modeRaw,
            "name" to displayName,
            "startedAt" to FieldValue.serverTimestamp(),
            "lastUpdateAt" to FieldValue.serverTimestamp(),
        )
        if (expiresAtEpochMillis != null) {
            data["expiresAt"] = com.google.firebase.Timestamp(expiresAtEpochMillis / 1000, 0)
        } else {
            data["expiresAt"] = FieldValue.delete()
        }
        // Allega l'avatar dell'utente al documento locations/{uid} così che gli altri
        // dispositivi (Android e iOS) possano mostrarlo nel cerchio. iOS scrive avatarURL
        // qui in fase di upload foto (AvatarRemoteStore.uploadAvatar); su Android l'URL
        // vive solo in users/{uid}, quindi lo riportiamo qui all'avvio della condivisione.
        fetchUserAvatarUrl(uid)?.let { data["avatarURL"] = it }
        firestore.collection("families")
            .document(familyId)
            .collection("locations")
            .document(uid)
            .set(data, SetOptions.merge())
            .await()
    }

    /**
     * Aggiorna solo le coordinate, su un documento SEPARATO da quello di
     * stato (`locations/{uid}`). Il fix GPS arriva ogni 5 secondi — se
     * scrivesse sullo stesso documento di `startSharing`/`stopSharing`, ogni
     * aggiornamento farebbe scattare `notifyLocationSharingChanged` lato
     * server (che osserva quel documento): con la condivisione attiva era
     * arrivata a essere il 94% di tutte le invocazioni Cloud Functions del
     * progetto. Scrivendo altrove, il trigger dello stato smette di vedere
     * questi aggiornamenti — non serve toccare `index.js`.
     */
    suspend fun updateLocation(
        familyId: String,
        uid: String,
        lat: Double,
        lon: Double,
        accuracy: Double?,
    ) {
        liveLocationRef(familyId, uid)
            .set(
                mapOf(
                    "lat" to lat,
                    "lon" to lon,
                    "accuracy" to accuracy,
                    "lastUpdateAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun stopSharing(
        familyId: String,
        uid: String,
    ) {
        firestore.collection("families")
            .document(familyId)
            .collection("locations")
            .document(uid)
            .set(
                mapOf(
                    "isSharing" to false,
                    "lastUpdateAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun updateDisplayName(
        familyId: String,
        uid: String,
        displayName: String,
    ) {
        firestore.collection("families")
            .document(familyId)
            .collection("locations")
            .document(uid)
            .set(
                mapOf(
                    "name" to displayName,
                    "lastUpdateAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private fun liveLocationRef(familyId: String, uid: String) =
        firestore.collection("families")
            .document(familyId)
            .collection("locations")
            .document(uid)
            .collection("live")
            .document("current")

    private suspend fun fetchUserAvatarUrl(uid: String): String? {
        // Avatar custom caricato dall'utente (users/{uid}.avatarURL).
        runCatching {
            firestore.collection("users")
                .document(uid)
                .get()
                .await()
                .getString("avatarURL")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { return it }
        // Fallback: foto dell'account (es. login Google). Chi non ha mai caricato un
        // avatar custom non ha users/{uid}.avatarURL ma ha FirebaseAuth.photoUrl: senza
        // questo fallback il suo cerchio sulla mappa resterebbe il pin di default.
        return auth.currentUser
            ?.takeIf { it.uid == uid }
            ?.photoUrl?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun Map<String, Any>.numberOrNull(key: String): Number? = this[key] as? Number

    private fun Map<String, Any>.timestampOrNull(key: String): Long? =
        (this[key] as? com.google.firebase.Timestamp)?.toDate()?.time
}
