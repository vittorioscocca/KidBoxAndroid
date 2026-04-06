package it.vittorioscocca.kidbox.data.remote.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val credentialManager: CredentialManager,
    private val firebaseAuth: FirebaseAuth,
) : AuthService {

    override val provider: AuthProvider = AuthProvider.GOOGLE

    override suspend fun signIn(presentation: AuthPresentation): FirebaseUser {
        val activity = when (presentation) {
            is AuthPresentation.ActivityContext -> presentation.activity
        }

        val serverClientId = appContext.getString(R.string.default_web_client_id)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = activity,
            )

            val credential = result.credential
            val idToken = when {
                credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                }
                else -> null
            } ?: throw AuthError.MissingToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
            authResult.user ?: throw AuthError.Unknown
        } catch (_: GetCredentialCancellationException) {
            throw AuthError.Cancelled
        } catch (e: GetCredentialException) {
            val msg = e.message.orEmpty()
            if (msg.contains("cancel", ignoreCase = true) || msg.contains("user", ignoreCase = true)) {
                throw AuthError.Cancelled
            }
            throw e
        }
    }
}