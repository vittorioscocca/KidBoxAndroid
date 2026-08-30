package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.HousePaymentDao
import it.vittorioscocca.kidbox.ui.screens.life.housePaymentPresetSubtypes
import it.vittorioscocca.kidbox.ui.screens.life.isHousePaymentPresetSubtype
import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import it.vittorioscocca.kidbox.data.remote.life.HousePaymentRemoteChange
import it.vittorioscocca.kidbox.data.remote.life.HousePaymentRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.notifications.HousePaymentReminderScheduler
import it.vittorioscocca.kidbox.util.KBLog
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
class HousePaymentRepository @Inject constructor(
    private val housePaymentDao: HousePaymentDao,
    private val remoteStore: HousePaymentRemoteStore,
    private val expenseRepository: ExpenseRepository,
    private val auth: FirebaseAuth,
    private val housePaymentReminderScheduler: HousePaymentReminderScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var boundFamilyId: String? = null
    private var subscriberCount: Int = 0

    fun observeByFamily(familyId: String): Flow<List<HousePaymentEntity>> =
        housePaymentDao.observeByFamily(familyId)

    fun observeById(id: String): Flow<HousePaymentEntity?> = housePaymentDao.observeById(id)

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (familyId.isBlank()) return@withLock
                if (boundFamilyId != null && boundFamilyId != familyId) {
                    listener?.remove()
                    listener = null
                    subscriberCount = 0
                }
                boundFamilyId = familyId
                subscriberCount++
                if (listener != null) return@withLock
                attachListenerLocked(familyId, onPermissionDenied)
            }
        }
    }

    /** Aggancio del listener: chiamare solo con [realtimeMutex] già preso. */
    private fun attachListenerLocked(
        familyId: String,
        onPermissionDenied: (() -> Unit)?,
    ) {
        listener = remoteStore.listenHousePayments(
            familyId = familyId,
            onChange = { changes -> scope.launch { applyInbound(changes) } },
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

    /**
     * Pull-to-refresh: stacca e riaggancia il listener realtime.
     *
     * [startRealtime] da solo non basta — con il listener già attivo esce dal
     * guard senza fare nulla. Qui il listener viene rimosso e riagganciato
     * dentro lo stesso lock, ma `subscriberCount` NON si tocca: le schermate
     * agganciate non sono cambiate, e alterarlo farebbe cadere il listener al
     * primo [stopRealtime]. Stesso idioma di
     * [PasswordsRepository.awaitForceRestartRealtime].
     */
    suspend fun awaitForceRestartRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        if (familyId.isBlank()) return
        realtimeMutex.withLock {
            listener?.remove()
            listener = null
            boundFamilyId = familyId
            attachListenerLocked(familyId, onPermissionDenied)
        }
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock {
                subscriberCount = (subscriberCount - 1).coerceAtLeast(0)
                if (subscriberCount == 0) {
                    listener?.remove()
                    listener = null
                    boundFamilyId = null
                }
            }
        }
    }

    suspend fun addHousePayment(
        familyId: String,
        name: String,
        typeRaw: String,
        subtypeRaw: String?,
        importo: Double?,
        giornoDiScadenzaMensile: Int?,
        dataScadenza: Long?,
        dataScadenzaContratto: Long?,
        fornitore: String?,
        note: String?,
        reminderOn: Boolean,
        presetPaymentId: String? = null,
        // Titolo di ripiego per la spesa quando la scadenza non ha un nome.
        expenseFallbackTitle: String? = null,
    ) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val id = presetPaymentId?.trim()?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()

        // Una scadenza con un importo è anche una spesa di famiglia: l'importo
        // si scrive una volta sola, qui, e la voce in Spese nasce da sé.
        val linkedExpenseId = if ((importo ?: 0.0) > 0.0) {
            runCatching {
                // La categoria "Casa" deve esistere prima di agganciarcisi.
                expenseRepository.seedDefaultCategories(familyId)
                expenseRepository.createExpenseLocal(
                    familyId = familyId,
                    title = name.trim().ifEmpty { expenseFallbackTitle?.trim().orEmpty().ifEmpty { "Scadenza casa" } },
                    amount = importo ?: 0.0,
                    // La scadenza vera se c'è: una bolletta datata deve cadere
                    // nel mese in cui si paga, non in quello in cui la registri.
                    dateEpochMillis = dataScadenza ?: now,
                    categoryId = ExpenseRepository.defaultCategoryId(familyId, "casa"),
                    notes = listOfNotNull(
                        fornitore?.trim()?.takeIf { it.isNotEmpty() },
                        note?.trim()?.takeIf { it.isNotEmpty() },
                    ).joinToString(" · ").takeIf { it.isNotEmpty() },
                ).id
            }.getOrNull()
        } else {
            null
        }

        val entity = HousePaymentEntity(
            id = id,
            familyId = familyId,
            name = name,
            typeRaw = typeRaw,
            subtypeRaw = subtypeRaw,
            importo = importo,
            giornoDiScadenzaMensile = giornoDiScadenzaMensile,
            dataScadenza = dataScadenza,
            dataScadenzaContratto = dataScadenzaContratto,
            fornitore = fornitore,
            note = note,
            linkedExpenseId = linkedExpenseId,
            reminderOn = reminderOn,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
            createdBy = uid,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        housePaymentDao.upsert(entity)
        if (linkedExpenseId != null) {
            runCatching { expenseRepository.flushPending(familyId) }
        }
        runCatching { remoteStore.upsertHousePaymentToFirestore(entity) }
            .onSuccess {
                val synced = entity.copy(syncState = KBSyncState.SYNCED.rawValue)
                housePaymentDao.upsert(synced)
                housePaymentReminderScheduler.syncPayment(synced)
            }
            .onFailure {
                housePaymentDao.upsert(entity.copy(syncState = KBSyncState.ERROR.rawValue))
                throw it
            }
    }

    /**
     * Ripulisce i sottotipi rimasti attaccati da un tipo precedente.
     *
     * Il form iOS partiva su Bolletta con `subtypeRaw = "luce"` e, cambiando tipo
     * in Mutuo/Affitto/Altro, non lo azzerava: il valore finiva salvato su
     * Firestore e da lì arrivava anche qui. Il form è corretto su entrambe le
     * piattaforme, ma i record già scritti restano sporchi.
     *
     * Si tocca solo un sottotipo che è **esattamente** un preset di un altro
     * tipo: il testo libero scritto dall'utente non viene mai perso. Gemella di
     * `KBHousePayment.cleanupInheritedSubtypes` su iOS ed è idempotente, quindi
     * non fa danni se ha già girato l'altra piattaforma.
     */
    suspend fun cleanupInheritedSubtypes(context: Context, familyId: String) {
        if (familyId.isBlank()) return
        val prefs = context.getSharedPreferences(CLEANUP_PREFS, Context.MODE_PRIVATE)
        val key = CLEANUP_KEY_PREFIX + familyId
        if (prefs.getBoolean(key, false)) return

        var fixed = 0
        for (payment in housePaymentDao.listActiveByFamily(familyId)) {
            val raw = payment.subtypeRaw?.takeIf { it.isNotBlank() } ?: continue
            if (housePaymentPresetSubtypes(payment.typeRaw).isNotEmpty()) continue
            if (!isHousePaymentPresetSubtype(raw)) continue
            runCatching { updateHousePayment(payment.copy(subtypeRaw = null)) }
                .onSuccess { fixed++ }
                .onFailure {
                    KBLog.data.error(
                        "cleanupInheritedSubtypes: ${payment.id} non aggiornato: ${it.message}",
                        "HousePayments",
                    )
                }
        }

        if (fixed > 0) {
            KBLog.data.info(
                "cleanupInheritedSubtypes: ripuliti $fixed sottotipi ereditati familyId=$familyId",
                "HousePayments",
            )
        }
        prefs.edit().putBoolean(key, true).apply()
    }

    suspend fun updateHousePayment(entity: HousePaymentEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val local = entity.copy(
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        housePaymentDao.upsert(local)
        runCatching { remoteStore.upsertHousePaymentToFirestore(local) }
            .onSuccess {
                val synced = local.copy(syncState = KBSyncState.SYNCED.rawValue)
                housePaymentDao.upsert(synced)
                housePaymentReminderScheduler.syncPayment(synced)
            }
            .onFailure {
                housePaymentDao.upsert(local.copy(syncState = KBSyncState.ERROR.rawValue))
                throw it
            }
    }

    suspend fun deleteHousePayment(entity: HousePaymentEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val tombstone = entity.copy(
            isDeleted = true,
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_DELETE.rawValue,
        )
        housePaymentDao.upsert(tombstone)
        housePaymentReminderScheduler.syncPayment(tombstone)
        runCatching { remoteStore.softDelete(entity.familyId, entity.id) }
            .onSuccess {
                housePaymentDao.upsert(tombstone.copy(syncState = KBSyncState.SYNCED.rawValue))
                housePaymentReminderScheduler.cancelForPayment(entity.id)
            }
    }

    private suspend fun applyInbound(changes: List<HousePaymentRemoteChange>) {
        changes.forEach { change ->
            when (change) {
                is HousePaymentRemoteChange.Remove -> {
                    val local = housePaymentDao.getById(change.id) ?: return@forEach
                    housePaymentReminderScheduler.cancelForPayment(change.id)
                    housePaymentDao.upsert(local.copy(isDeleted = true, syncState = KBSyncState.SYNCED.rawValue))
                }
                is HousePaymentRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) {
                        housePaymentReminderScheduler.cancelForPayment(dto.id)
                        return@forEach
                    }
                    val local = housePaymentDao.getById(dto.id)
                    val remoteTs = dto.updatedAtMillis ?: 0L
                    if (
                        local != null &&
                        KBSyncState.fromRaw(local.syncState) == KBSyncState.PENDING_DELETE
                    ) {
                        return@forEach
                    }
                    if (local != null && remoteTs < local.updatedAt) return@forEach
                    val now = System.currentTimeMillis()
                    housePaymentDao.upsert(
                        HousePaymentEntity(
                            id = dto.id,
                            familyId = dto.familyId,
                            name = dto.name,
                            typeRaw = dto.typeRaw,
                            subtypeRaw = dto.subtypeRaw,
                            importo = dto.importo,
                            linkedExpenseId = dto.linkedExpenseId,
                            giornoDiScadenzaMensile = dto.giornoDiScadenzaMensile,
                            dataScadenza = dto.dataScadenzaMillis,
                            dataScadenzaContratto = dto.dataScadenzaContrattoMillis,
                            fornitore = dto.fornitore,
                            note = dto.note,
                            reminderOn = dto.reminderOn,
                            isDeleted = false,
                            createdAt = local?.createdAt ?: (dto.createdAtMillis ?: now),
                            updatedAt = dto.updatedAtMillis ?: now,
                            createdBy = local?.createdBy ?: (dto.createdBy ?: ""),
                            updatedBy = dto.updatedBy ?: "",
                            syncState = KBSyncState.SYNCED.rawValue,
                        ),
                    )
                    housePaymentDao.getById(dto.id)?.let { housePaymentReminderScheduler.syncPayment(it) }
                }
            }
        }
    }

    private companion object {
        const val CLEANUP_PREFS = "kidbox_house_payments"
        const val CLEANUP_KEY_PREFIX = "subtypeCleanup."
    }
}
