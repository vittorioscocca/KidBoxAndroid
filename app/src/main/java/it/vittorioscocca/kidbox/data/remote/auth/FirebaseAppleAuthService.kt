package it.vittorioscocca.kidbox.data.remote.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.util.Log
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

        Log.d(TAG, "Apple sign-in started")
        return try {
            val result = providerStartSignIn(activity, oauthProvider)
            val credential = result.credential ?: throw AuthError.MissingToken
            val user = firebaseAuth.signInWithCredential(credential).await().user
                ?: throw AuthError.Unknown
            Log.i(TAG, "Apple sign-in completed uid=${user.uid}")
            user
        } catch (e: CancellationException) {
            throw e
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Apple sign-in activity unavailable: ${e.message}")
            throw e
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("cancel", ignoreCase = true) ||
                msg.contains("cancell", ignoreCase = true) ||
                msg.contains("dismiss", ignoreCase = true) ||
                msg.contains("closed", ignoreCase = true)
            ) {
                Log.d(TAG, "Apple sign-in cancelled by user")
                throw AuthError.Cancelled
            }
            Log.e(TAG, "Apple sign-in failed: ${e.message}", e)
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
