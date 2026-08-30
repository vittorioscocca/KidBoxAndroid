package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.LoyaltyCardDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBLoyaltyCardEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.remote.DocumentStorageManager
import it.vittorioscocca.kidbox.data.remote.wallet.LoyaltyCardRemoteChange
import it.vittorioscocca.kidbox.data.remote.wallet.LoyaltyCardRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
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

private const val TAG = "LoyaltyCardRepository"

/** Lato della tessera fisica fotografato. Il nome file su Storage deriva da qui. */
enum class LoyaltyCardPhotoSide(val fileBaseName: String) {
    FRONT("front"),
    BACK("back"),
}

/**
 * Repository per le carte fedeltà del Wallet. Mirror di [WalletRepository] ma
 * senza cifratura/PDF/reminder (pattern Repository-centrico Android: DAO
 * Room + listener Firestore + merge LWW dentro un unico repository, niente
 * "SyncCenter" separato come su iOS).
 */
@Singleton
class LoyaltyCardRepository @Inject constructor(
    private val loyaltyCardDao: LoyaltyCardDao,
    private val familyDao: KBFamilyDao,
    private val remoteStore: LoyaltyCardRemoteStore,
    private val documentStorage: DocumentStorageManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inboundMutex = Mutex()
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeActiveByFamilyId(familyId: String): Flow<List<KBLoyaltyCardEntity>> {
        val uid = auth.currentUser?.uid.orEmpty()
        return loyaltyCardDao.observeActiveByFamilyId(familyId, uid)
    }

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && listener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId
                listener = remoteStore.listen(
                    familyId = familyId,
                    onChange = { changes -> scope.launch { applyInbound(familyId, changes) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        } else {
                            KBLog.data.warning("loyaltyCard listen error: ${err.message}", TAG)
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
        scope.launch { realtimeMutex.withLock { stopRealtimeLocked() } }
    }

    suspend fun addCard(
        familyId: String,
        brandId: String?,
        brandName: String,
        cardNumber: String,
        barcodeFormat: String,
        note: String?,
        primaryColorHex: String,
        secondaryColorHex: String,
        logoURL: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Non autenticato")
            val displayName = user.displayName?.trim().orEmpty().ifBlank { "Tu" }
            ensureFamilyExists(familyId)
            val isFirstLoyaltyCard = loyaltyCardDao.countByFamilyId(familyId) == 0

            val cardId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val entity = KBLoyaltyCardEntity(
                id = cardId,
                familyId = familyId,
                brandId = brandId,
                brandName = brandName,
                cardNumber = cardNumber,
                barcodeFormat = barcodeFormat,
                note = note,
                primaryColorHex = primaryColorHex,
                secondaryColorHex = secondaryColorHex,
                logoURL = logoURL,
                createdBy = user.uid,
                createdByName = displayName,
                updatedBy = user.uid,
                updatedByName = displayName,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                isDeleted = false,
                visibilityScope = KBVisibilityScope.FAMILY,
                visibilityMemberIdsJson = "[]",
                syncStateRaw = 1,
            )
            loyaltyCardDao.upsert(entity)
            remoteStore.upsert(entity, displayName, emptyList())
            loyaltyCardDao.upsert(entity.copy(syncStateRaw = 0))
            AppAnalytics.contentCreated(context, "loyalty_card")
            if (isFirstLoyaltyCard) {
                AppAnalytics.featureFirstUse(context, feature = "loyalty_card")
            }
            cardId
        }
    }

    suspend fun updateCard(
        cardId: String,
        familyId: String,
        cardNumber: String,
        note: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Non autenticato")
            val displayName = user.displayName?.trim().orEmpty().ifBlank { "Tu" }
            val existing = loyaltyCardDao.getById(cardId) ?: error("Carta non trovata")
            if (existing.familyId != familyId) error("Famiglia non valida")

            val now = System.currentTimeMillis()
            val updated = existing.copy(
                cardNumber = cardNumber,
                note = note,
                updatedBy = user.uid,
                updatedByName = displayName,
                updatedAtEpochMillis = now,
                syncStateRaw = 1,
            )
            loyaltyCardDao.upsert(updated)
            remoteStore.upsert(updated, displayName, decodeStringList(updated.visibilityMemberIdsJson))
            loyaltyCardDao.upsert(updated.copy(syncStateRaw = 0))
        }
    }

    /**
     * Salva (o sostituisce) la foto fronte/retro della tessera fisica.
     *
     * Il JPEG viene CIFRATO con la chiave di famiglia ([DocumentCryptoManager], via
     * [DocumentStorageManager]) prima dell'upload — la foto può mostrare il nome
     * dell'intestatario, quindi non va in chiaro come il numero carta.
     *
     * Path: `families/{familyId}/wallet/loyaltyCards/{cardId}/{front|back}.jpg.kbenc`.
     *
     * Le Storage Rules (in console, non nel repo) sono un allowlist di sottopath
     * espliciti sotto `families/{familyId}/`: NON c'è un catch-all, quindi un path
     * di primo livello `loyaltyCards/…` viene negato. La regola
     * `match /families/{familyId}/wallet/{allPaths=**}` esiste già e copre questo
     * path senza modificare le regole in produzione. Deve restare identico a quello
     * iOS (`LoyaltyCardPhotoStore.storagePath`) o le foto non sono leggibili
     * cross-piattaforma.
     */
    suspend fun setCardPhoto(
        cardId: String,
        familyId: String,
        side: LoyaltyCardPhotoSide,
        jpegBytes: ByteArray,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Non autenticato")
            val displayName = user.displayName?.trim().orEmpty().ifBlank { "Tu" }
            val existing = loyaltyCardDao.getById(cardId) ?: error("Carta non trovata")
            if (existing.familyId != familyId) error("Famiglia non valida")

            val path = photoStoragePath(familyId, cardId, side)
            val downloadUrl = documentStorage.uploadEncryptedToPath(
                storagePath = path,
                familyId = familyId,
                mimeType = "image/jpeg",
                fileName = "${side.fileBaseName}.jpg",
                plainBytes = jpegBytes,
            )

            val now = System.currentTimeMillis()
            val base = existing.copy(
                updatedBy = user.uid,
                updatedByName = displayName,
                updatedAtEpochMillis = now,
                syncStateRaw = 1,
            )
            val updated = when (side) {
                LoyaltyCardPhotoSide.FRONT -> base.copy(frontPhotoStorageURL = downloadUrl, frontPhotoStoragePath = path)
                LoyaltyCardPhotoSide.BACK -> base.copy(backPhotoStorageURL = downloadUrl, backPhotoStoragePath = path)
            }
            loyaltyCardDao.upsert(updated)
            remoteStore.upsert(updated, displayName, decodeStringList(updated.visibilityMemberIdsJson))
            loyaltyCardDao.upsert(updated.copy(syncStateRaw = 0))
        }
    }

    /** Rimuove la foto di un lato: azzera i campi e cancella il blob su Storage (best-effort). */
    suspend fun removeCardPhoto(
        cardId: String,
        familyId: String,
        side: LoyaltyCardPhotoSide,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Non autenticato")
            val displayName = user.displayName?.trim().orEmpty().ifBlank { "Tu" }
            val existing = loyaltyCardDao.getById(cardId) ?: error("Carta non trovata")
            if (existing.familyId != familyId) error("Famiglia non valida")

            val oldPath = when (side) {
                LoyaltyCardPhotoSide.FRONT -> existing.frontPhotoStoragePath
                LoyaltyCardPhotoSide.BACK -> existing.backPhotoStoragePath
            }

            val now = System.currentTimeMillis()
            val base = existing.copy(
                updatedBy = user.uid,
                updatedByName = displayName,
                updatedAtEpochMillis = now,
                syncStateRaw = 1,
            )
            val updated = when (side) {
                LoyaltyCardPhotoSide.FRONT -> base.copy(frontPhotoStorageURL = null, frontPhotoStoragePath = null)
                LoyaltyCardPhotoSide.BACK -> base.copy(backPhotoStorageURL = null, backPhotoStoragePath = null)
            }
            loyaltyCardDao.upsert(updated)
            remoteStore.upsert(updated, displayName, decodeStringList(updated.visibilityMemberIdsJson))
            loyaltyCardDao.upsert(updated.copy(syncStateRaw = 0))
            if (!oldPath.isNullOrBlank()) documentStorage.delete(oldPath)
        }
    }

    /** Scarica e decifra una foto della tessera. */
    suspend fun loadCardPhoto(storagePath: String, familyId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching { documentStorage.downloadDecrypted(storagePath, familyId) }
                .onFailure { KBLog.data.warning("loadCardPhoto failed path=$storagePath: ${it.message}", TAG) }
                .getOrNull()
        }

    suspend fun deleteCard(cardId: String, familyId: String) = withContext(Dispatchers.IO) {
        // Cleanup best-effort delle foto su Storage prima del soft-delete: dopo
        // la cancellazione i path non sarebbero più raggiungibili dalla UI.
        val existing = loyaltyCardDao.getById(cardId)
        listOfNotNull(existing?.frontPhotoStoragePath, existing?.backPhotoStoragePath)
            .filter { it.isNotBlank() }
            .forEach { path ->
                runCatching { documentStorage.delete(path) }
                    .onFailure { KBLog.data.warning("photo cleanup failed path=$path: ${it.message}", TAG) }
            }
        loyaltyCardDao.softDelete(cardId)
        runCatching { remoteStore.softDelete(cardId, familyId) }
            .onFailure { KBLog.data.warning("remote softDelete failed: ${it.message}", TAG) }
    }

    /**
     * Eliminazione multipla: le singole cancellazioni girano in UN SOLO
     * contesto IO invece di un round-trip per carta, riusando [deleteCard]
     * così cleanup foto su Storage e soft-delete remoto restano identici.
     */
    suspend fun deleteCards(cardIds: List<String>, familyId: String) = withContext(Dispatchers.IO) {
        cardIds.forEach { deleteCard(it, familyId) }
    }

    private fun photoStoragePath(familyId: String, cardId: String, side: LoyaltyCardPhotoSide): String =
        "families/$familyId/wallet/loyaltyCards/$cardId/${side.fileBaseName}.jpg.kbenc"

    private suspend fun applyInbound(
        familyId: String,
        changes: List<LoyaltyCardRemoteChange>,
    ) {
        inboundMutex.withLock {
            changes.forEach { change ->
                when (change) {
                    is LoyaltyCardRemoteChange.Remove -> {
                        loyaltyCardDao.deleteById(change.id)
                    }
                    is LoyaltyCardRemoteChange.Upsert -> {
                        val dto = change.dto
                        if (dto.isDeleted) {
                            loyaltyCardDao.deleteById(dto.id)
                            return@forEach
                        }
                        ensureFamilyExists(dto.familyId)
                        val local = loyaltyCardDao.getById(dto.id)
                        val remoteUpdated = dto.updatedAtEpochMillis ?: 0L
                        val localUpdated = local?.updatedAtEpochMillis ?: 0L
                        if (local != null && remoteUpdated < localUpdated) {
                            return@forEach
                        }
                        val now = System.currentTimeMillis()
                        loyaltyCardDao.upsert(
                            KBLoyaltyCardEntity(
                                id = dto.id,
                                familyId = dto.familyId,
                                brandId = dto.brandId,
                                brandName = dto.brandName,
                                cardNumber = dto.cardNumber,
                                barcodeFormat = dto.barcodeFormat,
                                note = dto.note,
                                primaryColorHex = dto.primaryColorHex,
                                secondaryColorHex = dto.secondaryColorHex,
                                logoURL = dto.logoURL ?: local?.logoURL,
                                // Stesso accorgimento di logoURL: un documento remoto più
                                // vecchio, scritto prima che questi campi esistessero, non
                                // deve cancellare la foto già presente in locale.
                                frontPhotoStorageURL = dto.frontPhotoStorageURL ?: local?.frontPhotoStorageURL,
                                frontPhotoStoragePath = dto.frontPhotoStoragePath ?: local?.frontPhotoStoragePath,
                                backPhotoStorageURL = dto.backPhotoStorageURL ?: local?.backPhotoStorageURL,
                                backPhotoStoragePath = dto.backPhotoStoragePath ?: local?.backPhotoStoragePath,
                                createdBy = dto.createdBy ?: local?.createdBy ?: uidOrLocal(),
                                createdByName = dto.createdByName ?: local?.createdByName.orEmpty(),
                                updatedBy = dto.updatedBy ?: local?.updatedBy.orEmpty(),
                                updatedByName = dto.updatedByName ?: local?.updatedByName.orEmpty(),
                                createdAtEpochMillis = dto.createdAtEpochMillis ?: local?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                                isDeleted = false,
                                visibilityScope = KBVisibilityScope.normalized(dto.visibilityScope),
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
        val now = System.currentTimeMillis()
        val uid = uidOrLocal()
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

    private fun stopRealtimeLocked() {
        listener?.remove()
        listener = null
        listeningFamilyId = null
    }
}
