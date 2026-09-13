package it.vittorioscocca.kidbox.data.sync

import com.google.firebase.functions.FirebaseFunctionsException
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presidio davanti alle Cloud Function che portano un `familyId`.
 *
 * Il problema che risolve, visto nei log l'8/09/2026: quando un utente esce (o
 * viene tolto) da una famiglia, l'app non se ne accorge. L'id resta nelle
 * preferenze e ogni schermata continua a chiedere al server i dati di quella
 * famiglia — `getAIUsage`, `getStorageUsage`, `askAI`. Il server risponde
 * `permission-denied` ogni volta, e il client ritenta: dieci rifiuti in dieci
 * minuti per un solo utente.
 *
 * Qui il rifiuto viene riconosciuto dal `reason` nei `details` (non dal
 * messaggio, che è testo per l'utente), le chiamate verso quella famiglia si
 * fermano per un po', e il caso passa a [FamilySyncCenter.handleCallableAccessDenied],
 * l'unico che può decidere se è un'espulsione vera.
 */
@Singleton
class FamilyAccessGuard @Inject constructor(
    private val syncCenter: FamilySyncCenter,
) {
    companion object {
        private const val TAG = "FamilyAccessGuard"

        /**
         * Il `details.reason` mandato dal server. Deve restare uguale a
         * `NOT_FAMILY_MEMBER` in `functions/index.js`.
         */
        const val NOT_FAMILY_MEMBER = "not-family-member"

        /** Silenzio dopo un rifiuto non confermato come revoca. */
        private const val COOLDOWN_MS = 60_000L

        /** Dopo una revoca confermata non ha senso ritentare: cambia la famiglia. */
        private const val CONFIRMED_COOLDOWN_MS = 3_600_000L

        /** `true` se l'errore dice «non sei (più) membro di questa famiglia». */
        fun isNotAMember(error: Throwable?): Boolean {
            val e = error as? FirebaseFunctionsException ?: return false
            if (e.code != FirebaseFunctionsException.Code.PERMISSION_DENIED) return false
            val details = e.details as? Map<*, *> ?: return false
            return details["reason"] == NOT_FAMILY_MEMBER
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val blockedUntil = ConcurrentHashMap<String, Long>()

    /** `true` se le chiamate verso questa famiglia sono sospese. */
    fun isBlocked(familyId: String): Boolean {
        val until = blockedUntil[familyId] ?: return false
        if (until <= System.currentTimeMillis()) {
            blockedUntil.remove(familyId)
            return false
        }
        return true
    }

    /**
     * Riapre le chiamate. Da chiamare quando la situazione è cambiata davvero:
     * join, switch famiglia, nuovo login.
     */
    fun clear(familyId: String? = null) {
        if (familyId != null) blockedUntil.remove(familyId) else blockedUntil.clear()
    }

    /** Registra un rifiuto per mancata appartenenza e chiede la verifica. */
    fun noteAccessDenied(familyId: String, source: String) {
        if (familyId.isEmpty() || isBlocked(familyId)) return
        blockedUntil[familyId] = System.currentTimeMillis() + COOLDOWN_MS
        KBLog.sync.warning(
            "Callable rifiutata: non siamo membri di questa famiglia. " +
                "Chiamate sospese ${COOLDOWN_MS / 1000}s source=$source familyId=$familyId",
            TAG,
        )
        scope.launch {
            if (syncCenter.handleCallableAccessDenied(familyId, source)) {
                blockedUntil[familyId] = System.currentTimeMillis() + CONFIRMED_COOLDOWN_MS
            }
        }
    }

    /**
     * Esegue una chiamata verso una famiglia con il presidio davanti e dietro.
     * Ogni callable con `familyId` dovrebbe passare di qui: è il solo punto in
     * cui il rifiuto viene visto una volta sola invece che in sei schermate.
     */
    suspend fun <T> guarded(familyId: String, source: String, block: suspend () -> T): T {
        if (isBlocked(familyId)) {
            KBLog.sync.debug("Callable non inviata: accesso sospeso familyId=$familyId", TAG)
            throw FamilyAccessSuspendedException()
        }
        try {
            return block()
        } catch (e: Exception) {
            if (isNotAMember(e)) noteAccessDenied(familyId, source)
            throw e
        }
    }
}

/** Chiamata non partita: le chiamate verso quella famiglia sono sospese. */
class FamilyAccessSuspendedException :
    Exception("Non hai più accesso a questa famiglia.")
