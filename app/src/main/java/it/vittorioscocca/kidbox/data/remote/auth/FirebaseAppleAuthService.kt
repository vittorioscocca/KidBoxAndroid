package it.vittorioscocca.kidbox.data.remote.auth

import it.vittorioscocca.kidbox.util.KBLog

import android.app.Activity
import android.content.ActivityNotFoundException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAppleAuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : AuthService {

    override val provider: AuthProvider = AuthProvider.APPLE

    override suspend fun signIn(presentation: AuthPresentation): FirebaseUser {
        val activity = when (presentation) {
            is AuthPresentation.ActivityContext -> presentation.activity
        }
        return signIn(activity)
    }

    suspend fun signIn(activity: Activity): FirebaseUser {
        val rawNonce = generateRawNonce()
        val nonceHash = sha256(rawNonce)

        val oauthProvider = OAuthProvider.newBuilder("apple.com").apply {
            scopes = listOf("email", "name")
            addCustomParameter("nonce", nonceHash)
        }.build()

        KBLog.auth.debug("Apple sign-in started", TAG)
        return try {
            val result = providerStartSignIn(activity, oauthProvider)
            val credential = result.credential ?: throw AuthError.MissingToken
            val user = firebaseAuth.signInWithCredential(credential).await().user
                ?: throw AuthError.Unknown
            KBLog.auth.info("Apple sign-in completed uid=${user.uid}", TAG)
            user
        } catch (e: CancellationException) {
            throw e
        } catch (e: ActivityNotFoundException) {
            KBLog.auth.warning("Apple sign-in activity unavailable: ${e.message}", TAG)
            throw e
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("cancel", ignoreCase = true) ||
                msg.contains("cancell", ignoreCase = true) ||
                msg.contains("dismiss", ignoreCase = true) ||
                msg.contains("closed", ignoreCase = true)
            ) {
                KBLog.auth.debug("Apple sign-in cancelled by user", TAG)
                throw AuthError.Cancelled
            }
            KBLog.auth.error("Apple sign-in failed: ${e.message}", TAG, e)
            throw e
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    private suspend fun providerStartSignIn(
        activity: Activity,
        oauthProvider: OAuthProvider,
    ) = firebaseAuth.startActivityForSignInWithProvider(activity, oauthProvider).await()

    private fun generateRawNonce(bytes: Int = 32): String {
        val random = SecureRandom()
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return buffer.joinToString("") { b -> "%02x".format(b) }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }

    private companion object {
        private const val TAG = "KidBoxAuthApple"
    }
}
