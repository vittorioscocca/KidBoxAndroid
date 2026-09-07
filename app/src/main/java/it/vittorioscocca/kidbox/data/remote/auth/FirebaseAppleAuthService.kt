package it.vittorioscocca.kidbox.data.remote.auth

import it.vittorioscocca.kidbox.util.KBLog

import android.app.Activity
import android.content.ActivityNotFoundException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
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
        // Niente nonce manuale: nel flusso web è l'handler di Firebase a
        // generarlo e validarlo. Quello di ASAuthorization serve al flusso
        // nativo di iOS, qui aggiungerebbe solo una validazione in più da
        // superare.
        val oauthProvider = OAuthProvider.newBuilder("apple.com").apply {
            scopes = listOf("email", "name")
        }.build()

        KBLog.auth.debug("Apple sign-in started", TAG)
        return try {
            // Il login apre una Custom Tab, cioè un'altra activity: mentre
            // l'utente è su Apple il sistema può distruggere la nostra, e su
            // MIUI succede spesso. In quel caso il Task originale è perso ma
            // Firebase tiene il risultato qui: ripartire da capo rimanderebbe
            // l'utente sulla schermata delle credenziali pur essendo già
            // autenticato.
            val pending = firebaseAuth.pendingAuthResult
            if (pending != null) {
                KBLog.auth.debug("Apple sign-in resumed from pending result", TAG)
            }
            // `startActivityForSignInWithProvider` autentica già di suo: la
            // credenziale restituita non va riusata, sarebbe un secondo login
            // con un'autorizzazione Apple monouso.
            val result = (pending ?: providerStartSignIn(activity, oauthProvider)).await()
            val user = result.user ?: throw AuthError.Unknown
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

    private fun providerStartSignIn(
        activity: Activity,
        oauthProvider: OAuthProvider,
    ) = firebaseAuth.startActivityForSignInWithProvider(activity, oauthProvider)

    private companion object {
        private const val TAG = "KidBoxAuthApple"
    }
}
