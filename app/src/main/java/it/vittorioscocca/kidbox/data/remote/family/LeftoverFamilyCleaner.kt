package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.NudgeSignalsDao
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Elimina la famiglia creata solo per superare l'onboarding, quando poi si entra
 * in quella vera su invito.
 *
 * Chi installa l'app crea una famiglia per arrivare in fondo alla configurazione
 * iniziale, e subito dopo accetta l'invito della famiglia di casa. Quella prima
 * famiglia resta lì, vuota, e fa danni: occupa uno dei due slot per account, e
 * ogni pezzo di codice che deve scegliere "la famiglia" ha due candidate.
 *
 * NON è una pulizia generica: un utente può benissimo avere una famiglia sua e
 * farsi invitare in un'altra (i nonni, l'ex partner). Si cancella solo ciò che è
 * dimostrabilmente un residuo:
 *
 *   - l'ha creata questo utente, e
 *   - lui è l'unico membro attivo, e
 *   - non contiene NIENTE: né figli, né documenti, wallet, esami, chat, eventi
 *     o conversazioni AI.
 *
 * Se anche una sola di queste condizioni non regge, la famiglia resta dov'è.
 */
@Singleton
class LeftoverFamilyCleaner @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val signalsDao: NudgeSignalsDao,
    private val familyLeaveService: FamilyLeaveService,
    private val auth: FirebaseAuth,
) {

    /**
     * Da chiamare dopo un join riuscito, con la famiglia in cui si è appena
     * entrati: quella non viene mai toccata.
     */
    suspend fun deleteEmptyOwnedFamilies(keepFamilyId: String) {
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return
        val candidate = familyDao.getAll()
            .filter { it.id != keepFamilyId && it.createdBy == uid }

        for (family in candidate) {
            if (!isEmptyLeftover(family.id)) {
                KBLog.data.info("Famiglia ${family.id} non vuota: non la elimino", TAG)
                continue
            }
            runCatching { familyLeaveService.deleteFamily(family.id) }
                .onSuccess { KBLog.data.info("Eliminata la famiglia residua ${family.id}", TAG) }
                .onFailure { KBLog.data.error("Eliminazione famiglia residua fallita: ${it.message}", TAG) }
        }
    }

    private suspend fun isEmptyLeftover(familyId: String): Boolean =
        signalsDao.familyMemberCount(familyId) <= 1 &&
            signalsDao.childCount(familyId) == 0 &&
            signalsDao.documentCount(familyId) == 0 &&
            signalsDao.walletTicketCount(familyId) == 0 &&
            signalsDao.medicalExamCount(familyId) == 0 &&
            signalsDao.chatMessageCount(familyId) == 0 &&
            signalsDao.calendarEventCount(familyId) == 0 &&
            signalsDao.aiConversationCount(familyId) == 0

    private companion object {
        private const val TAG = "LeftoverFamilyCleaner"
    }
}
