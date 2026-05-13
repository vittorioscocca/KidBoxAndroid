package it.vittorioscocca.kidbox.data.passwords.security

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.feature.passwords.PasswordStrength
import it.vittorioscocca.kidbox.feature.passwords.PasswordStrengthLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class ScanResult(
    val compromised: List<PasswordEntryEntity>,
    val duplicates: List<List<PasswordEntryEntity>>,
    val weak: List<PasswordEntryEntity>,
    val unknown: Int,
)

@Singleton
class SecurityAuditService @Inject constructor(
    private val passwordsRepository: PasswordsRepository,
    private val pwnedChecker: PwnedChecker,
    private val cypher: PasswordCypher,
    private val auth: FirebaseAuth,
    private val familySessionPreferences: FamilySessionPreferences,
    private val duplicateDetector: DuplicateDetector,
) {
    suspend fun runFullScan(): ScanResult {
        val familyId = familySessionPreferences.getActiveFamilyId().orEmpty()
        if (familyId.isBlank()) return ScanResult(emptyList(), emptyList(), emptyList(), 0)
        val visible = passwordsRepository.listVisibleEntriesNow(familyId)
        val semaphore = Semaphore(3)
        val checks = coroutineScope {
            visible.map { entry ->
                async {
                    semaphore.withPermit {
                        val clear = decryptPassword(entry) ?: return@withPermit EntryCheck(entry, PwnedChecker.Result.Unknown, false)
                        val verdict = pwnedChecker.check(clear)
                        val strength = PasswordStrength.evaluate(clear).level
                        val isWeak = strength == PasswordStrengthLevel.VERY_WEAK || strength == PasswordStrengthLevel.WEAK
                        EntryCheck(entry, verdict, isWeak)
                    }
                }
            }.awaitAll()
        }

        val compromised = mutableListOf<PasswordEntryEntity>()
        val weak = mutableListOf<PasswordEntryEntity>()
        var unknownCount = 0
        checks.forEach { checked ->
            when (val verdict = checked.verdict) {
                is PwnedChecker.Result.Pwned -> {
                    compromised.add(checked.entry)
                    passwordsRepository.updatePwnedVerdict(checked.entry.id, verdict.count, System.currentTimeMillis())
                }
                PwnedChecker.Result.Safe -> {
                    passwordsRepository.updatePwnedVerdict(checked.entry.id, 0, System.currentTimeMillis())
                }
                PwnedChecker.Result.Unknown -> {
                    unknownCount += 1
                }
            }
            if (checked.isWeak) weak.add(checked.entry)
        }

        val duplicates = duplicateDetector.allDuplicateClusters(familyId)
        return ScanResult(
            compromised = compromised.distinctBy { it.id },
            duplicates = duplicates,
            weak = weak.distinctBy { it.id },
            unknown = unknownCount,
        )
    }

    suspend fun checkSingle(entryId: String): PwnedChecker.Result {
        val familyId = familySessionPreferences.getActiveFamilyId().orEmpty()
        if (familyId.isBlank()) return PwnedChecker.Result.Unknown
        val entry = passwordsRepository.listVisibleEntriesNow(familyId).firstOrNull { it.id == entryId }
            ?: return PwnedChecker.Result.Unknown
        val clear = decryptPassword(entry) ?: return PwnedChecker.Result.Unknown
        val result = pwnedChecker.check(clear)
        when (result) {
            is PwnedChecker.Result.Pwned -> passwordsRepository.updatePwnedVerdict(entry.id, result.count, System.currentTimeMillis())
            PwnedChecker.Result.Safe -> passwordsRepository.updatePwnedVerdict(entry.id, 0, System.currentTimeMillis())
            PwnedChecker.Result.Unknown -> Unit
        }
        return result
    }

    private fun decryptPassword(entry: PasswordEntryEntity): String? {
        val uid = auth.currentUser?.uid.orEmpty()
        return runCatching {
            cypher.decrypt(entry.passwordCipher, entry.familyId, entry.visibility, entry.createdBy, uid)
        }.getOrNull()
    }
}

private data class EntryCheck(
    val entry: PasswordEntryEntity,
    val verdict: PwnedChecker.Result,
    val isWeak: Boolean,
)
