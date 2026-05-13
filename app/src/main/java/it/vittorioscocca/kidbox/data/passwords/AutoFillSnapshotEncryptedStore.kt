package it.vittorioscocca.kidbox.data.passwords

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper [EncryptedFile] con master key Keystore alias [MASTER_KEY_ALIAS].
 * Il contenuto decifrato è il blob AES-GCM con family key (parità iOS).
 */
@Singleton
class AutoFillSnapshotEncryptedStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File = File(context.filesDir, "autofill").apply { mkdirs() }
    private val file = File(dir, "snapshot.kbpw-enc")

    private fun encryptedFile(): EncryptedFile {
        val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    fun readFamilyEncryptedBlobOrNull(): ByteArray? {
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            encryptedFile().openFileInput().use { it.readBytes() }
        }.getOrNull()
    }

    fun writeFamilyEncryptedBlob(blob: ByteArray) {
        // EncryptedFile non consente openFileOutput se il file esiste già ("output file already exists").
        runCatching {
            if (file.exists()) file.delete()
        }
        encryptedFile().openFileOutput().use { out ->
            out.write(blob)
            out.flush()
        }
    }

    fun delete() {
        runCatching { if (file.exists()) file.delete() }
    }

    fun lastModifiedMillis(): Long = if (file.exists()) file.lastModified() else 0L

    companion object {
        const val MASTER_KEY_ALIAS = "kidbox_autofill_master"
    }
}
