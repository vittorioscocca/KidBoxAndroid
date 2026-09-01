package it.vittorioscocca.kidbox.notifications

import android.content.Context
import android.content.Intent
import it.vittorioscocca.kidbox.notifications.nudge.NudgeDestination
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.screens.ai.planning.DailyBriefingDraftStore
import it.vittorioscocca.kidbox.ui.screens.ai.planning.HealthPatternDraftStore
import it.vittorioscocca.kidbox.ui.screens.ai.planning.WeeklySummaryDraftStore
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsEntryPoint
import it.vittorioscocca.kidbox.util.analytics.KBAnalyticsOrigin

/**
 * Router unico per i deep link da notifiche push e locali.
 *
 * - Estrae `type` + `familyId` (e gli id di dettaglio) dagli extras dell'`Intent`.
 * - Mappa ogni tipo nella route di [AppDestination] corrispondente.
 * - Espone [pendingFamilyId] perché l'UI faccia lo switch famiglia *prima*
 *   di consumare [pendingRoute]: se il payload è per una famiglia diversa
 *   da quella attiva, [AppNavGraph] deve aggiornare la famiglia attiva
 *   tramite `FamilySwitcherViewModel.switchToFamily(...)` e solo dopo
 *   navigare alla destinazione.
 *
 * Mirror del comportamento iOS (`AppCoordinator.switchFamilyIfNeededThenNavigate`).
 */
/**
 * Annuncio inviato a mano dalla console admin (`sendBroadcast`).
 *
 * Il contenuto *è* il messaggio: non c'è una destinazione dentro l'app né una
 * famiglia di riferimento, quindi non passa da [AppDestination] come tutto il
 * resto — viaggia intero dentro il payload della notifica.
 */
data class BroadcastMessage(
    val id: String,
    val title: String,
    val body: String,
    /** Campagna di origine, se è un nudge. Serve solo alle metriche. */
    val campaignId: String? = null,
    /** Se valorizzata, il dialog mostra "Vai" e "Non ora" invece di "Ho capito". */
    val destination: NudgeDestination? = null,
)

object NotificationDeepLinkRouter {

    private const val PREFS_NAME = "kidbox_prefs"
    private const val KEY_ACTIVE_FAMILY_ID = "active_family_id"
    private const val KEY_LAST_BRIEFING = "kb_dailyBriefing_lastText"
    private const val TAG = "NotificationDeepLink"

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    /**
     * `todoId` di cui va ancora individuata la lista che lo contiene.
     *
     * Valorizzato quando la notifica non porta `childId`/`listId`: lo consuma
     * [it.vittorioscocca.kidbox.ui.screens.todo.TodoDeepLinkResolverViewModel],
     * che legge il todo da Room e poi chiama [queueResolvedTodoList].
     * Volutamente NON azzerato da [clear], che scatta dopo la navigazione alla
     * sezione To-Do: la risoluzione della lista deve sopravvivere a quel passo.
     */
    private val _pendingTodoId = MutableStateFlow<String?>(null)
    val pendingTodoId: StateFlow<String?> = _pendingTodoId.asStateFlow()

    /** `messageId` del messaggio chat da evidenziare dopo la navigazione (menzioni). */
    private val _pendingChatMessageId = MutableStateFlow<String?>(null)
    val pendingChatMessageId: StateFlow<String?> = _pendingChatMessageId.asStateFlow()

    /**
     * `familyId` associato alla destinazione corrente in coda.
     *
     * L'UI deve attivare questa famiglia (se diversa dalla attuale) prima di
     * navigare a [pendingRoute]: i listener di sync e i ViewModel risolvono la
     * famiglia da `FamilySessionPreferences.active_family_id`, quindi navigare
     * senza switch porterebbe sulla famiglia sbagliata.
     */
    private val _pendingFamilyId = MutableStateFlow<String?>(null)
    val pendingFamilyId: StateFlow<String?> = _pendingFamilyId.asStateFlow()

    /**
     * Annuncio da mostrare in `BroadcastMessageDialog`.
     *
     * Tenuto fuori da [pendingRoute] di proposito: non è navigazione, non ha
     * famiglia da attivare e non deve entrare nel back stack.
     */
    private val _pendingBroadcast = MutableStateFlow<BroadcastMessage?>(null)
    val pendingBroadcast: StateFlow<BroadcastMessage?> = _pendingBroadcast.asStateFlow()

    /** Incrementato a ogni tap notifica AI (anche se la chat è già aperta). */
    private val _recapTick = MutableStateFlow(0)
    val recapTick: StateFlow<Int> = _recapTick.asStateFlow()

    fun handleLaunchIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("push_type")
            ?: intent.getStringExtra("type")
        val isAiChatAction = intent.action == "KB_OPEN_AI_CHAT"
        if (type.isNullOrBlank() && !isAiChatAction) return

        val resolvedType = type ?: return
        val familyId = resolveFamilyId(context, intent)

        when (resolvedType) {
            "daily_briefing" -> {
                val fullText = resolveFullText(context, intent, resolvedType)
                if (fullText.isNotBlank()) DailyBriefingDraftStore.save(context, fullText)
                queueAiChatRoute(familyId, resolvedType)
            }
            "weekly_summary" -> {
                val fullText = resolveFullText(context, intent, resolvedType)
                if (fullText.isNotBlank()) WeeklySummaryDraftStore.save(context, fullText)
                queueAiChatRoute(familyId, resolvedType)
            }
            "health_pattern" -> {
                val fullText = resolveFullText(context, intent, resolvedType)
                if (fullText.isNotBlank()) HealthPatternDraftStore.save(context, fullText)
                queueAiChatRoute(familyId, resolvedType)
            }
            "geofenceEvent",
            "location_sharing_started",
            "location_sharing_stopped" -> {
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.FamilyLocation.createRoute(fid)
                }
            }
            "new_chat_message",
            "chat_mention" -> {
                val messageId = intent.getStringExtra("push_message_id")
                    ?: intent.getStringExtra("messageId")
                _pendingChatMessageId.value = messageId
                queueFamilyAwareRoute(resolvedType, familyId) { _ ->
                    AppDestination.Chat.route
                }
            }
            "new_document" -> {
                // Solo i casi che aprono davvero un contenuto: `daily_briefing` &
                // co. portano in Home, e marcarli colorerebbe di ".notification"
                // il primo dettaglio aperto poi sfogliando.
                KBAnalyticsOrigin.set(KBAnalyticsEntryPoint.NOTIFICATION)
                val docId = intent.getStringExtra("push_doc_id")
                    ?: intent.getStringExtra("docId")
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.DocumentsHome.createRoute(
                        familyId = fid,
                        highlightDocumentId = docId,
                    )
                }
            }
            "todo_reminder",
            "todo_assigned",
            "todo_reassigned",
            "todo_due_changed" -> {
                val childId = intent.getStringExtra("push_child_id") ?: intent.getStringExtra("childId")
                val listId = intent.getStringExtra("push_list_id") ?: intent.getStringExtra("listId")
                val todoId = intent.getStringExtra("push_todo_id") ?: intent.getStringExtra("todoId")
                // Con il `todoId` si passa SEMPRE dall'attesa, anche quando il
                // payload porta già `listId`.
                //
                // Non è un giro inutile: la push arriva PRIMA che la
                // sincronizzazione abbia portato il to-do in Room, quindi
                // entrare subito nella lista la mostra senza l'elemento appena
                // creato — che è esattamente ciò che l'utente andava a vedere.
                // [TodoDeepLinkResolverViewModel] aspetta che il to-do esista
                // davvero (mostrando lo spinner) e solo allora si entra, con
                // `listId` e `childId` presi dal to-do stesso, che sono la
                // fonte autorevole se il payload fosse stale.
                //
                // Intanto si apre la sezione To-Do, che diventa anche il livello
                // a cui torna il tasto indietro — stesso stack di
                // `openTodoFromPush` su iOS (.todo → .todoList).
                if (!todoId.isNullOrBlank()) {
                    _pendingTodoId.value = todoId
                    KBLog.app.info(
                        "NotificationDeepLink: attendo il to-do in locale todoId=$todoId",
                        TAG,
                    )
                    queueFamilyAwareRoute(resolvedType, familyId) { _ ->
                        AppDestination.Todo.route
                    }
                    return
                }
                // Senza `todoId` non c'è niente da attendere né da evidenziare:
                // se si conosce la lista ci si entra, altrimenti resta la
                // panoramica To-Do. `childId` vuoto va bene, la rotta lo regge.
                if (listId.isNullOrBlank()) {
                    KBLog.app.warning(
                        "NotificationDeepLink: todo senza listId né todoId — apro la sezione To-Do",
                        TAG,
                    )
                    queueFamilyAwareRoute(resolvedType, familyId) { _ ->
                        AppDestination.Todo.route
                    }
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.TodoList.createRoute(
                        familyId = fid,
                        childId = childId.orEmpty(),
                        listId = listId,
                    )
                }
            }
            "new_grocery_item" -> {
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.ShoppingList.createRoute(fid)
                }
            }
            "new_note" -> {
                KBAnalyticsOrigin.set(KBAnalyticsEntryPoint.NOTIFICATION)
                val noteId = intent.getStringExtra("push_note_id") ?: intent.getStringExtra("noteId")
                if (noteId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: noteId mancante", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.NoteDetail.createRoute(familyId = fid, noteId = noteId)
                }
            }
            "new_calendar_event",
            "calendar_event" -> {
                KBAnalyticsOrigin.set(KBAnalyticsEntryPoint.NOTIFICATION)
                // Con l'id si apre direttamente il dettaglio dell'evento appena
                // creato; senza, resta la vista mese.
                val eventId = intent.getStringExtra("push_event_id")
                    ?: intent.getStringExtra("eventId")
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.Calendar.createRoute(fid, openEventId = eventId)
                }
            }
            "visit_reminder" -> {
                val childId = intent.getStringExtra("push_child_id") ?: intent.getStringExtra("childId")
                val visitId = intent.getStringExtra("push_visit_id") ?: intent.getStringExtra("visitId")
                if (childId.isNullOrBlank() || visitId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: visit payload incompleto", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.MedicalVisitDetail.route(fid, childId, visitId)
                }
            }
            "treatment_reminder" -> {
                val childId = intent.getStringExtra("push_child_id") ?: intent.getStringExtra("childId")
                val treatmentId = intent.getStringExtra("push_treatment_id") ?: intent.getStringExtra("treatmentId")
                if (childId.isNullOrBlank() || treatmentId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: treatment payload incompleto", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.TreatmentDetail.route(fid, childId, treatmentId)
                }
            }
            "fitness_session_reminder" -> {
                val childId = intent.getStringExtra("push_child_id")
                if (childId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: fitness payload incompleto", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.FitnessPlan.route(fid, childId)
                }
            }
            "exam_reminder" -> {
                val childId = intent.getStringExtra("push_child_id") ?: intent.getStringExtra("childId")
                val examId = intent.getStringExtra("push_exam_id") ?: intent.getStringExtra("examId")
                if (childId.isNullOrBlank() || examId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: exam payload incompleto", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.MedicalExamDetail.route(fid, childId, examId)
                }
            }
            "vaccine_reminder" -> {
                val childId = intent.getStringExtra("push_child_id") ?: intent.getStringExtra("childId")
                val vaccineId = intent.getStringExtra("push_vaccine_id") ?: intent.getStringExtra("vaccineId")
                if (childId.isNullOrBlank() || vaccineId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: vaccine payload incompleto", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.VaccineForm.routeEdit(fid, childId, vaccineId)
                }
            }
            "vehicle_deadline_reminder" -> {
                val vehicleId = intent.getStringExtra("push_vehicle_id")
                if (vehicleId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: vehicleId mancante", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.VehicleDetail.createRoute(fid, vehicleId)
                }
            }
            "house_payment_reminder" -> {
                val paymentId = intent.getStringExtra("push_house_payment_id")
                if (paymentId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: paymentId mancante", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.HousePaymentDetail.createRoute(fid, paymentId)
                }
            }
            "new_expense" -> {
                val expenseId = intent.getStringExtra("push_expense_id") ?: intent.getStringExtra("expenseId")
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.ExpensesHome.createRoute(
                        familyId = fid,
                        highlightExpenseId = expenseId,
                    )
                }
            }
            "new_wallet_ticket",
            "wallet_ticket_reminder" -> {
                KBAnalyticsOrigin.set(KBAnalyticsEntryPoint.NOTIFICATION)
                val ticketId = intent.getStringExtra("ticketId")
                if (ticketId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: ticketId mancante", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.WalletDetail.createRoute(familyId = fid, ticketId = ticketId)
                }
            }
            "new_loyalty_card" -> {
                KBAnalyticsOrigin.set(KBAnalyticsEntryPoint.NOTIFICATION)
                val cardId = intent.getStringExtra("cardId")
                if (cardId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: cardId mancante", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.WalletLoyaltyCardDetail.createRoute(familyId = fid, cardId = cardId)
                }
            }
            "wallet_document_reminder" -> {
                val documentId = intent.getStringExtra("documentId")
                if (documentId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: documentId mancante", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.WalletDocumentDetail.createRoute(familyId = fid, documentId = documentId)
                }
            }
            "password_expiry_reminder" -> {
                val entryId = intent.getStringExtra("push_entry_id") ?: intent.getStringExtra("entryId")
                if (entryId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: entryId mancante per password_expiry", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.PasswordDetail.createRoute(familyId = fid, passwordId = entryId)
                }
            }
            "password_security_summary" -> {
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.PasswordsSecurity.createRoute(fid)
                }
            }
            "broadcast" -> {
                // `title` / `body` arrivano dagli extra del payload `data`, non
                // dal blocco `notification`: il server li duplica lì apposta,
                // perché quello che il sistema mostra nella tendina è già
                // troncato e non è il testo integrale.
                // Doppia lettura, e non è ridondanza: gli extra `push_*` li
                // scrive `KidBoxFirebaseMessagingService`, che però viene
                // chiamato SOLO con app in foreground. In background la
                // notifica la mostra l'SDK di Firebase e il tap consegna il
                // payload `data` con le chiavi grezze. Leggere solo `push_body`
                // significa non aprire mai il dialog dal caso più comune.
                val body = (intent.getStringExtra("push_body")
                    ?: intent.getStringExtra("body")).orEmpty()
                if (body.isBlank()) {
                    KBLog.app.warning("NotificationDeepLink: broadcast senza body", TAG)
                    return
                }
                _pendingBroadcast.value = BroadcastMessage(
                    id = (intent.getStringExtra("push_broadcast_id")
                        ?: intent.getStringExtra("broadcastId")).orEmpty(),
                    title = (intent.getStringExtra("push_title")
                        ?: intent.getStringExtra("title")).orEmpty(),
                    body = body,
                )
                KBLog.app.info("NotificationDeepLink: broadcast in coda", TAG)
            }
            "nudge" -> {
                // Stessa view del broadcast, con in più la destinazione: per
                // l'utente sono lo stesso oggetto — un messaggio da KidBox.
                val body = intent.getStringExtra("push_body").orEmpty()
                val campaignId = intent.getStringExtra("push_campaign_id").orEmpty()
                if (body.isBlank() || campaignId.isBlank()) {
                    KBLog.app.warning("NotificationDeepLink: nudge incompleto", TAG)
                    return
                }
                _pendingBroadcast.value = BroadcastMessage(
                    id = campaignId,
                    title = intent.getStringExtra("push_title").orEmpty(),
                    body = body,
                    campaignId = campaignId,
                    // Destinazione sconosciuta (catalogo remoto più recente
                    // dell'app): il messaggio si apre lo stesso, senza pulsante
                    // di azione. Meglio informativo che assente.
                    destination = NudgeDestination.from(
                        intent.getStringExtra("push_destination")),
                )
                KBLog.app.info("NotificationDeepLink: nudge $campaignId", TAG)
            }
            else -> Unit
        }
        _recapTick.value += 1
    }

    private fun resolveFamilyId(context: Context, intent: Intent): String {
        return intent.getStringExtra("push_family_id")
            ?: intent.getStringExtra("familyId")
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_FAMILY_ID, null)
            ?: ""
    }

    private fun resolveFullText(context: Context, intent: Intent, type: String): String {
        val fromIntent = intent.getStringExtra("fullText").orEmpty()
        if (fromIntent.isNotBlank()) return fromIntent
        if (type != "daily_briefing") return ""
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_BRIEFING, null)
            .orEmpty()
    }

    private fun queueAiChatRoute(familyId: String, type: String) {
        if (familyId.isBlank()) {
            KBLog.app.warning("NotificationDeepLink: familyId mancante per type=$type", TAG)
            return
        }
        _pendingFamilyId.value = familyId
        _pendingRoute.value = AppDestination.AiChat.createRoute(familyId)
        KBLog.app.info("NotificationDeepLink: coda navigazione $type → $familyId", TAG)
    }

    private inline fun queueFamilyAwareRoute(
        type: String,
        familyId: String,
        buildRoute: (String) -> String,
    ) {
        if (familyId.isBlank()) {
            KBLog.app.warning(
                "NotificationDeepLink: familyId mancante per type=$type — fallback senza switch",
                TAG,
            )
            return
        }
        _pendingFamilyId.value = familyId
        _pendingRoute.value = buildRoute(familyId)
        KBLog.app.info(
            "NotificationDeepLink: coda navigazione type=$type familyId=$familyId route=${_pendingRoute.value}",
            TAG,
        )
    }

    fun clearChatMessageId() {
        _pendingChatMessageId.value = null
    }

    /**
     * Accoda la lista che contiene il todo, una volta risolta da Room.
     *
     * Arriva DOPO che si è già navigato alla sezione To-Do, quindi la lista si
     * impila sopra: indietro riporta alla panoramica To-Do, come su iOS.
     */
    fun queueResolvedTodoList(
        familyId: String,
        childId: String,
        listId: String,
        todoId: String,
    ) {
        _pendingTodoId.value = null
        if (familyId.isBlank()) return
        _pendingFamilyId.value = familyId
        _pendingRoute.value = AppDestination.TodoList.createRoute(
            familyId = familyId,
            childId = childId,
            listId = listId,
            highlightTodoId = todoId,
        )
        KBLog.app.info(
            "NotificationDeepLink: lista del todo risolta → ${_pendingRoute.value}",
            TAG,
        )
    }

    /** Risoluzione fallita (todo mai arrivato in locale, o senza lista). */
    fun clearPendingTodoId() {
        _pendingTodoId.value = null
    }

    /**
     * Consumato alla chiusura del dialog, non da [clear].
     * [clear] scatta dopo una navigazione riuscita: un annuncio ancora aperto
     * non deve sparire perché nel frattempo è arrivata un'altra notifica.
     */
    fun clearBroadcast() {
        _pendingBroadcast.value = null
    }

    fun clear() {
        _pendingRoute.value = null
        _pendingFamilyId.value = null
        _pendingChatMessageId.value = null
    }
}
