package it.vittorioscocca.kidbox.data.remote.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accesso centralizzato a Firebase Auth: stato sessione e sign-out globale.
 * Allineato al ruolo di `Auth.auth()` usato in `AuthFacade` / `LoginViewModel` iOS.
 */
@Singleton
class FirebaseAuthService @Inject constructor(
    private val auth: FirebaseAuth,
) {
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun signOut() {
        auth.signOut()
    }
}
