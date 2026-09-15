package it.vittorioscocca.kidbox.ui.screens.home.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.dao.NudgeSignalsDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * L'invito nel momento giusto: subito dopo aver creato qualcosa, se in
 * famiglia non c'è ancora nessun altro che possa vederlo.
 *
 * Perché un'altra superficie, quando la Home ha già la checklist «Invita un
 * familiare» e il motore nudge la push `family_invite`. I numeri dell'11/08 →
 * 14/09/2026: la checklist mostrata a 77 persone, toccata da 13, chiusa da un
 * secondo membro per 11; la push pianificata per 12, aperta da nessuno. Sono
 * richieste in astratto — «invita» — fatte a chi non ha ancora niente da
 * condividere, ripetute ogni giorno finché non le si ignora. Il passo invito
 * del wizard, per lo stesso motivo, lo salta il 55%.
 *
 * Qui la richiesta è concreta e arriva una volta sola: hai appena aggiunto un
 * documento, e per ora lo vedi solo tu. È la frase che dice il problema che
 * KidBox risolve, nel momento in cui esiste davvero.
 *
 * Regole:
 * - una volta per utente, per sempre. Chi dice «Non ora» non lo rivede: le
 *   altre due richieste continuano a esistere, non serve una terza che insiste;
 * - solo se la famiglia attiva ha UN membro. Chi ne ha già due o più non ha
 *   bisogno dell'invito, e la prima creazione «consuma» comunque l'occasione;
 * - parte da [it.vittorioscocca.kidbox.util.analytics.AppAnalytics.contentCreated],
 *   l'unico punto attraversato da tutti i salvataggi: aggiungere una chiamata
 *   in ogni schermata sarebbe stato il modo per dimenticarne una.
 *
 * Stessa logica di `FirstContentInvitePrompt.swift` su iOS.
 */
object FirstContentInvitePrompt {

    const val TRIGGER = "first_content"

    private const val PREFS_NAME = "kidbox_first_content_invite"
    private const val DONE_KEY_PREFIX = "done."

    private val _pending = MutableStateFlow<String?>(null)

    /** Tipo del contenuto appena creato, in attesa che il root lo presenti. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /**
     * Chiave per utente, non per dispositivo: due account sullo stesso
     * telefono sono due persone, e ciascuna ha diritto alla sua occasione.
     */
    private fun doneKey() = DONE_KEY_PREFIX + (FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDone(context: Context): Boolean = prefs(context).getBoolean(doneKey(), false)

    private fun markDone(context: Context) {
        prefs(context).edit().putBoolean(doneKey(), true).apply()
    }

    /** Da chiamare a ogni contenuto creato. Costa una lettura di preferenze. */
    fun noteContentCreated(context: Context, type: String) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        if (isDone(context)) return
        _pending.value = type
    }

    fun clearPending() {
        _pending.value = null
    }

    /**
     * Decide se mostrare il foglio e, in ogni caso, consuma l'occasione.
     * Vero solo se la famiglia attiva ha esattamente un membro.
     */
    suspend fun shouldPresent(context: Context, familyId: String?, dao: NudgeSignalsDao): Boolean {
        if (isDone(context) || familyId.isNullOrBlank()) return false
        markDone(context)
        return dao.familyMemberCount(familyId) == 1
    }

    /**
     * Il contenuto appena creato, nella frase del foglio. Le chiavi sono i tipi
     * di `AppAnalytics.contentCreated`; per un tipo non ancora elencato la
     * frase resta generica, non sbagliata.
     */
    fun subjectRes(contentType: String): Int = when (contentType) {
        "documents" -> R.string.first_content_invite_subject_documents
        "photos" -> R.string.first_content_invite_subject_photos
        "calendar" -> R.string.first_content_invite_subject_calendar
        "expenses" -> R.string.first_content_invite_subject_expenses
        "grocery" -> R.string.first_content_invite_subject_grocery
        "todo" -> R.string.first_content_invite_subject_todo
        "notes" -> R.string.first_content_invite_subject_notes
        "wallet" -> R.string.first_content_invite_subject_wallet
        "loyalty_card" -> R.string.first_content_invite_subject_loyalty_card
        "passwords" -> R.string.first_content_invite_subject_passwords
        "health" -> R.string.first_content_invite_subject_health
        "pets" -> R.string.first_content_invite_subject_pets
        "home_vehicles" -> R.string.first_content_invite_subject_home_vehicles
        "travel" -> R.string.first_content_invite_subject_travel
        "location" -> R.string.first_content_invite_subject_location
        else -> R.string.first_content_invite_subject_generic
    }
}

/** Porta il DAO al root della navigazione, che non ne ha uno suo. */
@HiltViewModel
class FirstContentInviteViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val nudgeSignalsDao: NudgeSignalsDao,
) : ViewModel() {
    suspend fun shouldPresent(familyId: String?): Boolean =
        FirstContentInvitePrompt.shouldPresent(appContext, familyId, nudgeSignalsDao)
}
