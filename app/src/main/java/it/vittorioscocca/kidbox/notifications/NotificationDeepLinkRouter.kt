package it.vittorioscocca.kidbox.notifications

import android.content.Context
import android.content.Intent
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.screens.ai.planning.DailyBriefingDraftStore
import it.vittorioscocca.kidbox.ui.screens.ai.planning.HealthPatternDraftStore
import it.vittorioscocca.kidbox.ui.screens.ai.planning.WeeklySummaryDraftStore
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
object NotificationDeepLinkRouter {

    private const val PREFS_NAME = "kidbox_prefs"
    private const val KEY_ACTIVE_FAMILY_ID = "active_family_id"
    private const val KEY_LAST_BRIEFING = "kb_dailyBriefing_lastText"
    private const val TAG = "NotificationDeepLink"

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

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
                _pendingChatMessageId.value = messageId
                queueFamilyAwareRoute(resolvedType, familyId) { _ ->
                    AppDestination.Chat.route
                }
            }
            "new_document" -> {
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
                if (childId.isNullOrBlank() || listId.isNullOrBlank()) {
                    KBLog.app.warning(
                        "NotificationDeepLink: todo payload incompleto type=$resolvedType",
                        TAG,
                    )
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.TodoList.createRoute(
                        familyId = fid,
                        childId = childId,
                        listId = listId,
                        highlightTodoId = todoId,
                    )
                }
            }
            "new_grocery_item" -> {
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.ShoppingList.createRoute(fid)
                }
            }
            "new_note" -> {
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
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.Calendar.createRoute(fid)
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
                val ticketId = intent.getStringExtra("ticketId")
                if (ticketId.isNullOrBlank()) {
                    KBLog.app.warning("NotificationDeepLink: ticketId mancante", TAG)
                    return
                }
                queueFamilyAwareRoute(resolvedType, familyId) { fid ->
                    AppDestination.WalletDetail.createRoute(familyId = fid, ticketId = ticketId)
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

    fun clear() {
        _pendingRoute.value = null
        _pendingFamilyId.value = null
        _pendingChatMessageId.value = null
    }
}
