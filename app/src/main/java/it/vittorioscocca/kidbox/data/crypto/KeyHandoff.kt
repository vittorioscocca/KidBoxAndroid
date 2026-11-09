package it.vittorioscocca.kidbox.data.crypto

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Date
import javax.crypto.KeyAgreement

/**
 * Passaggio della chiave col QR (piano v2, sezione 3.2). Gemello di
 * `KeyHandoff.swift` (iOS) e `keyHandoff.js` (web): stesse costanti.
 *
 * Chi RICEVE crea `keyHandoffs/{roomId}` con la pubblica di una coppia ECDH
 * P-256 effimera e mostra il QR `KBKEY1.{roomId}.{pub}`. Chi MANDA inquadra,
 * usa la pubblica DEL QR e deposita in `payload` la chiave chiusa: AES-GCM con
 * chiave HKDF-SHA256(ECDH, salt = roomId, info = "kidbox-vault-v2:handoff"),
 * AAD "kidbox-vault-v2:handoff:{roomId}:{kind}:{familyId}". `kind` = "rk"
 * (stesso utente) o "familyKey" (da un familiare). Le rules lo impongono.
 */
object KeyHandoff {
    const val PREFIX = "KBKEY1"
    const val INFO = "kidbox-vault-v2:handoff"
    const val TTL_MS = 10 * 60 * 1000L

    class BadQrException : Exception("qr non valido")
    class ExpiredException : Exception("stanza scaduta o usata")
    class MismatchException : Exception("stanza diversa dal qr")
    class NothingToSendException : Exception("niente da mandare")
    class OpenFailedException : Exception("busta non apribile")

    private val db get() = FirebaseFirestore.getInstance()
    private val random = SecureRandom()

    fun aad(roomId: String, kind: String, familyId: String?) = "$INFO:$roomId:$kind:${familyId ?: ""}"

    // ── P-256 con chiavi pubbliche grezze (0x04 || x || y), come WebCrypto e CryptoKit ──

    private fun newPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun fixed32(b: BigInteger): ByteArray {
        val raw = b.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    fun rawPublic(k: ECPublicKey): ByteArray = byteArrayOf(4) + fixed32(k.w.affineX) + fixed32(k.w.affineY)

    private fun publicFromRaw(raw: ByteArray, like: ECPublicKey): ECPublicKey {
        if (raw.size != 65 || raw[0] != 4.toByte()) throw BadQrException()
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, like.params)) as ECPublicKey
    }

    private fun wrapKey(pair: KeyPair, peerRaw: ByteArray, roomId: String): ByteArray {
        val peer = publicFromRaw(peerRaw, pair.public as ECPublicKey)
        val ka = KeyAgreement.getInstance("ECDH").apply { init(pair.private); doPhase(peer, true) }
        val shared = ka.generateSecret()
        return FamilyKeyVaultCrypto.hkdf(shared, roomId.toByteArray(Charsets.UTF_8), INFO).also { shared.fill(0) }
    }

    // ── chi manda ───────────────────────────────────────────────────────

    data class Target(
        val roomId: String,
        val pub: ByteArray,
        val kind: String,          // "rk" | "familyKey"
        val familyId: String?,
        val familyName: String?,
        val receiver: String,      // «Chrome · Windows» o il nome del familiare
    )

    fun parse(text: String): Pair<String, ByteArray>? {
        val parts = text.trim().split(".")
        if (parts.size != 3 || parts[0] != PREFIX || parts[1].length < 20) return null
        val pub = runCatching { FamilyKeyVaultCrypto.unb64Url(parts[2]) }.getOrNull() ?: return null
        return if (pub.size == 65) parts[1] to pub else null
    }

    suspend fun prepare(context: Context, qr: String, uid: String, familyIds: List<String>): Target {
        val (roomId, pub) = parse(qr) ?: throw BadQrException()
        val d = db.collection("keyHandoffs").document(roomId).get(Source.SERVER).await()
        val exp = d.getTimestamp("expiresAt")?.toDate()
        if (!d.exists() || d.contains("payload") || exp == null || exp.before(Date())) throw ExpiredException()
        val roomUid = d.getString("uid") ?: throw MismatchException()
        val docPub = d.getString("pub")?.let { runCatching { FamilyKeyVaultCrypto.unb64Url(it) }.getOrNull() }
        if (docPub == null || !docPub.contentEquals(pub)) throw MismatchException()
        val label = d.getString("label").orEmpty()

        if (roomUid == uid && FamilyKeyVault.loadRk(uid) != null) {
            return Target(roomId, pub, "rk", null, null, label)
        }
        for (fid in familyIds.toSet()) {
            if (fid.isBlank() || FamilyKeyStore.loadFamilyKey(context, fid, uid) == null) continue
            val m = runCatching {
                db.collection("families").document(fid).collection("members").document(roomUid).get(Source.SERVER).await()
            }.getOrNull() ?: continue
            if (!m.exists() || m.get("role") == null || m.getBoolean("isDeleted") == true) continue
            val name = runCatching { db.collection("families").document(fid).get().await().getString("name") }.getOrNull().orEmpty()
            val who = if (roomUid == uid) label else (m.getString("displayName") ?: m.getString("name") ?: label)
            return Target(roomId, pub, "familyKey", fid, name, who)
        }
        throw NothingToSendException()
    }

    suspend fun deliver(context: Context, t: Target, uid: String) {
        val plain = when (t.kind) {
            "rk" -> FamilyKeyVault.loadRk(uid)
            else -> t.familyId?.let { FamilyKeyStore.loadFamilyKey(context, it, uid) }
        } ?: throw NothingToSendException()
        val pair = newPair()
        val key = wrapKey(pair, t.pub, t.roomId)
        val s = FamilyKeyVaultCrypto.seal(plain, key, aad(t.roomId, t.kind, t.familyId))
        plain.fill(0); key.fill(0)
        val payload = mutableMapOf<String, Any>(
            "kind" to t.kind,
            "epk" to FamilyKeyVaultCrypto.b64Url(rawPublic(pair.public as ECPublicKey)),
            "nonce" to s.nonce, "cipher" to s.cipher, "tag" to s.tag,
        )
        t.familyId?.let { payload["familyId"] = it }
        db.collection("keyHandoffs").document(t.roomId).update(
            mapOf("payload" to payload, "senderUid" to uid, "sentAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    // ── chi riceve ──────────────────────────────────────────────────────

    data class Received(val kind: String, val familyId: String?, val bytes: ByteArray)

    class Room internal constructor(val roomId: String, private val pair: KeyPair, val expiresAt: Date) {
        val qrText: String = "$PREFIX.$roomId.${FamilyKeyVaultCrypto.b64Url(rawPublic(pair.public as ECPublicKey))}"
        private var listener: ListenerRegistration? = null
        @Volatile private var done = false

        internal fun listen(onResult: (Result<Received>) -> Unit) {
            listener = FirebaseFirestore.getInstance().collection("keyHandoffs").document(roomId)
                .addSnapshotListener { snap, _ ->
                    @Suppress("UNCHECKED_CAST")
                    val p = snap?.get("payload") as? Map<String, Any?> ?: return@addSnapshotListener
                    if (done) return@addSnapshotListener
                    done = true
                    val r = runCatching { open(p) }
                    close()
                    onResult(r)
                }
        }

        private fun open(p: Map<String, Any?>): Received {
            val kind = p["kind"] as? String ?: throw OpenFailedException()
            val epk = (p["epk"] as? String)?.let { FamilyKeyVaultCrypto.unb64Url(it) } ?: throw OpenFailedException()
            val sealed = FamilyKeyVaultCrypto.Sealed(
                p["cipher"] as? String ?: throw OpenFailedException(),
                p["nonce"] as? String ?: throw OpenFailedException(),
                p["tag"] as? String ?: throw OpenFailedException(),
            )
            val familyId = p["familyId"] as? String
            val key = wrapKey(pair, epk, roomId)
            val bytes = runCatching { FamilyKeyVaultCrypto.open(sealed, key, aad(roomId, kind, familyId)) }
                .getOrNull()?.takeIf { it.size == 32 } ?: throw OpenFailedException()
            key.fill(0)
            return Received(kind, familyId, bytes)
        }

        fun close() {
            listener?.remove()
            listener = null
            FirebaseFirestore.getInstance().collection("keyHandoffs").document(roomId).delete()
        }
    }

    suspend fun openRoom(uid: String, label: String, onResult: (Result<Received>) -> Unit): Room {
        val roomId = FamilyKeyVaultCrypto.b64Url(ByteArray(16).also { random.nextBytes(it) })
        val room = Room(roomId, newPair(), Date(System.currentTimeMillis() + TTL_MS))
        db.collection("keyHandoffs").document(roomId).set(
            mapOf(
                "uid" to uid,
                "pub" to room.qrText.split(".")[2],
                "label" to label.take(80),
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(room.expiresAt),
            ),
        ).await()
        room.listen(onResult)
        return room
    }

    /** RK (se è quella della cassaforte) o chiave di una famiglia, salvata come all'ingresso con invito. */
    suspend fun adopt(context: Context, r: Received, uid: String) {
        when (r.kind) {
            "rk" -> FamilyKeyVault.adoptHandedRk(uid, r.bytes)
            "familyKey" -> {
                val fid = r.familyId ?: throw OpenFailedException()
                FamilyKeyStore.saveFamilyKey(context, r.bytes, fid, uid)
                FamilyKeyEscrow.backupRawKey(r.bytes, fid, uid)
            }
            else -> throw OpenFailedException()
        }
    }
}
