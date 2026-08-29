package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBShoppingTripDao
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBShoppingTripEntity
import it.vittorioscocca.kidbox.data.remote.shoppingtrip.ShoppingTripRemoteChange
import it.vittorioscocca.kidbox.data.remote.shoppingtrip.ShoppingTripRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBShoppingTripLine
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.domain.model.ShoppingTripLines
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Le spese fatte: realtime e archiviazione dei prodotti spuntati.
 *
 * Lo scontrino è il dettaglio; i soldi restano nella spesa collegata della
 * sezione Spese, che è l'unica voce contata nei conti di famiglia.
 */
@Singleton
class ShoppingTripRepository @Inject constructor(
    private val tripDao: KBShoppingTripDao,
    private val remoteStore: ShoppingTripRemoteStore,
    private val groceryRepository: GroceryRepository,
    private val expenseRepository: ExpenseRepository,
    private val auth: FirebaseAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null

    fun observeByFamilyId(familyId: String): Flow<List<KBShoppingTripEntity>> =
        tripDao.observeByFamilyId(familyId)

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                listener?.remove()
                listener = remoteStore.listenShoppingTrips(
                    familyId = familyId,
                    onChange = { changes, snapshotIds ->
                        scope.launch { applyInboundChanges(familyId, changes, snapshotIds) }
                    },
                    onError = { err ->
                        if (
                            err is FirebaseFirestoreException &&
                            err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        ) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )
            }
        }
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock {
                listener?.remove()
                listener = null
            }
        }
    }

    /**
     * Archivia i prodotti spuntati come scontrino e crea la spesa corrispondente.
     * I prodotti escono dalla lista: lo scontrino li conserva.
     */
    suspend fun saveTrip(
        familyId: String,
        storeName: String?,
        total: Double,
        dateEpochMillis: Long,
        purchasedItems: List<KBGroceryItemEntity>,
        fallbackTitle: String,
    ) {
        if (purchasedItems.isEmpty()) return
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val store = storeName?.trim()?.takeIf { it.isNotEmpty() }
        val lines = purchasedItems.map { KBShoppingTripLine(it.name, it.quantity?.takeIf { q -> q > 1 }) }
        // L'elenco finisce anche nelle note della spesa: chi apre la sezione
        // Spese vede cosa c'era dentro senza tornare qui.
        val notes = lines.joinToString(", ") { line ->
            if ((line.quantity ?: 1) > 1) "${line.name} x${line.quantity}" else line.name
        }.takeIf { it.isNotEmpty() }

        // La categoria "Spesa" deve esistere prima di agganciarci la spesa: è una
        // chiave esterna, e su una famiglia mai aperta nella sezione Spese le
        // categorie di default non sono ancora state create.
        expenseRepository.seedDefaultCategories(familyId)
        val expense = expenseRepository.createExpenseLocal(
            familyId = familyId,
            title = store ?: fallbackTitle,
            amount = total,
            dateEpochMillis = dateEpochMillis,
            categoryId = ExpenseRepository.defaultCategoryId(familyId, "spesa"),
            notes = notes,
        )

        val trip = KBShoppingTripEntity(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            storeName = store,
            total = total,
            dateEpochMillis = dateEpochMillis,
            linesJson = ShoppingTripLines.encode(lines),
            notes = notes,
            linkedExpenseId = expense.id,
            isDeleted = false,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            createdBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        tripDao.upsert(trip)

        // Il locale è già a posto: se la rete manca, lo scontrino resta in
        // attesa e riparte al prossimo salvataggio.
        runCatching { remoteStore.upsert(trip) }
            .onSuccess {
                tripDao.upsert(trip.copy(syncStateRaw = KBSyncState.SYNCED.rawValue, lastSyncError = null))
            }
            .onFailure { err ->
                tripDao.upsert(
                    trip.copy(
                        syncStateRaw = KBSyncState.ERROR.rawValue,
                        lastSyncError = err.message,
                    ),
                )
            }

        runCatching { expenseRepository.flushPending(familyId) }

        purchasedItems.forEach { item ->
            runCatching { groceryRepository.deleteItem(item.id) }
        }
    }

    /**
     * Lo scontrino sparisce, la spesa collegata no: i soldi sono usciti comunque,
     * e cancellarli da qui sarebbe una sorpresa nei conti.
     */
    suspend fun deleteTrip(tripId: String) {
        val existing = tripDao.getById(tripId) ?: return
        runCatching { remoteStore.softDelete(existing.familyId, tripId) }
            .onSuccess { tripDao.deleteById(tripId) }
            .onFailure { err ->
                tripDao.upsert(
                    existing.copy(
                        isDeleted = true,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                        syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
                        lastSyncError = err.message,
                    ),
                )
            }
    }

    /** Riprova quello che era rimasto indietro senza rete. */
    suspend fun flushPending(familyId: String) {
        tripDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue)
            .plus(tripDao.getBySyncState(familyId, KBSyncState.ERROR.rawValue))
            .forEach { local ->
                runCatching { remoteStore.upsert(local) }
                    .onSuccess {
                        tripDao.upsert(
                            local.copy(
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                    }
            }

        tripDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue)
            .forEach { local ->
                runCatching { remoteStore.softDelete(familyId, local.id) }
                    .onSuccess { tripDao.deleteById(local.id) }
            }
    }

    private suspend fun applyInboundChanges(
        familyId: String,
        changes: List<ShoppingTripRemoteChange>,
        snapshotIds: Set<String>?,
    ) {
        changes.forEach { change ->
            when (change) {
                is ShoppingTripRemoteChange.Remove -> tripDao.deleteById(change.id)
                is ShoppingTripRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) {
                        tripDao.deleteById(dto.id)
                        return@forEach
                    }
                    val local = tripDao.getById(dto.id)
                    // Anti-resurrect: quello che sta uscendo di scena resta fuori.
                    if (
                        local != null &&
                        (local.isDeleted || KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.PENDING_DELETE)
                    ) {
                        return@forEach
                    }
                    val remoteTs = dto.updatedAtEpochMillis ?: 0L
                    if (local != null && remoteTs < local.updatedAtEpochMillis) return@forEach
                    val now = System.currentTimeMillis()
                    tripDao.upsert(
                        KBShoppingTripEntity(
                            id = dto.id,
                            familyId = dto.familyId,
                            storeName = dto.storeName,
                            total = dto.total,
                            dateEpochMillis = dto.dateEpochMillis,
                            linesJson = dto.linesJson,
                            notes = dto.notes,
                            linkedExpenseId = dto.linkedExpenseId,
                            isDeleted = false,
                            createdAtEpochMillis = local?.createdAtEpochMillis ?: (dto.updatedAtEpochMillis ?: now),
                            updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                            updatedBy = dto.updatedBy,
                            createdBy = local?.createdBy ?: dto.createdBy,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ),
                    )
                }
            }
        }

        // Riconciliazione: uno scontrino cancellato altrove può non arrivare mai
        // come REMOVED sul delta. Si saltano le scritture locali non ancora
        // inviate: non sono nel result set remoto perché non ci sono ancora
        // arrivate, non perché siano state cancellate.
        if (snapshotIds != null) {
            runCatching {
                tripDao.listByFamilyId(familyId).forEach { local ->
                    if (local.id in snapshotIds) return@forEach
                    val sync = KBSyncState.fromRaw(local.syncStateRaw)
                    if (sync == KBSyncState.PENDING_UPSERT || sync == KBSyncState.ERROR) return@forEach
                    tripDao.deleteById(local.id)
                }
            }
        }
    }
}
