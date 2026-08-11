package it.vittorioscocca.kidbox.ui.screens.home.onboarding

import android.content.Context
import androidx.compose.ui.graphics.Color
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.dao.OnboardingSignalsDao
import it.vittorioscocca.kidbox.ui.navigation.AppDestination

/**
 * Checklist "Per iniziare": i cinque passi che trasformano un'installazione in
 * una famiglia che usa KidBox davvero.
 *
 * Gemello di `OnboardingChecklist.swift`. La Home aveva già due modi di
 * suggerire cosa fare — il carosello promozionale e la schermata Suggerimenti —
 * e nessuno dei due sapeva cosa l'utente avesse già fatto: il carosello propone
 * "Invita nuovi membri" anche a chi ha quattro membri. Il solo componente che
 * quel profilo lo calcola davvero è `NudgeEngine.Signals`, ma parla unicamente
 * via notifica push, cioè fuori dall'app. Questa checklist è quello stesso
 * profilo reso visibile dentro la Home.
 *
 * Due regole che ne determinano quasi tutto il comportamento:
 *
 * 1. **Le spunte sono derivate, mai manuali.** Un passo si chiude perché il dato
 *    esiste (un documento caricato, un secondo membro nella famiglia), non
 *    perché qualcuno ha toccato un cerchietto. Una checklist che si spunta da
 *    sola non può mentire; una che si spunta a mano diventa subito un elenco di
 *    cose dichiarate e non fatte.
 * 2. **La selezione dei cinque passi si congela alla prima valutazione.** Senza
 *    congelamento le righe ballerebbero sotto le dita: chi concede le notifiche
 *    vedrebbe comparire un passo nuovo al posto di quello appena chiuso, e la
 *    barra di avanzamento non arriverebbe mai in fondo.
 */

// ── Passi ────────────────────────────────────────────────────────────────────

/**
 * I passi disponibili. L'ordine di [core] è l'ordine mostrato; [bench] entra
 * solo per rimpiazzare i passi core già chiusi alla prima valutazione.
 */
enum class OnboardingStep(val raw: String, val titleRes: Int, val tint: Color) {
    NOTIFICATIONS("notifications", R.string.onboarding_step_notifications, Color(0xFFFF9500)),
    INVITE("invite", R.string.onboarding_step_invite, Color(0xFF30B0C7)),
    DOCUMENT("document", R.string.onboarding_step_document, Color(0xFFFF9500)),
    PHOTO("photo", R.string.onboarding_step_photo, Color(0xFFFF2D55)),
    CALENDAR_EVENT("calendar_event", R.string.onboarding_step_calendar_event, Color(0xFFAF52DE)),

    // Panchina.
    EXPENSE("expense", R.string.onboarding_step_expense, Color(0xFF00C7BE)),
    NOTE("note", R.string.onboarding_step_note, Color(0xFFFFCC00)),
    GROCERY("grocery", R.string.onboarding_step_grocery, Color(0xFF34C759));

    /**
     * Dove porta il tap. `null` per le notifiche: il permesso si chiede sul
     * posto, mandare l'utente in una schermata per poi mostrargli il dialog di
     * sistema aggiungerebbe un passaggio senza aggiungere niente.
     */
    fun route(familyId: String): String? = when (this) {
        NOTIFICATIONS -> null
        INVITE -> AppDestination.InviteCode.route
        DOCUMENT -> AppDestination.DocumentsHome.createRoute(familyId)
        PHOTO -> AppDestination.FamilyPhotos.createRoute(familyId)
        CALENDAR_EVENT -> AppDestination.Calendar.createRoute(familyId)
        EXPENSE -> AppDestination.ExpensesHome.createRoute(familyId)
        NOTE -> AppDestination.NotesHome.createRoute(familyId)
        GROCERY -> AppDestination.ShoppingList.createRoute(familyId)
    }

    companion object {
        /**
         * I cinque passi che vogliamo davvero: permesso, rete, contenuto,
         * ricordo, impegno condiviso. Coprono le tre curve di attivazione (rete,
         * contenuto, abitudine) invece di insistere su una sola area.
         */
        val core = listOf(NOTIFICATIONS, INVITE, DOCUMENT, PHOTO, CALENDAR_EVENT)

        /**
         * Riserve, per chi arriva con qualche passo core già chiuso —
         * tipicamente chi entra in una famiglia già popolata tramite QR.
         */
        val bench = listOf(EXPENSE, NOTE, GROCERY)

        /**
         * Quanti passi mostra la card. Cinque è il numero oltre il quale una
         * lista di cose da fare smette di leggersi come un invito e inizia a
         * leggersi come un compito.
         */
        const val DISPLAY_COUNT = 5

        fun from(raw: String): OnboardingStep? = entries.firstOrNull { it.raw == raw }
    }
}

// ── Segnali ──────────────────────────────────────────────────────────────────

/**
 * Fotografia dello stato dell'utente, letta dal database locale e dal sistema.
 * Stessa natura dei `NudgeEngine.Signals`: costruita per una valutazione e mai
 * persistita. Nessuna query di rete, nessun profilo per-utente lato server.
 */
data class OnboardingSignals(
    val notificationsAuthorized: Boolean = false,
    val familyMembers: Int = 0,
    /** Passi chiusi dalla presenza di un contenuto. */
    val contentDone: Set<OnboardingStep> = emptySet(),
) {
    fun isComplete(step: OnboardingStep): Boolean = when (step) {
        OnboardingStep.NOTIFICATIONS -> notificationsAuthorized
        OnboardingStep.INVITE -> familyMembers >= 2
        else -> step in contentDone
    }

    companion object {
        suspend fun build(
            dao: OnboardingSignalsDao,
            familyId: String,
            notificationsAuthorized: Boolean,
        ): OnboardingSignals {
            val done = buildSet {
                if (dao.documentCount(familyId) > 0) add(OnboardingStep.DOCUMENT)
                if (dao.familyPhotoCount(familyId) > 0) add(OnboardingStep.PHOTO)
                if (dao.calendarEventCount(familyId) > 0) add(OnboardingStep.CALENDAR_EVENT)
                if (dao.expenseCount(familyId) > 0) add(OnboardingStep.EXPENSE)
                if (dao.noteCount(familyId) > 0) add(OnboardingStep.NOTE)
                if (dao.groceryItemCount(familyId) > 0) add(OnboardingStep.GROCERY)
            }
            return OnboardingSignals(
                notificationsAuthorized = notificationsAuthorized,
                familyMembers = dao.familyMemberCount(familyId),
                contentDone = done,
            )
        }
    }
}

// ── Stato persistito ─────────────────────────────────────────────────────────

/**
 * Quello che va salvato è pochissimo, ed è tutto ciò che *non* si può ricavare
 * dai dati: la selezione congelata, le due uscite (chiusa a mano, o soppressa
 * perché inutile) e il momento del completamento. Le spunte no: quelle si
 * ricalcolano, così non possono raccontare una cosa diversa dal database.
 *
 * Come `NudgeState`, sta in `SharedPreferences` e non su Firestore: è stato di
 * presentazione, non contenuto. Il prezzo è che cambiando dispositivo la
 * checklist riparte — accettabile, visto che i passi già chiusi si ripresentano
 * comunque spuntati.
 */
object OnboardingChecklistState {

    private const val PREFS = "kb_onboarding_checklist_prefs"
    private const val KEY_STEPS = "steps"
    private const val KEY_DISMISSED = "dismissed"
    private const val KEY_SUPPRESSED = "suppressed"
    private const val KEY_COMPLETED_AT = "completedAt"
    private const val KEY_SHOWN_LOGGED = "shownLogged"
    private const val KEY_COMPLETED_LOGGED = "completedLogged"
    private const val KEY_FORCED = "forced"

    /**
     * Istante in cui il processo ha toccato la checklist la prima volta: serve
     * solo a riconoscere "la sessione in cui è stata completata", per non far
     * sparire la card nello stesso istante dell'ultima spunta.
     */
    val launchMillis: Long = System.currentTimeMillis()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Selezione congelata. Vuota = non ancora decisa. */
    fun steps(context: Context): List<OnboardingStep> =
        prefs(context).getString(KEY_STEPS, "").orEmpty()
            .split(",")
            .mapNotNull { OnboardingStep.from(it) }

    fun setSteps(context: Context, value: List<OnboardingStep>) {
        prefs(context).edit()
            .putString(KEY_STEPS, value.joinToString(",") { it.raw })
            .apply()
    }

    /** L'utente ha chiuso la card. Recuperabile dai Suggerimenti. */
    fun isDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DISMISSED, false)

    fun setDismissed(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DISMISSED, value).apply()
    }

    /**
     * La checklist non aveva senso per questo utente (già attivo all'arrivo).
     * Diverso da [isDismissed]: qui non è mai stata mostrata, e riproporla dopo
     * non avrebbe più senso di quanto ne avesse la prima volta.
     */
    fun isSuppressed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUPPRESSED, false)

    fun setSuppressed(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUPPRESSED, value).apply()
    }

    /** Istante dell'ultima spunta. `null` finché non sono chiusi tutti e cinque. */
    fun completedAt(context: Context): Long? =
        prefs(context).getLong(KEY_COMPLETED_AT, 0L).takeIf { it > 0L }

    fun setCompletedAt(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_COMPLETED_AT, value).apply()
    }

    /**
     * L'utente ha chiesto lui la checklist. Sospende i due filtri che servono a
     * non importunare chi non ne ha bisogno: chi la chiede esplicitamente ne ha
     * bisogno per definizione, e negargliela perché "sembra già attivo" sarebbe
     * il prodotto che contraddice una richiesta diretta.
     */
    fun isForced(context: Context): Boolean = prefs(context).getBoolean(KEY_FORCED, false)

    fun setForced(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_FORCED, value).apply()
    }

    /**
     * Passi per cui l'evento `shown` è già partito: si registra una volta per
     * passo per utente, altrimenti il denominatore conterebbe le aperture della
     * Home invece delle persone.
     */
    fun markShownIfNeeded(context: Context, step: OnboardingStep): Boolean =
        markOnce(context, step, KEY_SHOWN_LOGGED)

    /**
     * Stessa logica per il completamento: la card si ridisegna a ogni apertura
     * della Home, ma un passo si chiude una volta sola.
     */
    fun markCompletedIfNeeded(context: Context, step: OnboardingStep): Boolean =
        markOnce(context, step, KEY_COMPLETED_LOGGED)

    private fun markOnce(context: Context, step: OnboardingStep, key: String): Boolean {
        val p = prefs(context)
        val logged = p.getString(key, "").orEmpty().split(",").filter { it.isNotBlank() }.toMutableSet()
        if (!logged.add(step.raw)) return false
        p.edit().putString(key, logged.sorted().joinToString(",")).apply()
        return true
    }

    /** Riporta la card dopo una chiusura manuale (dalla schermata Suggerimenti). */
    fun restore(context: Context) {
        setDismissed(context, false)
        setSuppressed(context, false)
        setForced(context, true)
    }

    /**
     * I passi che la Home sta effettivamente proponendo adesso. Vuoto se la
     * checklist non è in scena, per qualunque ragione: la usa il motore dei
     * nudge per non chiedere via push quello che è già scritto in Home.
     */
    fun liveSteps(context: Context): List<OnboardingStep> {
        if (isDismissed(context) || isSuppressed(context) || completedAt(context) != null) {
            return emptyList()
        }
        return steps(context)
    }

    /**
     * Esiste una checklist da poter riaprire? Una già completata non si
     * ripropone: non è una funzione, è un percorso, e i percorsi finiscono.
     */
    fun isRestorable(context: Context): Boolean =
        completedAt(context) == null && (isDismissed(context) || isSuppressed(context))

    /** Solo per le anteprime e i test manuali. */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

// ── Modello di presentazione ─────────────────────────────────────────────────

/** Una riga della card: il passo e il suo stato al momento del calcolo. */
data class OnboardingChecklistRow(
    val step: OnboardingStep,
    val isComplete: Boolean,
)

/**
 * Lo stato della card così come la Home lo consuma. `isVisible` false significa
 * "non disegnare niente": le righe restano quelle dell'ultimo calcolo utile, ma
 * non vanno mostrate.
 */
data class OnboardingChecklistUiState(
    val isVisible: Boolean = false,
    val rows: List<OnboardingChecklistRow> = emptyList(),
    /** Tutti i passi chiusi: la card resta in scena fino al prossimo avvio. */
    val isCelebrating: Boolean = false,
) {
    val completedCount: Int get() = rows.count { it.isComplete }
    val totalCount: Int get() = rows.size
}

/**
 * Decide quali cinque passi mostrare, una volta per installazione.
 *
 * Chi arriva con almeno tre passi core già chiusi non è un utente da attivare:
 * è entrato in una famiglia che già funziona. Una checklist quasi tutta spuntata
 * al primo sguardo non insegna niente e si legge come un bug, quindi a
 * quell'utente non si mostra affatto.
 */
private fun freezeSelection(context: Context, signals: OnboardingSignals): List<OnboardingStep> {
    val forced = OnboardingChecklistState.isForced(context)
    val coreDone = OnboardingStep.core.count { signals.isComplete(it) }

    if (!forced && coreDone >= 3) {
        OnboardingChecklistState.setSuppressed(context, true)
        return emptyList()
    }

    val candidates = (OnboardingStep.core + OnboardingStep.bench)
        .filterNot { signals.isComplete(it) }

    // Meno di cinque passi aperti in tutto il catalogo: l'utente si è già mosso
    // abbastanza da rendere la card una ripetizione. Su richiesta esplicita
    // basta che ne resti almeno uno: meglio una lista corta di un rifiuto senza
    // spiegazione.
    val minimum = if (forced) 1 else OnboardingStep.DISPLAY_COUNT
    if (candidates.size < minimum) {
        OnboardingChecklistState.setSuppressed(context, true)
        return emptyList()
    }

    val selection = candidates.take(OnboardingStep.DISPLAY_COUNT)
    OnboardingChecklistState.setSteps(context, selection)
    return selection
}

/**
 * Ricalcola tutto: da chiamare all'apparire della Home e a ogni ritorno in
 * foreground. Costa una manciata di `COUNT(*)` locali, e ricalcolare evita
 * l'errore peggiore — una riga che chiede di fare una cosa già fatta.
 *
 * Restituisce anche gli eventi analytics da registrare, invece di scriverli qui:
 * così la funzione resta pura rispetto alla rete e il chiamante decide quando
 * (e se) mandarli.
 */
suspend fun computeOnboardingChecklist(
    context: Context,
    dao: OnboardingSignalsDao,
    familyId: String,
    notificationsAuthorized: Boolean,
): Pair<OnboardingChecklistUiState, List<Pair<String, OnboardingStep>>> {
    val hidden = OnboardingChecklistUiState() to emptyList<Pair<String, OnboardingStep>>()

    if (familyId.isBlank()) return hidden
    if (OnboardingChecklistState.isDismissed(context)) return hidden
    if (OnboardingChecklistState.isSuppressed(context)) return hidden

    val signals = OnboardingSignals.build(dao, familyId, notificationsAuthorized)

    val selection = OnboardingChecklistState.steps(context)
        .ifEmpty { freezeSelection(context, signals) }
    if (selection.isEmpty()) return hidden

    val rows = selection.map { OnboardingChecklistRow(it, signals.isComplete(it)) }
    val allDone = rows.all { it.isComplete }

    if (allDone && OnboardingChecklistState.completedAt(context) == null) {
        OnboardingChecklistState.setCompletedAt(context, System.currentTimeMillis())
    }

    // Completata in una sessione precedente: ha finito il suo lavoro.
    val completedAt = OnboardingChecklistState.completedAt(context)
    if (completedAt != null && completedAt < OnboardingChecklistState.launchMillis) return hidden

    val events = buildList {
        rows.forEach { row ->
            if (OnboardingChecklistState.markShownIfNeeded(context, row.step)) {
                add("shown" to row.step)
            }
            if (row.isComplete && OnboardingChecklistState.markCompletedIfNeeded(context, row.step)) {
                add("completed" to row.step)
            }
        }
    }

    return OnboardingChecklistUiState(
        isVisible = true,
        rows = rows,
        isCelebrating = allDone,
    ) to events
}
