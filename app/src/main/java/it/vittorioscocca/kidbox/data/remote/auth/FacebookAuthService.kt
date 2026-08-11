package it.vittorioscocca.kidbox.data.remote.auth

import androidx.activity.ComponentActivity
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

/** Oltre questo tempo il flusso è considerato perso, non lento. */
private const val SIGN_IN_TIMEOUT_MS = 5 * 60 * 1000L

@Singleton
class FacebookAuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val callbackManager: CallbackManager,
) {

    private val lock = Any()
    private var pending: kotlinx.coroutines.CancellableContinuation<FirebaseUser>? = null

    init {
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val tokenString = AccessToken.getCurrentAccessToken()?.token
                        ?: result.accessToken?.token
                    val cont = synchronized(lock) {
                        pending.also { pending = null }
                    } ?: return

                    if (tokenString.isNullOrEmpty()) {
                        cont.resumeWithException(AuthError.MissingToken)
                        return
                    }

                    firebaseAuth
                        .signInWithCredential(FacebookAuthProvider.getCredential(tokenString))
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = task.result?.user
                                if (user != null) cont.resume(user)
                                else cont.resumeWithException(AuthError.Unknown)
                            } else {
                                cont.resumeWithException(
                                    task.exception ?: AuthError.Unknown,
                                )
                            }
                        }
                }

                override fun onCancel() {
                    val cont = synchronized(lock) {
                        pending.also { pending = null }
                    } ?: return
                    cont.resumeWithException(AuthError.Cancelled)
                }

                override fun onError(error: FacebookException) {
                    val cont = synchronized(lock) {
                        pending.also { pending = null }
                    } ?: return
                    cont.resumeWithException(error)
                }
            },
        )
    }

    fun callbackManager(): CallbackManager = callbackManager

    /**
     * Avvia il login Facebook e sospende finché il callback non risponde.
     *
     * Il timeout non è un dettaglio difensivo: senza, un esito che non torna
     * indietro lascia questa coroutine sospesa per sempre. Il chiamante resta
     * nel suo `try` senza mai raggiungere il `finally`, quindi la schermata di
     * caricamento non si spegne, e `pending` non si libera — bloccando anche
     * tutti i tentativi successivi sulla guardia qui sotto. Meglio un errore
     * dopo qualche minuto che un'app ferma.
     */
    suspend fun signInWithFacebook(activity: ComponentActivity): FirebaseUser =
        withTimeoutOrNull(SIGN_IN_TIMEOUT_MS) { awaitSignIn(activity) }
            ?: throw AuthError.TimedOut

    private suspend fun awaitSignIn(activity: ComponentActivity): FirebaseUser =
        suspendCancellableCoroutine { cont ->
            synchronized(lock) {
                if (pending != null) {
                    cont.resumeWithException(
                        IllegalStateException("Facebook sign-in already in progress"),
                    )
                    return@suspendCancellableCoroutine
                }
                pending = cont
            }

            cont.invokeOnCancellation {
                synchronized(lock) {
                    if (pending === cont) pending = null
                }
            }

            LoginManager.getInstance().logOut()

            // Overload basato sulle API AndroidX di Activity Result.
            //
            // L'overload `(Activity, Collection)` consegna l'esito via
            // `startActivityForResult`, quindi a `Activity.onActivityResult`: da
            // lì raggiunge il CallbackManager solo se l'Activity fa l'override e
            // inoltra il risultato a mano. MainActivity non lo fa, e il login
            // finiva nel vuoto — FacebookActivity si chiudeva e il callback non
            // scattava mai, né in successo né in errore.
            //
            // Passando il CallbackManager insieme all'ActivityResultRegistryOwner
            // è l'SDK a registrarsi sul registry e a consegnarsi l'esito da solo.
            LoginManager.getInstance().logInWithReadPermissions(
                activity,
                callbackManager,
                listOf("public_profile", "email"),
            )
        }

    fun signOut() {
        firebaseAuth.signOut()
        LoginManager.getInstance().logOut()
    }
}