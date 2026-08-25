package it.vittorioscocca.kidbox.data.remote.auth

import android.content.Context
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
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

    suspend fun signInWithEmail(email: String, password: String): AuthResult =
        firebaseAuth.signInWithEmailAndPassword(email, password).await()

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

    suspend fun sendPasswordReset(email: String) {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }
}
