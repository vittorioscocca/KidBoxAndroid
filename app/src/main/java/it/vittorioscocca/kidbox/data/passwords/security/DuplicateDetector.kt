package it.vittorioscocca.kidbox.data.passwords.security

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

typealias PasswordEntry = PasswordEntryEntity

@Singleton
class DuplicateDetector @Inject constructor(
    private val passwordsRepository: PasswordsRepository,
    private val cypher: PasswordCypher,
    private val auth: FirebaseAuth,
    private val familySessionPreferences: FamilySessionPreferences,
) {
    suspend fun duplicates(of: PasswordEntry): List<PasswordEntry> {
        val all = passwordsRepository.listVisibleEntriesNow(of.familyId)
        val target = decryptPassword(of) ?: return emptyList()
        val targetHash = sha256Hex(target)
        return all.filter { candidate ->
            if (candidate.id == of.id) return@filter false
            val decrypted = decryptPassword(candidate) ?: return@filter false
            sha256Hex(decrypted) == targetHash
        }
    }

    suspend fun allDuplicateClusters(familyId: String): List<List<PasswordEntry>> {
        val visible = passwordsRepository.listVisibleEntriesNow(familyId)
        val byHash = linkedMapOf<String, MutableList<PasswordEntry>>()
        visible.forEach { entry ->
            val pwd = decryptPassword(entry) ?: return@forEach
            val hash = sha256Hex(pwd)
            byHash.getOrPut(hash) { mutableListOf() }.add(entry)
        }
        return byHash.values.filter { it.size >= 2 }.map { it.toList() }
    }

    suspend fun allDuplicateClusters(): List<List<PasswordEntry>> {
        val familyId = familySessionPreferences.getActiveFamilyId().orEmpty()
        if (familyId.isBlank()) return emptyList()
        return allDuplicateClusters(familyId)
    }

    private fun decryptPassword(entry: PasswordEntry): String? {
        val currentUid = auth.currentUser?.uid.orEmpty()
        return runCatching {
            cypher.decrypt(entry.passwordCipher, entry.familyId, entry.visibility, entry.createdBy, currentUid)
        }.getOrNull()
    }

    private fun sha256Hex(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(bytes.size * 2)
        bytes.forEach { out.append(String.format("%02x", it)) }
        return out.toString()
    }
}
