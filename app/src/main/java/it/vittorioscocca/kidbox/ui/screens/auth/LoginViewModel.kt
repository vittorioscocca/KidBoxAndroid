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
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.remote.auth.AuthError
import it.vittorioscocca.kidbox.data.remote.auth.AuthFacade
import it.vittorioscocca.kidbox.data.remote.auth.AuthPresentation
import it.vittorioscocca.kidbox.data.remote.auth.DeviceSessionRegistry
import it.vittorioscocca.kidbox.data.remote.auth.AuthProvider
import it.vittorioscocca.kidbox.data.remote.auth.EmailAuthService
import it.vittorioscocca.kidbox.data.remote.auth.FacebookAuthService
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import it.vittorioscocca.kidbox.data.remote.family.FamilyIdsResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthFacade,
    private val facebookAuth: FacebookAuthService,
    private val emailAuth: EmailAuthService,
    private val onboardingPreferences: OnboardingPreferences,
    private val userProfileRepository: UserProfileRepository,
    private val familyDao: KBFamilyDao,
    private val deviceSessionRegistry: DeviceSessionRegistry,
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

    /**
     * Rete di sicurezza per i login che passano dal browser (Apple): se il
     * sistema distrugge l'activity mentre l'utente è sulla pagina del provider,
     * Firebase completa comunque l'autenticazione ma il ramo di successo del
     * ViewModel non viene mai raggiunto, e la schermata di login resta lì pur
     * essendo la sessione già valida. Qui ce ne accorgiamo e proseguiamo.
     */
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser ?: return@AuthStateListener
        // Solo con la schermata di login già a video: durante `Checking` se ne
        // occupa l'init, e con `isBusy` c'è un login esplicito in corso che
        // arriva da sé al ramo di successo. Senza questi due filtri
        // `onSignedInSuccessfully` verrebbe eseguito due volte, e con lui il
        // reset della persistenza Firestore.
        if (_authCheckState.value !is AuthCheckState.NotAuthenticated || _isBusy.value) {
            return@AuthStateListener
        }
        KBLog.auth.info("Auth state: sessione valida con login a video, proseguo uid=${user.uid}", "KidBoxAuth")
        viewModelScope.launch {
            runCatching { onSignedInSuccessfully() }
                .onFailure { KBLog.auth.warning("Recupero sessione fallito: ${it.message}", "KidBoxAuth") }
        }
    }

    override fun onCleared() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        super.onCleared()
    }

    init {
        viewModelScope.launch {
            // Chi ha una sessione email non verificata viene fatto uscire prima
            // di toccare qualunque dato: gemello del controllo in
            // `AppCoordinator.startSessionListener` su iOS.
            if (emailAuth.signOutIfEmailUnverified()) {
                KBLog.auth.info("Email non verificata: sessione chiusa all'avvio", "KidBoxAuth")
                _errorMessage.value = appContext.getString(R.string.auth_error_email_not_verified)
            }
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                userProfileRepository.ensureSeededFromAuth()
                runCatching { userProfileRepository.hydrateFromFirestore() }
                writePlatformToFirestore(user.uid)
                // Solo a identità cambiata. Qui siamo all'avvio con una sessione
                // già valida: se l'uid è lo stesso dell'ultimo reset, ripulire la
                // persistenza significherebbe buttare la cache Firestore a OGNI
                // apertura dell'app. Misurato l'11/09/2026 su device: due avvii
                // consecutivi, 179 document_change identici ciascuno (104
                // passwords, 51 trips, 15 passwordGroups) e ~236 letture, mentre
                // senza reset il server conferma il set invariato con un
                // existence filter e non trasferisce nulla.
                if (lastFirestoreResetUid() != user.uid) {
                    resetFirestoreClientAfterAuthChange()
                } else {
                    KBLog.auth.debug(
                        "reset persistenza saltato: stessa identita uid=${user.uid}",
                        "KidBoxDebug",
                    )
                }
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
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }


    fun clearError() {
        _errorMessage.value = null
    }

    fun signInGoogle(activity: ComponentActivity) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            AppAnalytics.loginAttempted(appContext, "google")
            try {
                auth.signIn(
                    AuthProvider.GOOGLE,
                    AuthPresentation.ActivityContext(activity),
                )
                onSignedInSuccessfully("google")
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
            AppAnalytics.loginAttempted(appContext, "apple")
            try {
                auth.signIn(
                    AuthProvider.APPLE,
                    AuthPresentation.ActivityContext(activity),
                )
                onSignedInSuccessfully("apple")
            } catch (e: Exception) {
                when (e) {
                    is AuthError.Cancelled -> return@launch
                    is ActivityNotFoundException ->
                        _errorMessage.value =
                            appContext.getString(R.string.auth_error_apple_unavailable)
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
            AppAnalytics.loginAttempted(appContext, "facebook")
            try {
                facebookAuth.signInWithFacebook(activity)
                onSignedInSuccessfully("facebook")
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

    /**
     * Chiude il banner "controlla la tua email" mostrato dopo la registrazione.
     *
     * Serve al pulsante che riporta al login: senza, il banner resterebbe
     * appeso anche dopo essere tornati alla modalità accesso.
     * Gemello di `vm.registrationPendingVerification = false` su iOS.
     */
    fun clearRegistrationPendingVerification() {
        _registrationPendingVerification.value = false
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
        // La rimozione della sessione va PRIMA del sign-out — dopo le rules non
        // lascerebbero più scrivere, e questo dispositivo resterebbe per sempre
        // nell'elenco degli altri. Il sign-out avviene comunque, anche se la
        // cancellazione fallisce: un errore di rete non deve impedire a
        // qualcuno di uscire dal proprio account.
        viewModelScope.launch {
            runCatching { deviceSessionRegistry.stopAndRemove() }
            try {
                auth.signOut()
                PhotoPreviewCache.clearAll(appContext)
            } catch (e: Exception) {
                // log only — come su iOS non esponiamo errore UI per signOut
            }
        }
    }

    private suspend fun onSignedInSuccessfully(provider: String? = null) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (provider != null) {
            val metadata = user.metadata
            val isNewUser = metadata != null && metadata.creationTimestamp == metadata.lastSignInTimestamp
            if (isNewUser) {
                AppAnalytics.signupStarted(appContext, provider)
                AppAnalytics.signupMethodSelected(appContext, provider)
                AppAnalytics.signupCompleted(appContext, provider)
            }
        }
        // Qui l'autenticazione è GIÀ riuscita: da questo punto in poi nulla
        // deve poter riportare l'utente alla schermata di login, o al tocco
        // successivo si riaprirebbe la pagina del provider come se il login non
        // fosse mai avvenuto.
        //
        // `ensureSeededFromAuth` scrive su Firestore e ne aspetta la conferma,
        // che arriva solo dal server: offline quel Task non si completa MAI e
        // il login resta appeso con lo spinner acceso. Non è un passo
        // indispensabile per entrare — lo rifà il prossimo avvio — quindi ha un
        // tetto di tempo e un fallimento che non blocca.
        withTimeoutOrNull(SEED_TIMEOUT_MS) {
            runCatching { userProfileRepository.ensureSeededFromAuth() }
                .onFailure { KBLog.auth.warning("Seeding profilo fallito: ${it.message}", "KidBoxAuth") }
        } ?: KBLog.auth.warning("Seeding profilo oltre il tempo massimo: proseguo", "KidBoxAuth")

        runCatching { userProfileRepository.hydrateFromFirestore() }
        writePlatformToFirestore(user.uid)
        resetFirestoreClientAfterAuthChange()
        val hasFamily = runCatching { checkHasFamily() }
            .onFailure { KBLog.auth.warning("Controllo famiglia fallito: ${it.message}", "KidBoxAuth") }
            .getOrDefault(false)
        _authCheckState.value = AuthCheckState.Authenticated(hasFamily)
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
     *
     * ATTENZIONE: `clearPersistence()` cancella l'INTERA cache locale — documenti e
     * resume token — quindi dopo questa chiamata ogni listener riscarica dal server
     * la sua collezione intera. Va invocata solo quando l'identita' e' davvero
     * cambiata (login esplicito) o in reazione a un PERMISSION_DENIED, mai come
     * profilassi a ogni avvio. Registra l'uid trattato cosi' che l'`init` possa
     * saltarla a sessione invariata.
     */
    private suspend fun resetFirestoreClientAfterAuthChange() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
            delay(400)
            FirebaseFirestore.getInstance().terminate().await()
            FirebaseFirestore.getInstance().clearPersistence().await()
            delay(250)
            if (uid != null) rememberFirestoreResetUid(uid)
            KBLog.auth.debug("resetFirestoreClientAfterAuthChange: OK", "KidBoxDebug")
        } catch (e: Exception) {
            KBLog.auth.warning("resetFirestoreClientAfterAuthChange: ${e.message}", "KidBoxDebug")
        }
    }

    /** uid per cui la persistenza Firestore e' stata ripulita l'ultima volta. */
    private fun lastFirestoreResetUid(): String? =
        appContext.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_FIRESTORE_RESET_UID, null)

    private fun rememberFirestoreResetUid(uid: String) {
        appContext.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_FIRESTORE_RESET_UID, uid)
            .apply()
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
        // Indice `memberships` + documenti membro insieme, dal server: appena
        // dopo il login la cache non vale, e l'indice è una copia che può avere
        // buchi (il fallback di prima scattava solo a elenco vuoto).
        val resolvedFamilyIds = FamilyIdsResolver.resolve(uid, Source.SERVER)

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

    /**
     * Traduce un errore tecnico nel testo che l'utente legge.
     *
     * L'ordine conta: prima i nostri [AuthError], che sanno già quale stringa
     * mostrare, poi i codici di FirebaseAuth. Il fallback resta il messaggio
     * dell'eccezione — non è tradotto, ma è pur sempre meglio di una schermata
     * che fallisce senza dire niente.
     */
    private fun friendlyError(error: Throwable): String {
        (error as? AuthError ?: error.cause as? AuthError)?.messageRes?.let {
            return appContext.getString(it)
        }

        val fe = error as? FirebaseAuthException ?: error.cause as? FirebaseAuthException
        if (fe == null) return error.localizedMessage.orEmpty()
        val res = when (fe.errorCode) {
            ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL ->
                R.string.auth_error_account_exists_different_credential
            ERROR_EMAIL_ALREADY_IN_USE -> R.string.auth_error_email_already_in_use
            ERROR_INVALID_EMAIL -> R.string.auth_error_invalid_email
            ERROR_WEAK_PASSWORD -> R.string.auth_error_weak_password
            ERROR_WRONG_PASSWORD -> R.string.auth_error_wrong_password
            ERROR_USER_NOT_FOUND -> R.string.auth_error_user_not_found
            ERROR_NETWORK_REQUEST_FAILED -> R.string.auth_error_network
            ERROR_TOO_MANY_REQUESTS -> R.string.auth_error_too_many_requests
            ERROR_USER_DISABLED -> R.string.auth_error_user_disabled
            else -> null
        }
        return res?.let { appContext.getString(it) }
            ?: fe.localizedMessage
            ?: error.localizedMessage.orEmpty()
    }

    private companion object {
        /** Tetto al seeding del profilo: vedi `onSignedInSuccessfully`. */
        private const val SEED_TIMEOUT_MS = 8_000L

        /** Dove ricordiamo per quale uid abbiamo gia' ripulito la persistenza Firestore. */
        private const val AUTH_PREFS = "auth_state"
        private const val KEY_LAST_FIRESTORE_RESET_UID = "last_firestore_reset_uid"

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
