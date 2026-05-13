package it.vittorioscocca.kidbox.feature.passwords.io

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.crypto.tink.subtle.AesGcmJce
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordsTxtExporter(
    private val context: Context,
    private val passwordCypher: PasswordCypher,
) {
    suspend fun export(
        entries: List<PasswordEntryEntity>,
        groups: List<PasswordGroupEntity>,
        familyName: String?,
        passphrase: String? = null,
        currentUid: String,
    ): Uri {
        val visibleGroups = groups.filter { isVisibleGroup(it, currentUid) }
        val groupNames = visibleGroups.associate { it.id to decryptGroupName(it, currentUid) }
        val content = buildPlainExport(entries, groupNames, currentUid, familyName)
        val finalText = if (passphrase.isNullOrBlank()) {
            content
        } else {
            val payload = encryptWithPassphrase(content.toByteArray(StandardCharsets.UTF_8), passphrase)
            "# KidBox Password Export v1 (encrypted)\n${android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)}"
        }

        val dir = File(context.cacheDir, "kbpw").apply { mkdirs() }
        val name = "KidBox-Passwords-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())}.txt"
        val out = File(dir, name)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        out.outputStream().use {
            it.write(bom)
            it.write(finalText.toByteArray(StandardCharsets.UTF_8))
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
    }

    private fun buildPlainExport(
        entries: List<PasswordEntryEntity>,
        groupNames: Map<String, String>,
        currentUid: String,
        familyName: String?,
    ): String {
        val lines = mutableListOf("# KidBox Password Export v1")
        if (!familyName.isNullOrBlank()) lines += "Family: ${escape(familyName)}"
        entries.forEach { entry ->
            if (!isVisibleEntry(entry, currentUid)) return@forEach
            if (KBVisibilityScope.normalizedPassword(entry.visibility) == KBVisibilityScope.ONLY_CREATOR && entry.createdBy != currentUid) return@forEach

            val title = runCatching {
                passwordCypher.decrypt(entry.titleCipher, entry.familyId, entry.visibility, entry.createdBy, currentUid)
            }.getOrNull() ?: return@forEach
            val password = runCatching {
                passwordCypher.decrypt(entry.passwordCipher, entry.familyId, entry.visibility, entry.createdBy, currentUid)
            }.getOrNull() ?: return@forEach
            val username = entry.usernameCipher?.let { runCatching { passwordCypher.decrypt(it, entry.familyId, entry.visibility, entry.createdBy, currentUid) }.getOrNull() } ?: ""
            val website = entry.websiteCipher?.let { runCatching { passwordCypher.decrypt(it, entry.familyId, entry.visibility, entry.createdBy, currentUid) }.getOrNull() } ?: ""
            val note = entry.notesCipher?.let { runCatching { passwordCypher.decrypt(it, entry.familyId, entry.visibility, entry.createdBy, currentUid) }.getOrNull() } ?: ""
            val groupName = groupNames[entry.groupId.orEmpty()].orEmpty()

            lines += "---"
            lines += "Title: ${escape(title)}"
            lines += "Username: ${escape(username)}"
            lines += "Password: ${escape(password)}"
            lines += "WebSite: ${escape(website)}"
            lines += "Group: ${escape(groupName)}"
            lines += "Visibility: ${KBVisibilityScope.normalizedPassword(entry.visibility)}"
            lines += "Note: ${escape(note)}"
            lines += "CreatedBy: ${escape(entry.createdBy)}"
            lines += "Favorite: ${entry.isFavorite}"
            lines += "---"
        }
        return lines.joinToString("\n")
    }

    private fun decryptGroupName(group: PasswordGroupEntity, uid: String): String {
        return runCatching {
            passwordCypher.decrypt(group.nameCipher, group.familyId, group.visibility, group.createdBy, uid)
        }.getOrDefault("")
    }

    private fun encryptWithPassphrase(plain: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = AesGcmJce(key)
        val ciphertext = cipher.encrypt(plain, ByteArray(0))
        return salt + ciphertext
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 100_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\n", "\\n")

    private fun isVisibleEntry(entry: PasswordEntryEntity, uid: String): Boolean {
        val scope = KBVisibilityScope.normalizedPassword(entry.visibility)
        return when (scope) {
            KBVisibilityScope.FAMILY -> true
            KBVisibilityScope.MEMBERS -> entry.visibilityMemberIdsJson.contains("\"$uid\"")
            KBVisibilityScope.ONLY_CREATOR -> entry.createdBy == uid
            else -> true
        }
    }

    private fun isVisibleGroup(group: PasswordGroupEntity, uid: String): Boolean {
        val scope = KBVisibilityScope.normalizedPassword(group.visibility)
        return scope == KBVisibilityScope.FAMILY || (scope == KBVisibilityScope.ONLY_CREATOR && group.createdBy == uid)
    }
}
