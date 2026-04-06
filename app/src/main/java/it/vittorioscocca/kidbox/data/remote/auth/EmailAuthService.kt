package it.vittorioscocca.kidbox.data.remote.auth

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Email / password (equivalente alle chiamate dirette a `Auth.auth()` in `LoginViewModel` iOS).
 */
@Singleton
class EmailAuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) {

    suspend fun signInWithEmail(email: String, password: String): AuthResult =
        firebaseAuth.signInWithEmailAndPassword(email, password).await()

    /**
     * Crea l'utente, invia email di verifica e fa subito sign-out (come su iOS).
     */
    suspend fun registerEmail(email: String, password: String) {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        result.user?.sendEmailVerification()?.await()
        firebaseAuth.signOut()
    }

    suspend fun sendPasswordReset(email: String) {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }
}
