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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun signInWithFacebook(activity: ComponentActivity): FirebaseUser =
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

            LoginManager.getInstance().logOut()

            LoginManager.getInstance().logInWithReadPermissions(
                activity,
                listOf("public_profile", "email"),
            )

            cont.invokeOnCancellation {
                synchronized(lock) {
                    if (pending === cont) pending = null
                }
            }
        }

    fun signOut() {
        firebaseAuth.signOut()
        LoginManager.getInstance().logOut()
    }
}