package it.vittorioscocca.kidbox.data.passwords

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot locale per AutoFill / Credential Provider, allineato a iOS [AutoFillSnapshot.swift]:
 * stesse chiavi JSON, stesso AES-GCM "combined" (12 byte nonce + ciphertext + tag) con la family key 32 byte.
 *
 * Il file su disco può essere ulteriormente protetto da [AutoFillSnapshotEncryptedStore] (EncryptedFile);
 * i byte gestiti da iOS come file singolo corrispondono al payload **interno** (blob family-AES).
 */
data class AutoFillOtpPayload(
    val secret: String,
    val digits: Int = 6,
    val period: Int = 30,
    val algorithm: String = "SHA1",
)

data class AutoFillSnapshotItem(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    /** Host normalizzato (no scheme/path), minuscolo, senza `www.`. */
    val website: String?,
    val visibility: String,
    val owner: String,
    val otp: AutoFillOtpPayload? = null,
)

data class AutoFillSnapshot(
    val version: Int = 1,
    val updatedAt: Instant,
    val items: List<AutoFillSnapshotItem>,
) {
    companion object {
        fun empty(): AutoFillSnapshot = AutoFillSnapshot(version = 1, updatedAt = Instant.now(), items = emptyList())
    }
}

/** Stesso layout di [it.vittorioscocca.kidbox.data.crypto.PasswordCryptoEngine] per AES-GCM combined (CryptoKit). */
internal object AutoFillSnapshotFamilyCipher {
    private const val AES_GCM = "AES/GCM/NoPadding"

    fun encryptCombined(plaintextUtf8: ByteArray, familyKey32: ByteArray): ByteArray {
        require(familyKey32.size == 32) { "family key must be 32 bytes" }
        val key = SecretKeySpec(familyKey32, "AES")
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plaintextUtf8)
        val iv = cipher.iv
        return ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
    }

    fun decryptCombined(combined: ByteArray, familyKey32: ByteArray): ByteArray {
        require(familyKey32.size == 32) { "family key must be 32 bytes" }
        val key = SecretKeySpec(familyKey32, "AES")
        if (combined.size < 12 + 16) throw IllegalArgumentException("Encrypted payload too small")

        val prefixedIvSize = ByteBuffer
            .wrap(combined, 0, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int

        val (iv, enc) = if (
            prefixedIvSize in 8..32 &&
            combined.size > (4 + prefixedIvSize + 16)
        ) {
            val ivStart = 4
            val encStart = ivStart + prefixedIvSize
            combined.copyOfRange(ivStart, encStart) to combined.copyOfRange(encStart, combined.size)
        } else {
            combined.copyOfRange(0, 12) to combined.copyOfRange(12, combined.size)
        }

        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(enc)
    }
}

object AutoFillSnapshotJson {

    /** ISO-8601 compatibile con JSONEncoder `.iso8601` di Swift (istante UTC). */
    private fun encodeInstant(i: Instant): String = i.toString()

    private fun decodeInstant(s: String): Instant = Instant.parse(s)

    fun toJson(snapshot: AutoFillSnapshot): String {
        val items = JSONArray()
        for (it in snapshot.items) {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("title", it.title)
            o.put("username", it.username)
            o.put("password", it.password)
            if (it.website != null) o.put("website", it.website)
            o.put("visibility", it.visibility)
            o.put("owner", it.owner)
            if (it.otp != null) {
                val otp = JSONObject()
                otp.put("secret", it.otp.secret)
                otp.put("digits", it.otp.digits)
                otp.put("period", it.otp.period)
                otp.put("algorithm", it.otp.algorithm)
                o.put("otp", otp)
            }
            items.put(o)
        }
        return JSONObject()
            .put("version", snapshot.version)
            .put("updatedAt", encodeInstant(snapshot.updatedAt))
            .put("items", items)
            .toString()
    }

    fun fromJson(raw: String): AutoFillSnapshot {
        val root = JSONObject(raw)
        val version = root.optInt("version", 1)
        val updatedAt = decodeInstant(root.getString("updatedAt"))
        val arr = root.getJSONArray("items")
        val items = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val otpJson = o.optJSONObject("otp")
                val otp = if (otpJson != null) {
                    AutoFillOtpPayload(
                        secret = otpJson.getString("secret"),
                        digits = otpJson.optInt("digits", 6),
                        period = otpJson.optInt("period", 30),
                        algorithm = otpJson.optString("algorithm", "SHA1"),
                    )
                } else {
                    null
                }
                add(
                    AutoFillSnapshotItem(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        username = o.getString("username"),
                        password = o.getString("password"),
                        website = if (!o.has("website") || o.isNull("website")) null else o.getString("website"),
                        visibility = o.getString("visibility"),
                        owner = o.getString("owner"),
                        otp = otp,
                    ),
                )
            }
        }
        return AutoFillSnapshot(version = version, updatedAt = updatedAt, items = items)
    }
}

object AutoFillWebsiteHost {
    fun normalizedHost(raw: String?): String? {
        var s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (!s.contains("://") && !s.contains("/") && !s.contains("?")) {
            var h = s.lowercase(Locale.ROOT)
            if (h.startsWith("www.")) h = h.drop(4)
            val colon = h.indexOf(':')
            if (colon >= 0) h = h.substring(0, colon)
            return h.ifBlank { null }
        }
        if (!s.contains("://")) s = "https://$s"
        return try {
            val uri = android.net.Uri.parse(s)
            val host = uri.host?.lowercase(Locale.ROOT)?.trim().orEmpty().ifBlank { return null }
            if (host.startsWith("www.")) host.drop(4) else host
        } catch (_: Exception) {
            null
        }
    }
}
