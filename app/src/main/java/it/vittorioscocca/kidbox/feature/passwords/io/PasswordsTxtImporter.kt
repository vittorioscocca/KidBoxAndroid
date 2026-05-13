package it.vittorioscocca.kidbox.feature.passwords.io

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.crypto.tink.subtle.AesGcmJce
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.dao.PasswordEntryDao
import it.vittorioscocca.kidbox.data.local.dao.PasswordGroupDao
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.flow.first
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordsTxtImporter(
    private val context: Context,
    private val passwordCypher: PasswordCypher,
    private val entryDao: PasswordEntryDao,
    private val groupDao: PasswordGroupDao,
    private val repository: PasswordsRepository,
) {
    suspend fun parse(uri: Uri, passphrase: String?, currentUid: String, familyId: String): ImportPreview {
        val text = readText(uri)
        if (text.isBlank()) throw IllegalArgumentException("File vuoto")
        val decoded = decryptIfNeeded(text, passphrase)
        val parsed = PasswordsTxtParser.parseText(decoded, currentUid)
        val existing = entryDao.observeVisibleByFamily(familyId, currentUid) // Flow not directly useful
        val existingList = entryDao.observeVisibleByFamily(familyId, currentUid).first()
        val existingPairs = existingList.map { decryptPair(it, currentUid) }.toSet()
        val conflicts = parsed.records.mapNotNull { rec ->
            val key = normalizedPair(rec.title, rec.username)
            if (existingPairs.contains(key)) Conflict(rec.title, rec.username) else null
        }

        val visibleGroups = groupDao.observeVisibleByFamily(familyId, currentUid).first()
        val groupNames = visibleGroups.mapNotNull { runCatching { passwordCypher.decrypt(it.nameCipher, familyId, it.visibility, it.createdBy, currentUid) }.getOrNull() }
            .map { it.trim().lowercase() }
            .toSet()
        val newGroups = parsed.records.map { it.group }.filter { it.isNotBlank() }.distinct()
            .filter { !groupNames.contains(it.trim().lowercase()) }

        return ImportPreview(
            total = parsed.records.size,
            conflicts = conflicts,
            newGroups = newGroups,
            errors = parsed.errors,
            skippedOtherPrivate = parsed.skippedOtherPrivate,
            legacyAmbiguousRecordIndices = parsed.legacyAmbiguousRecordIndices,
            records = parsed.records,
        )
    }

    suspend fun commit(preview: ImportPreview, strategy: MergeStrategy, currentUid: String, familyId: String) {
        val groups = groupDao.observeVisibleByFamily(familyId, currentUid).first().toMutableList()
        val groupsByName = groups.associateBy {
            runCatching { passwordCypher.decrypt(it.nameCipher, familyId, it.visibility, it.createdBy, currentUid) }
                .getOrDefault("").trim().lowercase()
        }.toMutableMap()

        preview.newGroups.forEach { name ->
            val key = name.trim().lowercase()
            if (key in groupsByName) return@forEach
            val now = System.currentTimeMillis()
            val group = PasswordGroupEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                nameCipher = passwordCypher.encrypt(name, familyId, KBVisibilityScope.FAMILY, currentUid, currentUid),
                icon = "folder",
                color = "#7C6FDE",
                visibility = KBVisibilityScope.FAMILY,
                createdBy = currentUid,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
                syncStateRaw = 1,
                lastSyncError = null,
            )
            groupDao.upsert(group)
            repository.pushUpsertGroup(group)
            groupsByName[key] = group
        }

        val existing = entryDao.observeVisibleByFamily(familyId, currentUid).first().toMutableList()
        val existingByPair = existing.associateBy { decryptPair(it, currentUid) }.toMutableMap()

        preview.records.forEach { rec ->
            val pair = normalizedPair(rec.title, rec.username)
            val existingEntity = existingByPair[pair]
            if (existingEntity != null && strategy == MergeStrategy.SKIP_DUPLICATES) return@forEach
            val groupId = groupsByName[rec.group.trim().lowercase()]?.id
            val vis = KBVisibilityScope.normalizedPassword(rec.visibility)
            if (existingEntity != null && strategy == MergeStrategy.OVERWRITE_BY_TITLE_USERNAME) {
                val updated = buildEntry(existingEntity.id, familyId, currentUid, vis, groupId, rec)
                entryDao.upsert(updated)
                repository.pushUpsertEntry(updated)
            } else {
                val newId = if (existingEntity != null) UUID.randomUUID().toString() else (existingEntity?.id ?: UUID.randomUUID().toString())
                val created = buildEntry(newId, familyId, currentUid, vis, groupId, rec)
                entryDao.upsert(created)
                repository.pushUpsertEntry(created)
            }
        }
    }

    private fun buildEntry(
        id: String,
        familyId: String,
        currentUid: String,
        visibility: String,
        groupId: String?,
        rec: ParsedPasswordRecord,
    ): PasswordEntryEntity {
        val now = System.currentTimeMillis()
        return PasswordEntryEntity(
            id = id,
            familyId = familyId,
            createdBy = currentUid,
            visibility = visibility,
            visibilityMemberIdsJson = "[]",
            groupId = groupId,
            titleCipher = passwordCypher.encrypt(rec.title, familyId, visibility, currentUid, currentUid),
            usernameCipher = rec.username.takeIf { it.isNotBlank() }?.let { passwordCypher.encrypt(it, familyId, visibility, currentUid, currentUid) },
            passwordCipher = passwordCypher.encrypt(rec.password, familyId, visibility, currentUid, currentUid),
            websiteCipher = rec.website.takeIf { it.isNotBlank() }?.let { passwordCypher.encrypt(it, familyId, visibility, currentUid, currentUid) },
            notesCipher = rec.note.takeIf { it.isNotBlank() }?.let { passwordCypher.encrypt(it, familyId, visibility, currentUid, currentUid) },
            otpConfigCipher = null,
            iconURL = null,
            lastUsedAtEpochMillis = null,
            passwordUpdatedAtEpochMillis = now,
            expiresAtEpochMillis = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            deletedAtEpochMillis = null,
            syncStateRaw = 1,
            lastSyncError = null,
        )
    }

    private fun decryptPair(entity: PasswordEntryEntity, uid: String): String {
        val title = runCatching { passwordCypher.decrypt(entity.titleCipher, entity.familyId, entity.visibility, entity.createdBy, uid) }.getOrDefault("")
        val username = entity.usernameCipher?.let { runCatching { passwordCypher.decrypt(it, entity.familyId, entity.visibility, entity.createdBy, uid) }.getOrDefault("") } ?: ""
        return normalizedPair(title, username)
    }

    private fun normalizedPair(title: String, username: String): String = "${title.trim().lowercase()}::${username.trim().lowercase()}"

    private fun readText(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("File non leggibile")
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else bytes
        return String(body, StandardCharsets.UTF_8)
    }

    private fun decryptIfNeeded(text: String, passphrase: String?): String {
        val marker = "# KidBox Password Export v1 (encrypted)\n"
        if (!text.startsWith(marker)) return text
        if (passphrase.isNullOrBlank()) throw IllegalArgumentException("Passphrase richiesta")
        val raw = Base64.decode(text.removePrefix(marker).trim(), Base64.NO_WRAP)
        val salt = raw.copyOfRange(0, 16)
        val cipher = raw.copyOfRange(16, raw.size)
        val key = deriveKey(passphrase, salt)
        return String(AesGcmJce(key).decrypt(cipher, ByteArray(0)), StandardCharsets.UTF_8)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 100_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

}
