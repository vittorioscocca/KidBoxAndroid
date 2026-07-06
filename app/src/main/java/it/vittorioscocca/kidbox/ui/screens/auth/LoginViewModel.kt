package it.vittorioscocca.kidbox.ui.screens.auth

import it.vittorioscocca.kidbox.util.KBLog

import androidx.activity.ComponentActivity
import android.content.ActivityNotFoundException
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.PhotoPreviewCache
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.data.user.UserProfileRepository
import it.vittorioscocca.kidbox.data.remote.auth.AuthError
import it.vittorioscocca.kidbox.data.remote.auth.AuthFacade
import it.vittorioscocca.kidbox.data.remote.auth.AuthPresentation
import it.vittorioscocca.kidbox.data.remote.auth.AuthProvider
import it.vittorioscocca.kidbox.data.remote.auth.EmailAuthService
import it.vittorioscocca.kidbox.data.remote.auth.FacebookAuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthFacade,
    private val facebookAuth: FacebookAuthService,
    private val emailAuth: EmailAuthService,
    private val onboardingPreferences: OnboardingPreferences,
    private val userProfileRepository: UserProfileRepository,
    private val familyDao: KBFamilyDao,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    sealed class AuthCheckState {
        data object Checking : AuthCheckState()
        data object NotAuthenticated : AuthCheckState()
        data class Authenticated(val hasFamily: Boolean) : AuthCheckState()
    }

    private val _authCheckState = MutableStateFlow<AuthCheckState>(AuthCheckState.Checking)
    val authCheckState: StateFlow<AuthCheckState> = _authCheckState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _resetPasswordSent = MutableStateFlow(false)
    val resetPasswordSent: StateFlow<Boolean> = _resetPasswordSent.asStateFlow()

    private val _registrationPendingVerification = MutableStateFlow(false)
    val registrationPendingVerification: StateFlow<Boolean> =
        _registrationPendingVerification.asStateFlow()

    init {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                userProfileRepository.ensureSeededFromAuth()
                writePlatformToFirestore(user.uid)
                resetFirestoreClientAfterAuthChange()
            }
            val hasFamily = if (user != null) checkHasFamily() else false
            val hasOnboarding = onboardingPreferences.hasSeenOnboarding()

            KBLog.auth.debug("user=${user?.uid} hasFamily=$hasFamily hasOnboarding=$hasOnboarding", "KidBoxDebug")

            _authCheckState.value = if (user == null) {
                AuthCheckState.NotAuthenticated
            } else {
                AuthCheckState.Authenticated(hasFamily)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun signInGoogle(activity: ComponentActivity) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                auth.signIn(
                    AuthProvider.GOOGLE,
                    AuthPresentation.ActivityContext(activity),
                )
                onSignedInSuccessfully()
            } catch (e: Exception) {
                if (e is AuthError.Cancelled) return@launch
                _errorMessage.value = friendlyError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun signInApple(activity: ComponentActivity) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                auth.signIn(
                    AuthProvider.APPLE,
                    AuthPresentation.ActivityContext(activity),
                )
                onSignedInSuccessfully()
            } catch (e: Exception) {
                when (e) {
                    is AuthError.Cancelled -> return@launch
                    is ActivityNotFoundException ->
                        _errorMessage.value = "Apple Sign-In non disponibile su questo dispositivo."
                    else -> _errorMessage.value = friendlyError(e)
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun signInFacebook(activity: ComponentActivity) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                facebookAuth.signInWithFacebook(activity)
                onSignedInSuccessfully()
            } catch (e: Exception) {
                if (e is AuthError.Cancelled) return@launch
                _errorMessage.value = friendlyError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun signInEmail(email: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                emailAuth.signInWithEmail(email, password)
                onSignedInSuccessfully()
            } catch (e: Exception) {
                _errorMessage.value = friendlyError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun registerEmail(email: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            _registrationPendingVerification.value = false
            try {
                emailAuth.registerEmail(email, password)
                _registrationPendingVerification.value = true
            } catch (e: Exception) {
                _errorMessage.value = friendlyError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) return
        viewModelScope.launch {
            _resetPasswordSent.value = false
            _errorMessage.value = null
            try {
                emailAuth.sendPasswordReset(email)
                _resetPasswordSent.value = true
            } catch (e: Exception) {
                _errorMessage.value = friendlyError(e)
            }
        }
    }

    fun signOut() {
        try {
            auth.signOut()
            PhotoPreviewCache.clearAll(appContext)
        } catch (e: Exception) {
            // log only — come su iOS non esponiamo errore UI per signOut
        }
    }

    private suspend fun onSignedInSuccessfully() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        userProfileRepository.ensureSeededFromAuth()
        writePlatformToFirestore(user.uid)
        resetFirestoreClientAfterAuthChange()
        _authCheckState.value = AuthCheckState.Authenticated(checkHasFamily())
    }

    private fun writePlatformToFirestore(uid: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(mapOf("platform" to "android"), com.google.firebase.firestore.SetOptions.merge())
    }

    /**
     * Dopo login/logout con wipe locale, forza nuovo token e resetta il client Firestore
     * per evitare PERMISSION_DENIED transitori dovuti a credenziali/cache stale.
     */
    private suspend fun resetFirestoreClientAfterAuthChange() {
        try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
            delay(400)
            FirebaseFirestore.getInstance().terminate().await()
            FirebaseFirestore.getInstance().clearPersistence().await()
            delay(250)
            KBLog.auth.debug("resetFirestoreClientAfterAuthChange: OK", "KidBoxDebug")
        } catch (e: Exception) {
            KBLog.auth.warning("resetFirestoreClientAfterAuthChange: ${e.message}", "KidBoxDebug")
        }
    }

    private suspend fun checkHasFamily(): Boolean {
        // Prefer local state to avoid false negatives on slow/offline network.
        runCatching { familyDao.hasAnyFamily() }
            .onSuccess { hasLocalFamily ->
                if (hasLocalFamily) {
                    KBLog.auth.debug("checkHasFamily: local family found -> true", "KidBoxDebug")
                    return true
                }
            }
            .onFailure { err ->
                KBLog.auth.warning("checkHasFamily local lookup failed: ${err.message}", "KidBoxDebug")
            }

        return try {
            checkHasFamilyOnce()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                KBLog.auth.error("checkHasFamily firestore error: ${e.message}", "KidBoxDebug")
                return false
            }
            KBLog.auth.warning("checkHasFamily: PERMISSION_DENIED, reset+retry", "KidBoxDebug")
            resetFirestoreClientAfterAuthChange()
            runCatching { checkHasFamilyOnce() }
                .onFailure { t -> KBLog.auth.error("checkHasFamily retry failed: ${t.message}", "KidBoxDebug") }
                .getOrDefault(false)
        } catch (e: Exception) {
            KBLog.auth.error("checkHasFamily error: ${e.message}", "KidBoxDebug")
            false
        }
    }

    private suspend fun checkHasFamilyOnce(): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val membershipsSnap = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("memberships")
            .get(Source.SERVER)
            .await()
        val candidateFamilyIds = buildList {
            membershipsSnap.documents.forEach { doc ->
                if (doc.id.isNotBlank()) add(doc.id)
                (doc.data?.get("familyId") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { add(it) }
            }
        }.distinct()

        // Fallback robusto: se memberships è vuota/incoerente, ricava famiglie da members collectionGroup.
        val resolvedFamilyIds = if (candidateFamilyIds.isNotEmpty()) {
            candidateFamilyIds
        } else {
            KBLog.auth.warning("checkHasFamily: memberships vuote, fallback collectionGroup(members)", "KidBoxDebug")
            val memberDocs = FirebaseFirestore.getInstance()
                .collectionGroup("members")
                .whereEqualTo("uid", uid)
                .get(Source.SERVER)
                .await()
                .documents
            memberDocs
                .filter { it.data?.get("isDeleted") as? Boolean != true }
                .mapNotNull { it.reference.parent.parent?.id }
                .distinct()
        }

        if (resolvedFamilyIds.isEmpty()) {
            KBLog.auth.debug("checkHasFamily: no family ids on server -> false", "KidBoxDebug")
            return false
        }

        // Verifica definitiva: devo avere almeno un member doc valido nella family.
        val hasValidMembership = resolvedFamilyIds.any { familyId ->
            if (familyId.isBlank()) return@any false
            val memberSnap = FirebaseFirestore.getInstance()
                .collection("families")
                .document(familyId)
                .collection("members")
                .document(uid)
                .get(Source.SERVER)
                .await()
            memberSnap.exists() && (memberSnap.data?.get("isDeleted") as? Boolean != true)
        }

        KBLog.auth.debug("checkHasFamily server candidates=${resolvedFamilyIds.size} hasValidMembership=$hasValidMembership", "KidBoxDebug")
        return hasValidMembership
    }

    private fun friendlyError(error: Throwable): String {
        val fe = error as? FirebaseAuthException ?: error.cause as? FirebaseAuthException
        if (fe == null) return error.localizedMessage.orEmpty()
        return when (fe.errorCode) {
            ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL ->
                "Questa email è già registrata con un altro accesso (Google, Apple o email). " +
                    "Usa lo stesso pulsante di prima oppure contatta il supporto per unire gli accessi."
            ERROR_EMAIL_ALREADY_IN_USE ->
                "Questa email è già registrata. Prova ad accedere."
            ERROR_INVALID_EMAIL -> "Indirizzo email non valido."
            ERROR_WEAK_PASSWORD -> "La password è troppo debole (min. 6 caratteri)."
            ERROR_WRONG_PASSWORD ->
                "Password errata. Riprova o usa \"Password dimenticata\"."
            ERROR_USER_NOT_FOUND -> "Nessun account trovato con questa email."
            ERROR_NETWORK_REQUEST_FAILED -> "Errore di rete. Controlla la connessione."
            ERROR_TOO_MANY_REQUESTS -> "Troppi tentativi. Riprova tra qualche minuto."
            ERROR_USER_DISABLED -> "Account disabilitato. Contatta il supporto."
            else -> fe.localizedMessage ?: error.localizedMessage.orEmpty()
        }
    }

    private companion object {
        private const val ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL =
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"
        private const val ERROR_EMAIL_ALREADY_IN_USE = "ERROR_EMAIL_ALREADY_IN_USE"
        private const val ERROR_INVALID_EMAIL = "ERROR_INVALID_EMAIL"
        private const val ERROR_WEAK_PASSWORD = "ERROR_WEAK_PASSWORD"
        private const val ERROR_WRONG_PASSWORD = "ERROR_WRONG_PASSWORD"
        private const val ERROR_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"
        private const val ERROR_NETWORK_REQUEST_FAILED = "ERROR_NETWORK_REQUEST_FAILED"
        private const val ERROR_TOO_MANY_REQUESTS = "ERROR_TOO_MANY_REQUESTS"
        private const val ERROR_USER_DISABLED = "ERROR_USER_DISABLED"
    }
}
