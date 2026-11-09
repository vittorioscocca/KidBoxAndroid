package it.vittorioscocca.kidbox.data.crypto

import android.content.SharedPreferences
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import it.vittorioscocca.kidbox.util.KBLog
import it.vittorioscocca.kidbox.util.KidBoxApplicationHolder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

private const val TAG = "FamilyKeyVault"
private const val PREFS_FILE = "kidbox_vault"
private fun rkPref(uid: String) = "rk_$uid"

/**
 * Cassaforte v2 lato Android: la chiave personale (RK) in EncryptedPrefs,
 * l'interruttore `config/keyVault`, la copia `v2` nell'escrow.
 *
 * Fase 1: lettura e scrittura. La RK nasce SOLO con la cassaforte (codice
 * creato o aperto): una RK nata in silenzio qui mentre un altro dispositivo ne
 * crea un'altra darebbe due chiavi personali per lo stesso utente. Le schermate
 * sono la Fase 2. Crittografia in [FamilyKeyVaultCrypto].
 */
object FamilyKeyVault {

    private val db get() = FirebaseFirestore.getInstance()

    @Volatile private var prefs: SharedPreferences? = null

    private fun prefs(): SharedPreferences? {
        prefs?.let { return it }
        val ctx = KidBoxApplicationHolder.applicationContext ?: return null
        return synchronized(this) {
            prefs ?: EncryptedPrefs.createOrNull(ctx, PREFS_FILE)?.also { prefs = it }
        }
    }

    // ── RK ──────────────────────────────────────────────────────────────

    fun loadRk(uid: String): ByteArray? {
        if (uid.isBlank()) return null
        val s = runCatching { prefs()?.getString(rkPref(uid), null) }.getOrNull() ?: return null
        return runCatching { FamilyKeyVaultCrypto.unb64(s) }.getOrNull()?.takeIf { it.size == 32 }
    }

    /**
     * EncryptedPrefs (mai le prefs in chiaro) e, se cifrata end-to-end, anche
     * Block Store: così la RK torna da sola dopo una reinstallazione o su un
     * telefono nuovo con lo stesso account Google.
     */
    private suspend fun saveRk(uid: String, rk: ByteArray) {
        val p = prefs() ?: error("EncryptedPrefs non disponibili")
        p.edit().putString(rkPref(uid), FamilyKeyVaultCrypto.b64(rk)).commit()
        saveToBlockStore(uid, rk)
    }

    // ── Block Store (decisione del 09/11/2026: solo con E2E confermata) ──

    private fun blockStoreKey(uid: String) = "kidbox_vault_rk_$uid"

    private suspend fun saveToBlockStore(uid: String, rk: ByteArray) {
        val ctx = KidBoxApplicationHolder.applicationContext ?: return
        try {
            val client = Blockstore.getClient(ctx)
            // Senza blocco schermo Google potrebbe aprire la RK: non si salva, resta il codice.
            if (!client.isEndToEndEncryptionAvailable.await()) {
                KBLog.crypto.info("Block Store senza E2E: RK non salvata lì", TAG)
                return
            }
            client.storeBytes(
                StoreBytesData.Builder()
                    .setKey(blockStoreKey(uid))
                    .setBytes(rk)
                    .setShouldBackupToCloud(true)
                    .build()
            ).await()
            KBLog.crypto.info("RK salvata su Block Store (E2E)", TAG)
        } catch (e: Exception) {
            KBLog.crypto.info("Block Store non disponibile: ${e.message}", TAG)
        }
    }

    /**
     * Rimette in locale la RK da Block Store, se c'è e se il suo `rkId` coincide
     * con quello della cassaforte dell'utente (una RK vecchia o di un altro
     * account non si adotta). `true` = RK ora presente.
     */
    suspend fun restoreFromBlockStore(uid: String): Boolean {
        if (loadRk(uid) != null) return true
        val ctx = KidBoxApplicationHolder.applicationContext ?: return false
        return try {
            val key = blockStoreKey(uid)
            val res = Blockstore.getClient(ctx).retrieveBytes(
                RetrieveBytesRequest.Builder().setKeys(listOf(key)).build()
            ).await()
            val rk = res.blockstoreDataMap[key]?.bytes?.takeIf { it.size == 32 } ?: return false
            val ids = db.collection("users").document(uid).collection("keyVault")
                .get(Source.SERVER).await().documents.mapNotNull { it.getString("rkId") }.toSet()
            if (FamilyKeyVaultCrypto.rkId(rk) !in ids) {
                KBLog.crypto.info("Block Store: RK non della cassaforte attuale, ignorata", TAG)
                return false
            }
            val p = prefs() ?: return false
            p.edit().putString(rkPref(uid), FamilyKeyVaultCrypto.b64(rk)).commit()
            KBLog.crypto.info("RK ripresa da Block Store", TAG)
            true
        } catch (e: Exception) {
            KBLog.crypto.info("Block Store: lettura non riuscita: ${e.message}", TAG)
            false
        }
    }

    /** Quanti slot ha la cassaforte (0 = da creare). */
    suspend fun slotCount(uid: String): Int =
        db.collection("users").document(uid).collection("keyVault").get(Source.SERVER).await().size()

    /** Copia v2 verificata per ogni famiglia di cui questo telefono ha la chiave. */
    suspend fun syncFamilyKeysV2(context: android.content.Context, uid: String, familyIds: List<String>) {
        for (fid in familyIds.toSet()) {
            if (fid.isBlank()) continue
            val key = FamilyKeyStore.loadFamilyKey(context, fid, uid) ?: continue
            writeAndVerifyV2(uid, fid, key)
        }
    }

    fun forgetRk(uid: String) {
        runCatching { prefs()?.edit()?.remove(rkPref(uid))?.apply() }
    }

    // ── interruttore ────────────────────────────────────────────────────

    data class Flags(val active: Boolean, val enforce: Boolean)

    @Volatile private var flagCache: Triple<String, Long, Flags>? = null

    /** `enabled` = tutti; `internal` = solo `config/internalUsers.uids`. Spento o illeggibile = come prima. */
    suspend fun flags(uid: String): Flags {
        flagCache?.let { (u, at, v) -> if (u == uid && System.currentTimeMillis() - at < 300_000) return v }
        val value = try {
            coroutineScope {
                val cfg = async { db.collection("config").document("keyVault").get().await() }
                val internal = async { db.collection("config").document("internalUsers").get().await() }
                val c = cfg.await().data ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val uids = (internal.await().get("uids") as? List<String>) ?: emptyList()
                val active = c["enabled"] == true || (c["internal"] == true && uid in uids)
                Flags(active, active && c["enforce"] == true)
            }
        } catch (e: Exception) {
            KBLog.crypto.info("vault flags non leggibili: ${e.message}", TAG)
            Flags(false, false)
        }
        flagCache = Triple(uid, System.currentTimeMillis(), value)
        return value
    }

    // ── copia v2 ────────────────────────────────────────────────────────

    private fun sealedFrom(map: Map<*, *>?): Pair<FamilyKeyVaultCrypto.Sealed, String>? {
        val c = map?.get("cipher") as? String ?: return null
        val n = map["nonce"] as? String ?: return null
        val t = map["tag"] as? String ?: return null
        val id = map["rkId"] as? String ?: return null
        return FamilyKeyVaultCrypto.Sealed(c, n, t) to id
    }

    /** La chiave di famiglia dalla mappa `v2`, se questo dispositivo ha la RK giusta. */
    fun openV2(uid: String, familyId: String, v2: Map<*, *>?): ByteArray? {
        val (sealed, id) = sealedFrom(v2) ?: return null
        val rk = loadRk(uid) ?: return null
        if (FamilyKeyVaultCrypto.rkId(rk) != id) return null
        return FamilyKeyVaultCrypto.openFamilyKeyV2(uid, familyId, sealed, rk)
    }

    /** Il blocco `v2` da mettere nel documento, o null se cassaforte spenta o RK assente. */
    suspend fun v2Field(uid: String, familyId: String, familyKey: ByteArray): Map<String, Any>? {
        if (!flags(uid).active) return null
        val rk = loadRk(uid) ?: return null
        val s = runCatching { FamilyKeyVaultCrypto.sealFamilyKeyV2(uid, familyId, familyKey, rk) }.getOrNull()
            ?: return null
        return mapOf(
            "cipher" to s.cipher, "nonce" to s.nonce, "tag" to s.tag,
            "rkId" to FamilyKeyVaultCrypto.rkId(rk),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    }

    /**
     * Scrive la copia v2 in merge (la v1 non si tocca) e la VERIFICA rileggendola
     * dal server (piano, 4 bis regola 4). Non cancella niente.
     */
    suspend fun writeAndVerifyV2(uid: String, familyId: String, familyKey: ByteArray): Boolean {
        val field = v2Field(uid, familyId, familyKey) ?: return false
        val ref = db.collection("families").document(familyId)
            .collection("memberKeyBackups").document(uid)
        return try {
            ref.set(mapOf("v2" to field), com.google.firebase.firestore.SetOptions.merge()).await()
            val fresh = ref.get(Source.SERVER).await()
            val ok = openV2(uid, familyId, fresh.get("v2") as? Map<*, *>)?.contentEquals(familyKey) == true
            if (!ok) KBLog.crypto.error("copia v2 non verificata familyId=$familyId", TAG)
            ok
        } catch (e: Exception) {
            KBLog.crypto.error("copia v2 non scritta familyId=$familyId: ${e.message}", TAG)
            false
        }
    }

    // ── creare e aprire la cassaforte (schermate: Fase 2) ───────────────

    class VaultExistsException : Exception("vault exists")
    class VaultMissingException : Exception("vault missing")

    /** Crea la cassaforte col codice già mostrato e confermato. */
    suspend fun createVault(uid: String, recoveryCode: String) {
        val rk = FamilyKeyVaultCrypto.newRk()
        val (salt, s) = FamilyKeyVaultCrypto.sealRecoverySlot(uid, recoveryCode, rk)
        val col = db.collection("users").document(uid).collection("keyVault")
        if (!col.get(Source.SERVER).await().isEmpty) throw VaultExistsException()
        val ref = col.document("recoveryCode")
        val rec = mapOf(
            "type" to "recoveryCode", "version" to FamilyKeyVaultCrypto.VERSION,
            "rkId" to FamilyKeyVaultCrypto.rkId(rk), "salt" to salt,
            "nonce" to s.nonce, "cipher" to s.cipher, "tag" to s.tag,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        db.runTransaction { txn ->
            if (txn.get(ref).exists()) throw VaultExistsException()
            txn.set(ref, rec)
        }.await()
        saveRk(uid, rk)
    }

    /** Apre la cassaforte col codice di recupero e salva la RK. */
    suspend fun openWithRecoveryCode(uid: String, recoveryCode: String) {
        val d = db.collection("users").document(uid).collection("keyVault")
            .document("recoveryCode").get(Source.SERVER).await().data ?: throw VaultMissingException()
        val salt = d["salt"] as? String ?: throw VaultMissingException()
        val sealed = FamilyKeyVaultCrypto.Sealed(
            d["cipher"] as? String ?: throw VaultMissingException(),
            d["nonce"] as? String ?: throw VaultMissingException(),
            d["tag"] as? String ?: throw VaultMissingException(),
        )
        val rk = FamilyKeyVaultCrypto.openRecoverySlot(uid, recoveryCode, salt, sealed, d["rkId"] as? String)
        saveRk(uid, rk)
    }
}
