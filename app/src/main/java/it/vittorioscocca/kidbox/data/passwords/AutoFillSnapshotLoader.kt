package it.vittorioscocca.kidbox.data.passwords

import android.content.Context
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import java.util.concurrent.atomic.AtomicReference

/**
 * Carica e decifra lo snapshot (blob interno family-AES) con piccola cache in-memory
 * invalidata quando cambia [AutoFillSnapshotEncryptedStore.lastModifiedMillis].
 */
object AutoFillSnapshotLoader {
    private val cacheFingerprint = AtomicReference<Long>(0L)
    private val cacheSnapshot = AtomicReference<AutoFillSnapshot?>(null)

    fun clearMemoryCache() {
        cacheFingerprint.set(0L)
        cacheSnapshot.set(null)
    }

    fun load(context: Context, familyId: String, uid: String, store: AutoFillSnapshotEncryptedStore): AutoFillSnapshot? {
        val fp = store.lastModifiedMillis()
        if (fp == 0L) {
            clearMemoryCache()
            return null
        }
        val cached = cacheSnapshot.get()
        if (cached != null && cacheFingerprint.get() == fp) return cached
        val familyKey = FamilyKeyStore.loadFamilyKey(context, familyId, uid) ?: return null
        val encBlob = store.readFamilyEncryptedBlobOrNull() ?: return null
        val plain = runCatching {
            AutoFillSnapshotFamilyCipher.decryptCombined(encBlob, familyKey).toString(Charsets.UTF_8)
        }.getOrNull() ?: return null
        val snap = runCatching { AutoFillSnapshotJson.fromJson(plain) }.getOrNull() ?: return null
        cacheFingerprint.set(fp)
        cacheSnapshot.set(snap)
        return snap
    }
}
