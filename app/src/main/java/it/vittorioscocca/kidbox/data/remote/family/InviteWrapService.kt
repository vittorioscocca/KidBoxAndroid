package it.vittorioscocca.kidbox.data.remote.family

import android.util.Base64
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Date
import java.util.UUID
import kotlin.random.Random

class InviteWrapService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    data class Result(
        val inviteId: String,
        val secretBase64url: String,
        val qrPayload: String,
        val expiresAt: Date,
    )

    suspend fun createInvite(familyId: String, ttlSeconds: Long = 24 * 3600): Result {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        require(familyId.isNotBlank()) { "familyId vuoto" }

        val now = Date()
        val expiresAt = Date(now.time + ttlSeconds * 1000L)
        val inviteId = UUID.randomUUID().toString()

        val secret = randomBytes(32)
        val salt = randomBytes(16)
        val secretHash = sha256Base64(secret)

        db.collection("families")
            .document(familyId)
            .collection("invites")
            .document(inviteId)
            .set(
                mapOf(
                    "createdAt" to Timestamp(now),
                    "createdBy" to uid,
                    "expiresAt" to Timestamp(expiresAt),
                    "secretHash" to secretHash,
                    "kdfSalt" to Base64.encodeToString(salt, Base64.NO_WRAP),
                    "usedAt" to null,
                    "usedBy" to null,
                ),
            ).await()

        val secretB64url = base64Url(secret)
        val qrPayload = "kidbox://join?familyId=$familyId&inviteId=$inviteId&secret=$secretB64url"
        return Result(
            inviteId = inviteId,
            secretBase64url = secretB64url,
            qrPayload = qrPayload,
            expiresAt = expiresAt,
        )
    }

    private fun randomBytes(size: Int): ByteArray {
        val out = ByteArray(size)
        Random.nextBytes(out)
        return out
    }

    private fun sha256Base64(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun base64Url(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}
