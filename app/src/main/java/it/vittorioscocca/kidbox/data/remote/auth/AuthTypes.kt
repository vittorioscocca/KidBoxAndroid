package it.vittorioscocca.kidbox.data.remote.auth

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import it.vittorioscocca.kidbox.R
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
 *
 * Ogni caso porta due testi con due destinatari diversi: [message], in inglese,
 * finisce nei log e negli stack trace ed è per chi legge un crash report;
 * [messageRes] è quello che vede l'utente, e vive in `strings.xml` perché va
 * tradotto. Tenere un solo testo costringerebbe a scegliere fra un log
 * localizzato — inutile per chi indaga — e un messaggio d'errore in una lingua
 * che l'utente non parla.
 *
 * `messageRes` è nullo solo per [Cancelled], che non è un errore da mostrare:
 * l'utente ha chiuso lui il dialog e sa già cos'è successo.
 */
sealed class AuthError(
    message: String?,
    @StringRes val messageRes: Int? = null,
) : Exception(message) {

    object Cancelled : AuthError(null)

    object MissingToken : AuthError(
        "Access token missing.",
        R.string.auth_error_missing_token,
    )

    data class InvalidPresentation(override val message: String) : AuthError(message)

    object Unknown : AuthError(
        "Unknown error.",
        R.string.auth_error_unknown,
    )

    /**
     * Il provider non ha mai risposto. Non è un errore che l'utente possa
     * causare: è la rete di sicurezza per un esito che non torna mai indietro
     * (un callback non consegnato, un'Activity uccisa dal sistema). Esiste
     * perché il fallimento silenzioso — schermata di caricamento infinita e
     * nessun messaggio — è la cosa peggiore che possa fare un login.
     */
    object TimedOut : AuthError(
        "Sign-in did not complete in time.",
        R.string.auth_error_timed_out,
    )
}

/**
 * Servizio di sign-in per un provider (allineato al protocol `AuthService` iOS).
 */
interface AuthService {
    val provider: AuthProvider

    suspend fun signIn(presentation: AuthPresentation): FirebaseUser
}
