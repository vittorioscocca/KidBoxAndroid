package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.WalletTicketDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.remote.DocumentCryptoManager
import it.vittorioscocca.kidbox.data.remote.DocumentStorageManager
import it.vittorioscocca.kidbox.data.remote.wallet.WalletRemoteChange
import it.vittorioscocca.kidbox.data.remote.wallet.WalletRemoteStore
import it.vittorioscocca.kidbox.data.remote.wallet.WalletTicketRemoteDto
import it.vittorioscocca.kidbox.data.wallet.WalletParsedData
import it.vittorioscocca.kidbox.data.wallet.WalletPdfParser
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.notifications.WalletReminderScheduler
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

private const val TAG = "WalletRepository"

@Singleton
class WalletRepository @Inject constructor(
    private val walletTicketDao: WalletTicketDao,
    private val familyDao: KBFamilyDao,
    private val remoteStore: WalletRemoteStore,
    private val documentCrypto: DocumentCryptoManager,
    private val documentStorage: DocumentStorageManager,
    private val walletReminderScheduler: WalletReminderScheduler,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inboundMutex = Mutex()
    private val realtimeMutex = Mutex()
    private var walletListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeActiveByFamilyId(familyId: String): Flow<List<KBWalletTicketEntity>> {
        val uid = auth.currentUser?.uid.orEmpty()
        return walletTicketDao.observeActiveByFamilyId(familyId, uid)
    }

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && walletListener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId
                walletListener = remoteStore.listen(
                    familyId = familyId,
                    onChange = { changes -> scope.launch { applyInbound(familyId, changes) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        } else {
                            KBLog.data.warning("wallet listen error: ${err.message}", TAG)
                        }
                    },
                )
            }
        }
    }

    fun stopRealtime() {
        scope.launch { realtimeMutex.withLock { stopRealtimeLocked() } }
    }

    suspend fun addTicket(
        familyId: String,
        pdfBytes: ByteArray,
        fileName: String,
        parsed: WalletParsedData,
        title: String,
        visibilityScope: String,
        visibilityMemberIds: List<String>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Non autenticato")
            val displayName = user.displayName?.trim().orEmpty().ifBlank { "Tu" }
            ensureFamilyExists(familyId)
            val isFirstUse = walletTicketDao.countByFamilyId(familyId) == 0

            val ticketId = UUID.randomUUID().toString()
            val storagePath = "families/$familyId/wallet/$ticketId/ticket.pdf.kbenc"
            val encryptedPdf = documentCrypto.encrypt(pdfBytes, familyId)
            val encSize = encryptedPdf.size.toLong()
            val downloadUrl = documentStorage.uploadEncryptedBytes(
                storagePath = storagePath,
                encrypted = encryptedPdf,
                mimeType = "application/pdf",
                fileName = fileName,
            )

            val now = System.currentTimeMillis()
            val effectiveScope = KBVisibilityScope.normalizedWallet(visibilityScope)
            val membersJson = encodeStringList(
                if (effectiveScope == KBVisibilityScope.MEMBERS) visibilityMemberIds else emptyList(),
            )
            val entity = KBWalletTicketEntity(
                id = ticketId,
                familyId = familyId,
                title = title,
                kindRaw = parsed.kind.raw,
                eventDateEpochMillis = parsed.eventDate,
                eventEndDateEpochMillis = parsed.eventEndDate,
                location = parsed.location,
                seat = null,
                bookingCode = parsed.bookingCode,
                arrivalLocation = parsed.arrivalLocation,
                holderName = parsed.holderName,
                notes = parsed.notes,
                emitter = parsed.emitter,
                pdfStorageURL = downloadUrl,
                pdfStorageBytes = encSize,
                pdfFileName = fileName,
                pdfThumbnailBase64 = parsed.thumbnailBase64,
                addToAppleWalletURL = null,
                barcodeText = parsed.barcodeText,
                barcodeFormat = parsed.barcodeFormat,
                createdBy = user.uid,
                createdByName = displayName,
                updatedBy = user.uid,
                updatedByName = displayName,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                isDeleted = false,
                visibilityScope = effectiveScope,
                visibilityMemberIdsJson = membersJson,
                syncStateRaw = 1,
            )
            walletTicketDao.upsert(entity)

            remoteStore.upsert(entity, displayName)
            walletTicketDao.upsert(entity.copy(syncStateRaw = 0))
            walletReminderScheduler.rescheduleForFamily(familyId)
            AppAnalytics.contentCreated(context, "wallet")
            if (isFirstUse) {
                AppAnalytics.featureFirstUse(context, feature = "wallet")
            }
            ticketId
        }
    }

    suspend fun deleteTicket(ticketId: String, familyId: String) = withContext(Dispatchers.IO) {
        walletReminderScheduler.cancelTicket(ticketId)
        walletTicketDao.softDelete(ticketId)
        runCatching { remoteStore.softDelete(ticketId, familyId) }
            .onFailure { KBLog.data.warning("remote softDelete failed: ${it.message}", TAG) }
    }

    suspend fun updateWalletTicketVisibility(
        ticketId: String,
        familyId: String,
        visibilityScope: String,
        visibilityMemberIds: List<String>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Non autenticato")
            val displayName = user.displayName?.trim().orEmpty().ifBlank { "Tu" }
            val existing = walletTicketDao.getById(ticketId) ?: error("Biglietto non trovato")
            if (existing.familyId != familyId) error("Famiglia non valida")
            val cid = existing.createdBy.trim()
            if (cid.isNotEmpty() && cid != user.uid) {
                error("Solo chi ha creato il biglietto può modificare la visibilità")
            }

            val effectiveScope = KBVisibilityScope.normalizedWallet(visibilityScope)
            val membersJson = encodeStringList(
                if (effectiveScope == KBVisibilityScope.MEMBERS) visibilityMemberIds else emptyList(),
            )
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                visibilityScope = effectiveScope,
                visibilityMemberIdsJson = membersJson,
                updatedBy = user.uid,
                updatedByName = displayName,
                updatedAtEpochMillis = now,
                syncStateRaw = 1,
            )
            walletTicketDao.upsert(updated)
            remoteStore.upsert(updated, displayName)
            walletTicketDao.upsert(updated.copy(syncStateRaw = 0))
        }
    }

    /**
     * Cambia il promemoria di UN biglietto. `null` = torna al comportamento
     * legacy (1h prima, come oggi); `0`/`1`/`24`/`48` = scelta esplicita
     * dell'utente. Vedi [KBWalletTicketEntity.reminderOffsetHours].
     */
    suspend fun updateTicketReminderOffset(
        ticketId: String,
        familyId: String,
        reminderOffsetHours: Int?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Non autenticato")
            val displayName = user.displayName?.trim().orEmpty().ifBlank { "Tu" }
            val existing = walletTicketDao.getById(ticketId) ?: error("Biglietto non trovato")
            if (existing.familyId != familyId) error("Famiglia non valida")

            val now = System.currentTimeMillis()
            val updated = existing.copy(
                reminderOffsetHours = reminderOffsetHours,
                updatedBy = user.uid,
                updatedByName = displayName,
                updatedAtEpochMillis = now,
                syncStateRaw = 1,
            )
            walletTicketDao.upsert(updated)
            remoteStore.upsert(updated, displayName)
            walletTicketDao.upsert(updated.copy(syncStateRaw = 0))
            walletReminderScheduler.rescheduleForFamily(familyId)
        }
    }

    suspend fun openPdf(familyId: String, ticketId: String): ByteArray = withContext(Dispatchers.IO) {
        val ticket = walletTicketDao.getById(ticketId)
            ?: error("Biglietto non trovato")
        val uid = auth.currentUser?.uid
        if (!KBVisibilityScope.isVisible(
                KBVisibilityScope.normalizedWallet(ticket.visibilityScope),
                decodeStringList(ticket.visibilityMemberIdsJson),
                ticket.createdBy,
                uid,
            )
        ) {
            error("Biglietto non disponibile")
        }
        val storagePath = "families/$familyId/wallet/$ticketId/ticket.pdf.kbenc"
        documentStorage.downloadDecrypted(storagePath, familyId)
    }

    suspend fun importPdfFromUri(
        familyId: String,
        uri: Uri,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            auth.currentUser ?: error("Non autenticato")
            val displayName = auth.currentUser?.displayName?.trim().orEmpty().ifBlank { "Tu" }
            ensureFamilyExists(familyId)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Impossibile leggere il PDF")
            val ticketId = UUID.randomUUID().toString()
            var title = queryDisplayName(uri) ?: "Biglietto.pdf"
            if (title.isBlank()) title = "Biglietto.pdf"
            val parsed = runCatching {
                WalletPdfParser.parse(context, bytes, title.removeSuffix(".pdf"))
            }.getOrNull()
            val storagePath = "families/$familyId/wallet/$ticketId/ticket.pdf.kbenc"
            val encryptedPdf = documentCrypto.encrypt(bytes, familyId)
            val encSize = encryptedPdf.size.toLong()
            val downloadUrl = documentStorage.uploadEncryptedBytes(
                storagePath = storagePath,
                encrypted = encryptedPdf,
                mimeType = "application/pdf",
                fileName = title,
            )
            val encTitle = encryptToB64(title, familyId)
            val encFile = encryptToB64(title, familyId)
            remoteStore.upsertImportedTicket(
                familyId = familyId,
                ticketId = ticketId,
                titleEnc = encTitle,
                fileNameEnc = encFile,
                pdfStorageURL = downloadUrl,
                pdfStorageBytes = encSize,
                displayName = displayName,
                kindRaw = parsed?.kind?.raw ?: "other",
                emitter = parsed?.emitter,
                eventDateEpochMillis = parsed?.eventDate,
            )
            ticketId
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }

    private suspend fun applyInbound(
        familyId: String,
        changes: List<WalletRemoteChange>,
    ) {
        inboundMutex.withLock {
            changes.forEach { change ->
                when (change) {
                    is WalletRemoteChange.Remove -> {
                        walletReminderScheduler.cancelTicket(change.id)
                        walletTicketDao.deleteById(change.id)
                    }
                    is WalletRemoteChange.Upsert -> {
                        val dto = change.dto
                        if (dto.isDeleted) {
                            walletReminderScheduler.cancelTicket(dto.id)
                            walletTicketDao.deleteById(dto.id)
                            return@forEach
                        }
                        ensureFamilyExists(dto.familyId)
                        val decrypted = decryptDto(dto, familyId) ?: return@forEach
                        val local = walletTicketDao.getById(dto.id)
                        val remoteUpdated = dto.updatedAtEpochMillis ?: 0L
                        val localUpdated = local?.updatedAtEpochMillis ?: 0L
                        if (local != null && remoteUpdated < localUpdated) {
                            return@forEach
                        }
                        val now = System.currentTimeMillis()
                        walletTicketDao.upsert(
                            KBWalletTicketEntity(
                                id = dto.id,
                                familyId = dto.familyId,
                                title = decrypted.title,
                                kindRaw = dto.kindRaw ?: "other",
                                eventDateEpochMillis = dto.eventDateEpochMillis,
                                eventEndDateEpochMillis = dto.eventEndDateEpochMillis,
                                location = decrypted.location,
                                seat = decrypted.seat,
                                bookingCode = decrypted.bookingCode,
                                arrivalLocation = decrypted.arrivalLocation,
                                holderName = decrypted.holderName,
                                notes = decrypted.notes,
                                emitter = dto.emitter,
                                pdfStorageURL = dto.pdfStorageURL,
                                pdfStorageBytes = dto.pdfStorageBytes,
                                pdfFileName = decrypted.pdfFileName,
                                pdfThumbnailBase64 = local?.pdfThumbnailBase64,
                                addToAppleWalletURL = dto.addToAppleWalletURL,
                                barcodeText = decrypted.barcodeText,
                                barcodeFormat = dto.barcodeFormat,
                                createdBy = dto.createdBy ?: local?.createdBy ?: uidOrLocal(),
                                createdByName = dto.createdByName ?: local?.createdByName.orEmpty(),
                                updatedBy = dto.updatedBy ?: local?.updatedBy.orEmpty(),
                                updatedByName = dto.updatedByName ?: local?.updatedByName.orEmpty(),
                                createdAtEpochMillis = dto.createdAtEpochMillis ?: local?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                                isDeleted = false,
                                visibilityScope = KBVisibilityScope.normalizedWallet(dto.visibilityScope),
                                visibilityMemberIdsJson = encodeStringList(dto.visibilityMemberIds),
                                syncStateRaw = 0,
                                reminderOffsetHours = dto.reminderOffsetHours,
                            ),
                        )
                    }
                }
            }
            walletReminderScheduler.rescheduleForFamily(familyId)
        }
    }

    private fun decryptDto(dto: WalletTicketRemoteDto, familyId: String): DecryptedWallet? {
        val title = when {
            !dto.titleEnc.isNullOrBlank() -> decryptField(dto.titleEnc, familyId)
                ?: dto.titlePlain?.trim()?.takeIf { it.isNotEmpty() }
            else -> dto.titlePlain?.trim()?.takeIf { it.isNotEmpty() }
        } ?: run {
            KBLog.data.warning("ticket ${dto.id} missing/undecryptable title", TAG)
            return null
        }
        return DecryptedWallet(
            title = title,
            location = decryptField(dto.locationEnc, familyId)
                ?: dto.locationPlain?.trim()?.takeIf { it.isNotEmpty() },
            seat = decryptField(dto.seatEnc, familyId)
                ?: dto.seatPlain?.trim()?.takeIf { it.isNotEmpty() },
            bookingCode = decryptField(dto.bookingCodeEnc, familyId)
                ?: dto.bookingCodePlain?.trim()?.takeIf { it.isNotEmpty() },
            arrivalLocation = decryptField(dto.arrivalLocationEnc, familyId)
                ?: dto.arrivalLocationPlain?.trim()?.takeIf { it.isNotEmpty() },
            holderName = decryptField(dto.holderNameEnc, familyId)
                ?: dto.holderNamePlain?.trim()?.takeIf { it.isNotEmpty() },
            notes = decryptField(dto.notesEnc, familyId)
                ?: dto.notesPlain?.trim()?.takeIf { it.isNotEmpty() },
            barcodeText = decryptField(dto.barcodeTextEnc, familyId)
                ?: dto.barcodeTextPlain?.trim()?.takeIf { it.isNotEmpty() },
            pdfFileName = decryptField(dto.fileNameEnc, familyId)
                ?: dto.fileNamePlain?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private data class DecryptedWallet(
        val title: String,
        val location: String?,
        val seat: String?,
        val bookingCode: String?,
        val arrivalLocation: String?,
        val holderName: String?,
        val notes: String?,
        val barcodeText: String?,
        val pdfFileName: String?,
    )

    private fun decryptField(b64: String?, familyId: String): String? {
        if (b64.isNullOrBlank()) return null
        return runCatching {
            val combined = Base64.decode(b64, Base64.DEFAULT)
            String(documentCrypto.decrypt(combined, familyId), Charsets.UTF_8)
        }.getOrElse { null }
    }

    private fun encryptToB64(plain: String, familyId: String): String {
        val combined = documentCrypto.encrypt(plain.toByteArray(Charsets.UTF_8), familyId)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
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
        walletListener?.remove()
        walletListener = null
        listeningFamilyId = null
    }
}
