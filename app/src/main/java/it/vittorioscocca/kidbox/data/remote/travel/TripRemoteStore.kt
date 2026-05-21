package it.vittorioscocca.kidbox.data.remote.travel

import it.vittorioscocca.kidbox.util.KBLog

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.dao.KBPackingItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDayPlanDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripLegDao
import it.vittorioscocca.kidbox.data.local.entity.KBPackingItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripDayPlanEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class TripRemoteStore @Inject constructor(
    private val tripDao: KBTripDao,
    private val tripLegDao: KBTripLegDao,
    private val tripDayPlanDao: KBTripDayPlanDao,
    private val packingItemDao: KBPackingItemDao,
) {
    private val db get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun listenTrips(
        familyId: String,
        onError: (Throwable) -> Unit = {},
    ): ListenerRegistration {
        return db.collection("families")
            .document(familyId)
            .collection("trips")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                scope.launch {
                    runCatching {
                        applyTripsSnapshot(familyId, snapshot)
                    }.onFailure { err ->
                        KBLog.data.error("Failed to apply trips snapshot", TAG, err)
                        onError(err)
                    }
                }
            }
    }

    private suspend fun applyTripsSnapshot(
        familyId: String,
        snapshot: QuerySnapshot,
    ) {
        if (snapshot.documentChanges.isNotEmpty()) {
            for (change in snapshot.documentChanges) {
                when (change.type) {
                    DocumentChange.Type.REMOVED -> deleteTripLocally(change.document.id)
                    else -> applyTripDocument(change.document, familyId)
                }
            }
            return
        }

        val remoteIds = mutableListOf<String>()
        for (doc in snapshot.documents) {
            val trip = decodeTrip(doc, familyId) ?: continue
            remoteIds.add(trip.id)
            tripDao.upsert(trip.copy(syncStateRaw = 0))
            syncSubcollections(familyId, trip.id)
        }

        if (!snapshot.metadata.isFromCache) {
            if (remoteIds.isEmpty()) {
                tripDao.deleteAllForFamily(familyId)
            } else {
                tripDao.deleteAllExcept(familyId, remoteIds)
            }
        }
    }

    private suspend fun applyTripDocument(
        doc: DocumentSnapshot,
        familyId: String,
    ) {
        val trip = decodeTrip(doc, familyId) ?: return
        tripDao.upsert(trip.copy(syncStateRaw = 0))
        syncSubcollections(familyId, trip.id)
    }

    suspend fun deleteTrip(trip: KBTripEntity) {
        runCatching {
            deleteTripRemote(trip)
        }.onFailure { err ->
            KBLog.data.error("Trip remote delete failed tripId=${trip.id}", TAG, err)
        }
        deleteTripLocally(trip.id)
    }

    private suspend fun deleteTripRemote(trip: KBTripEntity) {
        val tripRef = db.collection("families")
            .document(trip.familyId)
            .collection("trips")
            .document(trip.id)

        deleteCollection(tripRef.collection("legs"))
        deleteCollection(tripRef.collection("dayPlans"))
        deleteCollection(tripRef.collection("packingItems"))
        deleteCollection(tripRef.collection("expenses"))
        tripRef.delete().await()
    }

    private suspend fun deleteCollection(
        collection: com.google.firebase.firestore.CollectionReference,
    ) {
        val snap = collection.get().await()
        snap.documents.forEach { doc ->
            doc.reference.delete().await()
        }
    }

    private suspend fun deleteTripLocally(tripId: String) {
        tripLegDao.deleteAllForTrip(tripId)
        tripDayPlanDao.deleteAllForTrip(tripId)
        packingItemDao.deleteAllForTrip(tripId)
        tripDao.deleteById(tripId)
    }

    private suspend fun syncSubcollections(familyId: String, tripId: String) {
        val tripRef = db.collection("families")
            .document(familyId)
            .collection("trips")
            .document(tripId)

        val legsSnap = tripRef.collection("legs").get().await()
        val legs = legsSnap.documents.mapNotNull { decodeLeg(it, familyId, tripId) }
        if (legs.isNotEmpty()) {
            tripLegDao.deleteAllForTrip(tripId)
            tripLegDao.upsertAll(legs)
        }

        val dayPlansSnap = tripRef.collection("dayPlans").get().await()
        val dayPlans = dayPlansSnap.documents.mapNotNull { decodeDayPlan(it, familyId, tripId) }
        if (dayPlans.isNotEmpty()) {
            tripDayPlanDao.deleteAllForTrip(tripId)
            tripDayPlanDao.upsertAll(dayPlans)
        }

        val packingSnap = tripRef.collection("packingItems").get().await()
        val packing = packingSnap.documents.mapNotNull { decodePackingItem(it, familyId, tripId) }
        if (packing.isNotEmpty()) {
            packingItemDao.deleteAllForTrip(tripId)
            packingItemDao.upsertAll(packing)
        }
    }

    suspend fun syncTrip(tripId: String): Result<Unit> {
        val trip = tripDao.getById(tripId)
            ?: return Result.failure(IllegalStateException("Trip non trovato in locale tripId=$tripId"))
        val legs = tripLegDao.observeByTrip(tripId).first()
        val dayPlans = tripDayPlanDao.observeByTrip(tripId).first()
        val packing = packingItemDao.observeByTrip(tripId).first()
        return syncTripEntity(trip, legs, dayPlans, packing)
    }

    suspend fun syncTripEntity(
        trip: KBTripEntity,
        legs: List<KBTripLegEntity>,
        dayPlans: List<KBTripDayPlanEntity>,
        packing: List<KBPackingItemEntity>,
    ): Result<Unit> = runCatching {
        if (trip.familyId.isBlank()) {
            error("familyId mancante sul trip ${trip.id}")
        }
        val tripRef = db.collection("families").document(trip.familyId)
            .collection("trips").document(trip.id)

        legs.forEach { leg ->
            tripRef.collection("legs").document(leg.id).set(
                buildMap {
                    put("order", leg.order)
                    put("fromLocation", leg.fromLocation)
                    put("toLocation", leg.toLocation)
                    put("transportMode", leg.transportModeRaw)
                    put("updatedAt", FieldValue.serverTimestamp())
                    leg.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
                },
                SetOptions.merge(),
            ).await()
        }

        dayPlans.forEach { day ->
            tripRef.collection("dayPlans").document(day.id).set(
                buildMap {
                    put("date", day.dateString)
                    put("location", day.location)
                    put("morningPlan", day.morningPlan)
                    put("afternoonPlan", day.afternoonPlan)
                    put("eveningPlan", day.eveningPlan)
                    put("updatedAt", FieldValue.serverTimestamp())
                    day.accommodationName?.let { put("accommodationName", it) }
                    day.accommodationType?.let { put("accommodationType", it) }
                    day.accommodationCostPerNight?.let { put("accommodationCostPerNight", it) }
                    day.weatherBackupPlan?.let { put("weatherBackupPlan", it) }
                    day.estimatedDailyCost?.let { put("estimatedDailyCost", it) }
                },
                SetOptions.merge(),
            ).await()
        }

        packing.forEach { item ->
            tripRef.collection("packingItems").document(item.id).set(
                mapOf(
                    "label" to item.label,
                    "category" to item.categoryRaw,
                    "isChecked" to item.isChecked,
                    "isAIGenerated" to item.isAIGenerated,
                    "fromMedicalProfile" to item.fromMedicalProfile,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        }

        // Il listener osserva il documento trip e poi rilegge le subcollection:
        // scrivendo il root per ultimo evita di ricaricare dayPlans ancora vecchi.
        tripRef.set(tripDocumentPayload(trip), SetOptions.merge()).await()
        KBLog.data.info("Trip pushed familyId=${trip.familyId} tripId=${trip.id}", TAG)
        tripDao.upsert(trip.copy(syncStateRaw = 0, lastSyncError = null))
    }.onFailure { err ->
        KBLog.data.error("Trip push sync failed familyId=${trip.familyId} tripId=${trip.id}", TAG, err)
        tripDao.upsert(trip.copy(syncStateRaw = 1, lastSyncError = err.message))
    }

    /** Allineato a iOS: niente campi null espliciti (merge sicuro su Firestore). */
    private fun tripDocumentPayload(trip: KBTripEntity): Map<String, Any> = buildMap {
        put("name", trip.name)
        put("startDate", Timestamp(java.util.Date(trip.startDateEpoch)))
        put("endDate", Timestamp(java.util.Date(trip.endDateEpoch)))
        put("participantIdsJson", trip.participantIdsJson)
        put("budgetTotal", trip.budgetTotal)
        put("currency", trip.currency)
        put("status", trip.statusRaw)
        put("createdBy", trip.createdBy)
        put("updatedBy", trip.updatedBy)
        put("createdAt", Timestamp(java.util.Date(trip.createdAtEpoch)))
        put("updatedAt", FieldValue.serverTimestamp())
        trip.aiProposalJson?.takeIf { it.isNotBlank() }?.let { put("aiProposalJson", it) }
        trip.photoAlbumId?.takeIf { it.isNotBlank() }?.let { put("photoAlbumId", it) }
        trip.notesNoteId?.takeIf { it.isNotBlank() }?.let { put("notesNoteId", it) }
        trip.todoListId?.takeIf { it.isNotBlank() }?.let { put("todoListId", it) }
    }

    private fun decodeTrip(doc: DocumentSnapshot, familyId: String): KBTripEntity? {
        val data = doc.data ?: return null
        val start = data["startDate"] as? Timestamp ?: return null
        val end = data["endDate"] as? Timestamp ?: return null
        val now = System.currentTimeMillis()
        return KBTripEntity(
            id = doc.id,
            familyId = familyId,
            name = data["name"] as? String ?: "",
            startDateEpoch = start.toDate().time,
            endDateEpoch = end.toDate().time,
            participantIdsJson = data["participantIdsJson"] as? String ?: "[]",
            budgetTotal = (data["budgetTotal"] as? Number)?.toDouble() ?: 0.0,
            currency = data["currency"] as? String ?: "EUR",
            statusRaw = data["status"] as? String ?: "planning",
            aiProposalJson = data["aiProposalJson"] as? String,
            photoAlbumId = data["photoAlbumId"] as? String,
            notesNoteId = data["notesNoteId"] as? String,
            todoListId = data["todoListId"] as? String,
            createdAtEpoch = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: now,
            updatedAtEpoch = (data["updatedAt"] as? Timestamp)?.toDate()?.time ?: now,
            createdBy = data["createdBy"] as? String ?: "",
            updatedBy = data["updatedBy"] as? String ?: "",
            syncStateRaw = 0,
            lastSyncError = null,
        )
    }

    private fun decodeLeg(
        doc: DocumentSnapshot,
        familyId: String,
        tripId: String,
    ): KBTripLegEntity? {
        val data = doc.data ?: return null
        return KBTripLegEntity(
            id = doc.id,
            familyId = familyId,
            tripId = tripId,
            order = (data["order"] as? Number)?.toInt() ?: 0,
            fromLocation = data["fromLocation"] as? String ?: "",
            toLocation = data["toLocation"] as? String ?: "",
            transportModeRaw = data["transportMode"] as? String ?: "car",
            departureAtEpoch = null,
            arrivalAtEpoch = null,
            notes = data["notes"] as? String,
            updatedAtEpoch = (data["updatedAt"] as? Timestamp)?.toDate()?.time
                ?: System.currentTimeMillis(),
        )
    }

    private fun decodeDayPlan(
        doc: DocumentSnapshot,
        familyId: String,
        tripId: String,
    ): KBTripDayPlanEntity? {
        val data = doc.data ?: return null
        return KBTripDayPlanEntity(
            id = doc.id,
            familyId = familyId,
            tripId = tripId,
            dateString = data["date"] as? String ?: "",
            location = data["location"] as? String ?: "",
            morningPlan = data["morningPlan"] as? String ?: "",
            afternoonPlan = data["afternoonPlan"] as? String ?: "",
            eveningPlan = data["eveningPlan"] as? String ?: "",
            accommodationName = data["accommodationName"] as? String,
            accommodationType = data["accommodationType"] as? String,
            accommodationCostPerNight = (data["accommodationCostPerNight"] as? Number)?.toDouble(),
            weatherBackupPlan = data["weatherBackupPlan"] as? String,
            estimatedDailyCost = (data["estimatedDailyCost"] as? Number)?.toDouble(),
            updatedAtEpoch = (data["updatedAt"] as? Timestamp)?.toDate()?.time
                ?: System.currentTimeMillis(),
        )
    }

    private fun decodePackingItem(
        doc: DocumentSnapshot,
        familyId: String,
        tripId: String,
    ): KBPackingItemEntity? {
        val data = doc.data ?: return null
        return KBPackingItemEntity(
            id = doc.id,
            familyId = familyId,
            tripId = tripId,
            label = data["label"] as? String ?: "",
            categoryRaw = data["category"] as? String ?: "other",
            isChecked = data["isChecked"] as? Boolean ?: false,
            isAIGenerated = data["isAIGenerated"] as? Boolean ?: false,
            fromMedicalProfile = data["fromMedicalProfile"] as? Boolean ?: false,
            updatedAtEpoch = (data["updatedAt"] as? Timestamp)?.toDate()?.time
                ?: System.currentTimeMillis(),
        )
    }

    private companion object {
        const val TAG = "TripRemoteStore"
    }
}
