package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.NudgeSignalsDao
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.tasks.await
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
 *
 * ⚠️ Quelle condizioni vanno verificate **sul server**, non su Room. Il
 * 23/09/2026, su iOS, questa stessa pulizia ha chiesto la cancellazione di una
 * famiglia d'origine viva — cinque membri, documenti, note e to-do — subito dopo
 * un join. Il motivo: il database locale di una famiglia **non attiva** è vuoto
 * per costruzione. Il bootstrap non ne scarica nemmeno i membri, e documenti,
 * wallet e chat arrivano solo dai listener della famiglia attiva: contarli in
 * locale significa contare ciò che non è mai stato scaricato. Quella volta la
 * famiglia si è salvata solo perché il presidio server rifiuta la cancellazione
 * con più di un membro attivo; con un membro solo sarebbe sparita davvero, con
 * tutto ciò che conteneva.
 *
 * Room resta il primo filtro, per scartare in fretta i casi ovvi; la prova che
 * autorizza la cancellazione arriva da Firestore, letta con [Source.SERVER]
 * (dalla cache risponderebbe lo stesso vuoto). E vale la regola di
 * `FamilySyncCenter.verifyRevocation`: assenza di prove non è prova. Se anche
 * una sola lettura non riesce, non si cancella niente.
 *
 * Gemello di `KBLeftoverFamilyCleaner` su iOS, stesse regole.
 */
@Singleton
class LeftoverFamilyCleaner @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val signalsDao: NudgeSignalsDao,
    private val familyLeaveService: FamilyLeaveService,
    private val auth: FirebaseAuth,
) {

    /** Esito della verifica sul server. */
    private sealed interface ServerVerdict {
        /** Il server conferma: nessun altro membro attivo, nessun contenuto. */
        data object Leftover : ServerVerdict

        /** Il server dice che la famiglia è viva. */
        data class InUse(val reason: String) : ServerVerdict

        /** Non è stato possibile saperlo (rete, regole, credenziali). */
        data class Unknown(val reason: String) : ServerVerdict
    }

    /**
     * Da chiamare dopo un join riuscito, con la famiglia in cui si è appena
     * entrati: quella non viene mai toccata.
     */
    suspend fun deleteEmptyOwnedFamilies(keepFamilyId: String) {
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return
        val candidate = familyDao.getAll()
            .filter { it.id != keepFamilyId && it.createdBy == uid }

        for (family in candidate) {
            // Primo filtro, gratis: se il locale sa già di contenuti, è viva.
            // Il contrario non vale — vedi il commento in testa alla classe.
            if (!isEmptyLeftover(family.id)) {
                KBLog.data.info("Famiglia ${family.id} non vuota in locale: non la elimino", TAG)
                continue
            }

            when (val verdict = serverVerdict(family.id, uid)) {
                is ServerVerdict.InUse -> {
                    KBLog.data.info("Famiglia ${family.id} viva sul server (${verdict.reason}): non la elimino", TAG)
                    continue
                }
                is ServerVerdict.Unknown -> {
                    KBLog.data.error("Famiglia ${family.id}: verifica sul server impossibile (${verdict.reason}). Nel dubbio non elimino", TAG)
                    continue
                }
                ServerVerdict.Leftover -> Unit
            }

            // `deleteFamilyIfServerConfirms` e non `deleteFamily`: il wipe locale
            // deve avvenire solo se il server ha davvero cancellato.
            runCatching { familyLeaveService.deleteFamilyIfServerConfirms(family.id) }
                .onSuccess { KBLog.data.info("Eliminata la famiglia residua ${family.id}", TAG) }
                .onFailure { KBLog.data.error("Eliminazione famiglia residua fallita: ${it.message}", TAG) }
        }
    }

    // ── Prova locale (solo per scartare in fretta) ──────────────────────────

    private suspend fun isEmptyLeftover(familyId: String): Boolean =
        signalsDao.familyMemberCount(familyId) <= 1 &&
            signalsDao.childCount(familyId) == 0 &&
            signalsDao.documentCount(familyId) == 0 &&
            signalsDao.walletTicketCount(familyId) == 0 &&
            signalsDao.medicalExamCount(familyId) == 0 &&
            signalsDao.chatMessageCount(familyId) == 0 &&
            signalsDao.calendarEventCount(familyId) == 0 &&
            signalsDao.aiConversationCount(familyId) == 0

    // ── Prova sul server (l'unica che autorizza la cancellazione) ───────────

    /**
     * Chiede a Firestore se la famiglia è davvero un residuo: un solo membro
     * attivo (io) e nessun contenuto in nessuna sottocollezione.
     */
    private suspend fun serverVerdict(familyId: String, uid: String): ServerVerdict {
        val famiglia = FirebaseFirestore.getInstance().collection("families").document(familyId)

        // 1) I membri. `Source.SERVER` perché dalla cache una famiglia mai
        // sincronizzata risulta senza membri, che è esattamente l'errore da
        // evitare.
        val membri = runCatching { famiglia.collection("members").get(Source.SERVER).await() }
            .getOrElse { return ServerVerdict.Unknown("membri non leggibili: ${it.message}") }

        val attivi = membri.documents.filter { it.getBoolean("isDeleted") != true }
        if (attivi.size != 1) return ServerVerdict.InUse("membri attivi=${attivi.size}")
        if (attivi.first().id != uid) return ServerVerdict.InUse("l'unico membro attivo non sono io")

        // 2) I contenuti. Una riga qualsiasi in una qualsiasi di queste
        // collezioni basta a dire che la famiglia è stata usata: la pulizia vale
        // solo per una famiglia in cui non è mai entrato niente.
        for (collezione in CONTENT_COLLECTIONS) {
            val snap = runCatching {
                famiglia.collection(collezione).limit(1).get(Source.SERVER).await()
            }.getOrElse { return ServerVerdict.Unknown("$collezione non leggibile: ${it.message}") }

            if (!snap.isEmpty) return ServerVerdict.InUse("$collezione non è vuota")
        }

        return ServerVerdict.Leftover
    }

    private companion object {
        private const val TAG = "LeftoverFamilyCleaner"

        /**
         * Le sottocollezioni che dimostrano l'**uso** di una famiglia.
         *
         * Sono un sottoinsieme di `FAMILY_SUBCOLLECTIONS` in
         * `functions/index.js`: restano fuori quelle che esistono anche in una
         * famiglia mai usata e non proverebbero niente (`members`, `invites`,
         * `locations`, `counters`, `stats`, `documentCategories`,
         * `memberKeyBackups`) e quelle figlie di un'altra (`routineChecks`,
         * `geofenceEvents`). Stesso elenco del gemello iOS.
         */
        private val CONTENT_COLLECTIONS = listOf(
            "children",
            "documents",
            "todos",
            "groceries",
            "calendarEvents",
            "events",
            "expenses",
            "medicalVisits",
            "medicalExams",
            "treatments",
            "vaccines",
            "pediatricProfiles",
            "photos",
            "notes",
            "chatMessages",
            "routines",
            "pets",
            "petEvents",
            "homeItems",
            "housePayments",
            "vehicles",
            "vehicleEvents",
            "walletTickets",
            "geofences",
        )
    }
}
