package it.vittorioscocca.kidbox.data.remote.auth

import it.vittorioscocca.kidbox.util.KBLog

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
/**
 * Facciata centrale per i provider registrati (stessa logica di [AuthFacade] su iOS).
 *
 * Su Android la mappa predefinita contiene Google e Apple via Firebase OAuthProvider.
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
        KBLog.auth.info("Sign-in requested for provider: $provider", TAG)
        val service = services[provider]
            ?: run {
                KBLog.auth.error("Auth provider not available: $provider", TAG)
                throw IllegalStateException("Auth provider not available: $provider")
            }
        KBLog.auth.debug("Delegating sign-in to provider: $provider", TAG)
        val user = service.signIn(presentation)
        KBLog.auth.info("Sign-in completed for provider: $provider", TAG)
        return user
    }

    /**
     * Sign-out globale Firebase (come `Auth.auth().signOut()` nel facade iOS).
     */
    fun signOut() {
        KBLog.auth.info("Global sign-out requested", TAG)
        try {
            firebaseAuth.signOut()
            KBLog.auth.info("Firebase sign-out successful", TAG)
        } catch (e: Exception) {
            KBLog.auth.error("Firebase sign-out failed: ${e.message}", TAG)
            throw e
        }
    }

    private companion object {
        private const val TAG = "KidBoxAuth"
    }
}
