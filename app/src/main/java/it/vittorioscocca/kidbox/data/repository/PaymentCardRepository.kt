package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.PaymentCardDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPaymentCardEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.remote.DocumentCryptoManager
import it.vittorioscocca.kidbox.data.remote.DocumentStorageManager
import it.vittorioscocca.kidbox.data.remote.wallet.PaymentCardRemoteChange
import it.vittorioscocca.kidbox.data.remote.wallet.PaymentCardRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.screens.wallet.paymentcards.PaymentCardFormat
import it.vittorioscocca.kidbox.ui.screens.wallet.paymentcards.PaymentCardNetwork
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
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
import kotlinx.coroutines.withContext

private const val TAG = "PaymentCardRepository"

/**
 * I campi di una carta decifrati, solo in memoria e solo per mostrarli.
 * `isUnreadable` = chiave di famiglia assente o blob che non si apre.
 * Mirror di `PaymentCardPlain` (iOS).
 */
data class PaymentCardPlain(
    val label: String = "",
    val cardNumber: String = "",
    val holderName: String = "",
    val iban: String = "",
    val expiry: String = "",
    val notes: String = "",
    val pin: String = "",
    val isUnreadable: Boolean = false,
) {
    val network: PaymentCardNetwork get() = PaymentCardNetwork.detect(cardNumber)
    val last4: String get() = cardNumber.takeLast(4)
}

/** Una carta col suo contenuto decifrato, pronta per la UI. */
data class PaymentCardItem(
    val entity: KBPaymentCardEntity,
    val plain: PaymentCardPlain,
)

/**
 * Carte di pagamento del Wallet: DAO Room + listener Firestore + LWW, come
 * [LoyaltyCardRepository]. In Room e su Firestore i campi restano cifrati;
 * [decrypt] li apre solo per la UI.
 */
@Singleton
class PaymentCardRepository @Inject constructor(
    private val dao: PaymentCardDao,
    private val familyDao: KBFamilyDao,
    private val remoteStore: PaymentCardRemoteStore,
    private val documentCrypto: DocumentCryptoManager,
    private val documentStorage: DocumentStorageManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inboundMutex = Mutex()
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeActiveByFamilyId(familyId: String): Flow<List<KBPaymentCardEntity>> =
        dao.observeActiveByFamilyId(familyId, auth.currentUser?.uid.orEmpty())

    fun observeById(cardId: String): Flow<KBPaymentCardEntity?> = dao.observeById(cardId)

    fun startRealtime(familyId: String, onPermissionDenied: (() -> Unit)? = null) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && listener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId
                listener = remoteStore.listen(
                    familyId = familyId,
                    onChange = { changes -> scope.launch { applyInbound(changes) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        } else {
                            KBLog.data.warning("paymentCard listen error: ${err.message}", TAG)
                        }
                    },
                )
            }
        }
    }

    suspend fun awaitForceRestartRealtime(familyId: String) {
        realtimeMutex.withLock { stopRealtimeLocked() }
        startRealtime(familyId)
    }

    fun stopRealtime() {
        scope.launch { realtimeMutex.withLock { stopRealtimeLocked() } }
    }

    // MARK: - Cifratura

    fun decrypt(card: KBPaymentCardEntity): PaymentCardPlain = runCatching {
        fun open(b64: String?): String {
            if (b64.isNullOrBlank()) return ""
            val combined = Base64.decode(b64, Base64.DEFAULT)
            return String(documentCrypto.decrypt(combined, card.familyId), Charsets.UTF_8)
        }
        PaymentCardPlain(
            label = open(card.labelEnc),
            cardNumber = open(card.cardNumberEnc),
            holderName = open(card.holderNameEnc),
            iban = open(card.ibanEnc),
            expiry = open(card.expiryEnc),
            notes = open(card.notesEnc),
            pin = open(card.pinEnc),
        )
    }.getOrElse {
        KBLog.data.warning("paymentCard decrypt failed id=${card.id}", TAG)
        PaymentCardPlain(isUnreadable = true)
    }

    private fun seal(plain: String, familyId: String): String? {
        if (plain.isEmpty()) return null
        val combined = documentCrypto.encrypt(plain.toByteArray(Charsets.UTF_8), familyId)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    // MARK: - Scritture

    /**
     * Crea (`cardId == null`) o aggiorna una carta. Cifra tutto prima di
     * toccare Room: se la chiave di famiglia manca non si salva niente.
     */
    suspend fun saveCard(
        familyId: String,
        cardId: String?,
        plain: PaymentCardPlain,
        colorHex: String,
        visibilityScope: String,
        visibilityMemberIds: List<String>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Non autenticato")
            val displayName = user.displayName?.trim().orEmpty().ifBlank { "Tu" }
            ensureFamilyExists(familyId)
            val existing = cardId?.let { dao.getById(it) }
            if (existing != null && existing.familyId != familyId) error("Famiglia non valida")
            val isFirst = existing == null && dao.countByFamilyId(familyId) == 0

            val scopeValue = KBVisibilityScope.normalizedWallet(visibilityScope)
            val members = if (scopeValue == KBVisibilityScope.MEMBERS) visibilityMemberIds else emptyList()
            val now = System.currentTimeMillis()
            val base = existing ?: KBPaymentCardEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                colorHex = colorHex,
                createdBy = user.uid,
                createdByName = displayName,
                updatedBy = user.uid,
                updatedByName = displayName,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                isDeleted = false,
            )
            val updated = base.copy(
                labelEnc = seal(plain.label, familyId),
                cardNumberEnc = seal(plain.cardNumber, familyId),
                holderNameEnc = seal(plain.holderName, familyId),
                ibanEnc = seal(plain.iban, familyId),
                expiryEnc = seal(plain.expiry, familyId),
                notesEnc = seal(plain.notes, familyId),
                pinEnc = seal(plain.pin, familyId),
                colorHex = colorHex,
                visibilityScope = scopeValue,
                visibilityMemberIdsJson = encodeStringList(members),
                updatedBy = user.uid,
                updatedByName = displayName,
                updatedAtEpochMillis = now,
                syncStateRaw = 1,
            )
            dao.upsert(updated)
            remoteStore.upsert(updated, displayName, members)
            dao.upsert(updated.copy(syncStateRaw = 0))
            if (existing == null) {
                AppAnalytics.contentCreated(context, "payment_card")
                if (isFirst) AppAnalytics.featureFirstUse(context, feature = "payment_card")
            }
            updated.id
        }
    }

    /**
     * Foto di un lato, cifrata prima dell'upload. Path identico a iOS e web:
     * `families/{familyId}/wallet/paymentCards/{cardId}/{front|back}.jpg.kbenc`.
     */
    suspend fun setCardPhoto(
        cardId: String,
        familyId: String,
        side: LoyaltyCardPhotoSide,
        jpegBytes: ByteArray,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = dao.getById(cardId) ?: error("Carta non trovata")
            val path = photoStoragePath(familyId, cardId, side)
            val url = documentStorage.uploadEncryptedToPath(
                storagePath = path,
                familyId = familyId,
                mimeType = "image/jpeg",
                fileName = "${side.fileBaseName}.jpg",
                plainBytes = jpegBytes,
            )
            val updated = when (side) {
                LoyaltyCardPhotoSide.FRONT -> existing.copy(frontPhotoStorageURL = url, frontPhotoStoragePath = path)
                LoyaltyCardPhotoSide.BACK -> existing.copy(backPhotoStorageURL = url, backPhotoStoragePath = path)
            }
            touchAndPush(updated)
        }
    }

    suspend fun removeCardPhoto(
        cardId: String,
        side: LoyaltyCardPhotoSide,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = dao.getById(cardId) ?: error("Carta non trovata")
            val oldPath = when (side) {
                LoyaltyCardPhotoSide.FRONT -> existing.frontPhotoStoragePath
                LoyaltyCardPhotoSide.BACK -> existing.backPhotoStoragePath
            }
            val updated = when (side) {
                LoyaltyCardPhotoSide.FRONT -> existing.copy(frontPhotoStorageURL = null, frontPhotoStoragePath = null)
                LoyaltyCardPhotoSide.BACK -> existing.copy(backPhotoStorageURL = null, backPhotoStoragePath = null)
            }
            touchAndPush(updated)
            if (!oldPath.isNullOrBlank()) {
                runCatching { documentStorage.delete(oldPath) }
                    .onFailure { KBLog.data.warning("photo delete failed: ${it.message}", TAG) }
            }
        }
    }

    suspend fun loadCardPhoto(storagePath: String, familyId: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { documentStorage.downloadDecrypted(storagePath, familyId) }
            .onFailure { KBLog.data.warning("loadCardPhoto failed: ${it.message}", TAG) }
            .getOrNull()
    }

    suspend fun deleteCard(cardId: String, familyId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getById(cardId)
        listOfNotNull(existing?.frontPhotoStoragePath, existing?.backPhotoStoragePath)
            .filter { it.isNotBlank() }
            .forEach { path ->
                runCatching { documentStorage.delete(path) }
                    .onFailure { KBLog.data.warning("photo cleanup failed: ${it.message}", TAG) }
            }
        dao.softDelete(cardId)
        runCatching { remoteStore.softDelete(cardId, familyId) }
            .onFailure { KBLog.data.warning("remote softDelete failed: ${it.message}", TAG) }
    }

    private suspend fun touchAndPush(card: KBPaymentCardEntity) {
        val user = auth.currentUser ?: error("Non autenticato")
        val displayName = user.displayName?.trim().orEmpty().ifBlank { "Tu" }
        val updated = card.copy(
            updatedBy = user.uid,
            updatedByName = displayName,
            updatedAtEpochMillis = System.currentTimeMillis(),
            syncStateRaw = 1,
        )
        dao.upsert(updated)
        remoteStore.upsert(updated, displayName, decodeStringList(updated.visibilityMemberIdsJson))
        dao.upsert(updated.copy(syncStateRaw = 0))
    }

    private fun photoStoragePath(familyId: String, cardId: String, side: LoyaltyCardPhotoSide): String =
        "families/$familyId/wallet/paymentCards/$cardId/${side.fileBaseName}.jpg.kbenc"

    // MARK: - Inbound (LWW)

    private suspend fun applyInbound(changes: List<PaymentCardRemoteChange>) {
        inboundMutex.withLock {
            changes.forEach { change ->
                when (change) {
                    is PaymentCardRemoteChange.Remove -> dao.deleteById(change.id)
                    is PaymentCardRemoteChange.Upsert -> {
                        val dto = change.dto
                        if (dto.isDeleted) {
                            dao.deleteById(dto.id)
                            return@forEach
                        }
                        ensureFamilyExists(dto.familyId)
                        val local = dao.getById(dto.id)
                        // Una cancellazione locale non ancora confermata non si fa resuscitare.
                        if (local?.isDeleted == true) return@forEach
                        val remoteUpdated = dto.updatedAtEpochMillis ?: 0L
                        if (local != null && remoteUpdated < local.updatedAtEpochMillis) return@forEach
                        val now = System.currentTimeMillis()
                        dao.upsert(
                            KBPaymentCardEntity(
                                id = dto.id,
                                familyId = dto.familyId,
                                labelEnc = dto.labelEnc,
                                cardNumberEnc = dto.cardNumberEnc,
                                holderNameEnc = dto.holderNameEnc,
                                ibanEnc = dto.ibanEnc,
                                expiryEnc = dto.expiryEnc,
                                notesEnc = dto.notesEnc,
                                pinEnc = dto.pinEnc,
                                colorHex = dto.colorHex,
                                frontPhotoStorageURL = dto.frontPhotoStorageURL,
                                frontPhotoStoragePath = dto.frontPhotoStoragePath,
                                backPhotoStorageURL = dto.backPhotoStorageURL,
                                backPhotoStoragePath = dto.backPhotoStoragePath,
                                createdBy = dto.createdBy ?: local?.createdBy ?: uidOrLocal(),
                                createdByName = dto.createdByName ?: local?.createdByName.orEmpty(),
                                updatedBy = dto.updatedBy ?: local?.updatedBy.orEmpty(),
                                updatedByName = dto.updatedByName ?: local?.updatedByName.orEmpty(),
                                createdAtEpochMillis = dto.createdAtEpochMillis ?: local?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                                isDeleted = false,
                                visibilityScope = dto.visibilityScope,
                                visibilityMemberIdsJson = encodeStringList(dto.visibilityMemberIds),
                                syncStateRaw = 0,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun uidOrLocal(): String = auth.currentUser?.uid ?: "local"

    private suspend fun ensureFamilyExists(familyId: String) {
        if (familyId.isBlank()) return
        if (familyDao.getById(familyId) != null) return
        val uid = uidOrLocal()
        // Riga segnaposto con updatedAt = 0: il dato remoto vince il LWW.
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
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0,
                lastSyncAtEpochMillis = null,
                lastSyncError = null,
            ),
        )
    }

    private fun stopRealtimeLocked() {
        listener?.remove()
        listener = null
        listeningFamilyId = null
    }
}

/** Normalizza i campi del form prima di cifrarli. */
fun PaymentCardPlain.normalizedForSave(): PaymentCardPlain = copy(
    label = label.trim(),
    cardNumber = PaymentCardFormat.digits(cardNumber),
    holderName = holderName.trim(),
    expiry = if (PaymentCardFormat.isValidExpiry(expiry)) expiry else "",
    iban = PaymentCardFormat.ibanCompact(iban),
    notes = notes.trim(),
    pin = pin.filter { it.isDigit() }.take(8),
)
