package it.vittorioscocca.kidbox.data.remote.auth

import android.content.Context
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Email / password (equivalente alle chiamate dirette a `Auth.auth()` in `LoginViewModel` iOS).
 */
@Singleton
class EmailAuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val appContext: Context,
) {

    /**
     * Senza email verificata non si entra: si rimanda il link di verifica e si
     * esce subito, come fa `LoginViewModel.signInEmail` su iOS. Il reinvio è
     * best effort — Firebase lo rifiuta con TOO_MANY_REQUESTS se è appena
     * partito, e non deve mascherare il motivo vero del rifiuto.
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val user = result.user
        if (user != null && user.isPasswordUnverified()) {
            runCatching { user.sendEmailVerification().await() }
            firebaseAuth.signOut()
            throw AuthError.EmailNotVerified
        }
        return result
    }

    /**
     * Crea l'utente, invia email di verifica e fa subito sign-out (come su iOS).
     */
    suspend fun registerEmail(email: String, password: String) {
        AppAnalytics.signupStarted(appContext, "email")
        AppAnalytics.signupMethodSelected(appContext, "email")
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        AppAnalytics.signupCompleted(appContext, "email")
        result.user?.sendEmailVerification()?.await()
        firebaseAuth.signOut()
    }

    /**
     * Sessione ripristinata all'avvio con email non verificata: si chiude,
     * come fa `AppCoordinator` su iOS. Il `reload()` serve a non fidarsi della
     * cache: l'utente può aver verificato dall'ultima apertura.
     * Restituisce true se ha buttato fuori l'utente.
     */
    suspend fun signOutIfEmailUnverified(): Boolean {
        val user = firebaseAuth.currentUser ?: return false
        runCatching { user.reload().await() }
        if (!user.isPasswordUnverified()) return false
        firebaseAuth.signOut()
        return true
    }

    suspend fun sendPasswordReset(email: String) {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    private fun FirebaseUser.isPasswordUnverified(): Boolean =
        providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID } && !isEmailVerified
}
