package it.vittorioscocca.kidbox.data.remote.auth

import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseUser

/**
 * Provider di autenticazione (allineato a [AuthProvider] iOS).
 * Su Android non è registrato Apple Sign-In nella facade predefinita.
 */
enum class AuthProvider {
    APPLE,
    GOOGLE,
    FACEBOOK,
}

/**
 * Contesto di presentazione per i flussi che richiedono un'Activity.
 * Su iOS: UIWindow vs UIViewController; su Android: Activity.
 */
sealed class AuthPresentation {
    data class ActivityContext(val activity: ComponentActivity) : AuthPresentation()
}

/**
 * Errori applicativi per flussi auth (allineati a [AuthError] iOS).
 */
sealed class AuthError(message: String?) : Exception(message) {
    object Cancelled : AuthError(null)

    object MissingToken : AuthError("Token di accesso mancante.")

    data class InvalidPresentation(override val message: String) : AuthError(message)

    object Unknown : AuthError("Errore sconosciuto.")
}

/**
 * Servizio di sign-in per un provider (allineato al protocol `AuthService` iOS).
 */
interface AuthService {
    val provider: AuthProvider

    suspend fun signIn(presentation: AuthPresentation): FirebaseUser
}
