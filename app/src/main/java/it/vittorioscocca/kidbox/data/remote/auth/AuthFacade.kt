package it.vittorioscocca.kidbox.data.remote.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
/**
 * Facciata centrale per i provider registrati (stessa logica di [AuthFacade] su iOS).
 *
 * Su Android la mappa predefinita contiene solo **Google**; Apple non è disponibile come su iOS.
 * Facebook è gestito separatamente da [FacebookAuthService], come nel `LoginViewModel` iOS.
 */
class AuthFacade(
    private val services: Map<AuthProvider, AuthService>,
    private val firebaseAuth: FirebaseAuth,
) {

    suspend fun signIn(
        provider: AuthProvider,
        presentation: AuthPresentation,
    ): FirebaseUser {
        Log.i(TAG, "Sign-in requested for provider: $provider")
        val service = services[provider]
            ?: run {
                Log.e(TAG, "Auth provider not available: $provider")
                throw IllegalStateException("Auth provider not available: $provider")
            }
        Log.d(TAG, "Delegating sign-in to provider: $provider")
        val user = service.signIn(presentation)
        Log.i(TAG, "Sign-in completed for provider: $provider")
        return user
    }

    /**
     * Sign-out globale Firebase (come `Auth.auth().signOut()` nel facade iOS).
     */
    fun signOut() {
        Log.i(TAG, "Global sign-out requested")
        try {
            firebaseAuth.signOut()
            Log.i(TAG, "Firebase sign-out successful")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase sign-out failed: ${e.message}")
            throw e
        }
    }

    private companion object {
        private const val TAG = "KidBoxAuth"
    }
}
